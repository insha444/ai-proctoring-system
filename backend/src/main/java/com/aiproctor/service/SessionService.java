package com.aiproctor.service;

import com.aiproctor.exception.ResourceNotFoundException;
import com.aiproctor.model.*;
import com.aiproctor.model.ExamSession.SessionStatus;
import com.aiproctor.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SessionService {

    private final SessionRepository sessionRepository;
    private final ExamRepository examRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager em;

    // ── Start exam (idempotent) ───────────────────────────────────────────────

    @Transactional
    public ExamSession startExam(Long examId, String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", studentEmail));

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", examId));

        if (!exam.getIsActive()) {
            throw new IllegalStateException("Exam is not currently active");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(exam.getEndTime())) {
            throw new IllegalStateException("Exam has already ended");
        }

        // Idempotent: return existing in-progress session
        Optional<ExamSession> existing =
                sessionRepository.findByStudentIdAndExamId(student.getId(), examId);

        if (existing.isPresent()) {
            ExamSession session = existing.get();
            if (session.getStatus() == SessionStatus.IN_PROGRESS) {
                log.debug("Returning existing session {} for student {}", session.getId(), studentEmail);
                return session;
            }
            throw new IllegalStateException(
                    "You have already " + session.getStatus().name().toLowerCase() + " this exam");
        }

        ExamSession session = ExamSession.builder()
                .student(student)
                .exam(exam)
                .status(SessionStatus.IN_PROGRESS)
                .startedAt(LocalDateTime.now())
                .build();

        ExamSession saved = sessionRepository.save(session);
        log.info("Exam session started: sessionId={} examId={} student={}",
                saved.getId(), examId, studentEmail);
        return saved;
    }

    // ── Save / upsert a single answer ────────────────────────────────────────

    @Transactional
    public StudentAnswer saveAnswer(Long sessionId, Long questionId,
                                    String selectedAnswer, String studentEmail) {
        ExamSession session = getActiveSession(sessionId, studentEmail);
        Exam exam = session.getExam();

        ExamQuestion question = exam.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Question " + questionId + " does not belong to this exam"));

        validateAnswer(selectedAnswer);

        // Upsert: find existing answer or create new
        @SuppressWarnings("unchecked")
        List<StudentAnswer> existing = em.createQuery(
                "SELECT a FROM StudentAnswer a WHERE a.session.id = :sid AND a.question.id = :qid")
                .setParameter("sid", sessionId)
                .setParameter("qid", questionId)
                .getResultList();

        StudentAnswer answer;
        if (!existing.isEmpty()) {
            answer = existing.get(0);
            answer.setSelectedAnswer(selectedAnswer.toUpperCase());
        } else {
            answer = StudentAnswer.builder()
                    .session(session)
                    .student(session.getStudent())
                    .exam(exam)
                    .question(question)
                    .selectedAnswer(selectedAnswer != null
                            ? selectedAnswer.toUpperCase() : null)
                    .build();
        }

        em.persist(answer);
        em.flush();
        return answer;
    }

    // ── Submit exam (grade all answers) ──────────────────────────────────────

    @Transactional
    public Map<String, Object> submitExam(Long sessionId, String studentEmail) {
        ExamSession session = getActiveSession(sessionId, studentEmail);
        Exam exam = session.getExam();

        // Load all saved answers for this session
        @SuppressWarnings("unchecked")
        List<StudentAnswer> savedAnswers = em.createQuery(
                "SELECT a FROM StudentAnswer a WHERE a.session.id = :sid")
                .setParameter("sid", sessionId)
                .getResultList();

        List<ExamQuestion> questions = exam.getQuestions();
        int totalMarks = questions.stream().mapToInt(ExamQuestion::getMarks).sum();
        int earnedMarks = 0;

        for (StudentAnswer answer : savedAnswers) {
            boolean correct = answer.getSelectedAnswer() != null
                    && answer.getSelectedAnswer().equalsIgnoreCase(
                            answer.getQuestion().getCorrectAnswer());
            answer.setIsCorrect(correct);
            if (correct) {
                earnedMarks += answer.getQuestion().getMarks();
            }
            em.merge(answer);
        }

        double percentage = totalMarks > 0
                ? (earnedMarks * 100.0 / totalMarks) : 0.0;

        session.setStatus(SessionStatus.SUBMITTED);
        session.setSubmittedAt(LocalDateTime.now());
        session.setScore(BigDecimal.valueOf(earnedMarks).setScale(2, RoundingMode.HALF_UP));
        session.setTotalMarks(totalMarks);
        sessionRepository.save(session);

        log.info("Exam submitted: sessionId={} score={}/{} ({}%)",
                sessionId, earnedMarks, totalMarks,
                String.format("%.1f", percentage));

        return Map.of(
                "sessionId",   sessionId,
                "score",       earnedMarks,
                "totalMarks",  totalMarks,
                "percentage",  Math.round(percentage * 10.0) / 10.0,
                "answered",    savedAnswers.size(),
                "totalQuestions", questions.size(),
                "status",      "SUBMITTED"
        );
    }

    // ── Get session results ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExamSession getSessionResult(Long sessionId, String studentEmail) {
        ExamSession session = sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (!session.getStudent().getEmail().equals(studentEmail)) {
            throw new IllegalStateException("Session does not belong to you");
        }
        return session;
    }

    // ── Get all sessions for a student ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamSession> getStudentSessions(String studentEmail) {
        User student = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", studentEmail));
        return sessionRepository.findByStudentIdOrderByStartedAtDesc(student.getId());
    }

    // ── Get saved answers for resume ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<StudentAnswer> getSavedAnswers(Long sessionId, String studentEmail) {
        getActiveSession(sessionId, studentEmail); // ownership check
        @SuppressWarnings("unchecked")
        List<StudentAnswer> answers = em.createQuery(
                "SELECT a FROM StudentAnswer a WHERE a.session.id = :sid")
                .setParameter("sid", sessionId)
                .getResultList();
        return answers;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ExamSession getActiveSession(Long sessionId, String studentEmail) {
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (!session.getStudent().getEmail().equals(studentEmail)) {
            throw new IllegalStateException("Session does not belong to you");
        }
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Session is not active (status=" + session.getStatus() + ")");
        }
        return session;
    }

    private void validateAnswer(String answer) {
        if (answer != null && !answer.matches("[AaBbCcDd]")) {
            throw new IllegalArgumentException(
                    "Invalid answer value: must be A, B, C, or D");
        }
    }
}
