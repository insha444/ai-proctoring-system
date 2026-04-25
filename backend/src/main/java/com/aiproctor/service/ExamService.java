package com.aiproctor.service;

import com.aiproctor.exception.ResourceNotFoundException;
import com.aiproctor.model.Exam;
import com.aiproctor.model.ExamQuestion;
import com.aiproctor.model.User;
import com.aiproctor.repository.ExamRepository;
import com.aiproctor.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamService {

    private final ExamRepository examRepository;
    private final UserRepository userRepository;

    // ── Student: list available exams ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Exam> getAvailableExams() {
        return examRepository.findAvailableExams(LocalDateTime.now());
    }

    // ── Student / Admin: get exam by ID (with questions) ─────────────────────

    @Transactional(readOnly = true)
    public Exam getExamById(Long id) {
        return examRepository.findByIdWithQuestions(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", id));
    }

    /**
     * Returns questions for students — correct_answer field must be
     * stripped by the controller before sending to the client.
     */
    @Transactional(readOnly = true)
    public List<ExamQuestion> getQuestionsForStudent(Long examId) {
        Exam exam = getExamById(examId);
        return exam.getQuestions();
    }

    // ── Admin: all exams (including inactive) ────────────────────────────────

    @Transactional(readOnly = true)
    public List<Exam> getAllExams() {
        return examRepository.findAll();
    }

    // ── Admin: create exam ───────────────────────────────────────────────────

    @Transactional
    public Exam createExam(Map<String, Object> payload, String adminEmail) {
        User admin = userRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User", "email", adminEmail));

        Exam exam = Exam.builder()
                .title((String) payload.get("title"))
                .description((String) payload.get("description"))
                .durationMinutes(getInt(payload, "durationMinutes", 60))
                .startTime(LocalDateTime.parse((String) payload.get("startTime")))
                .endTime(LocalDateTime.parse((String) payload.get("endTime")))
                .createdBy(admin)
                .isActive(true)
                .maxViolations(getInt(payload, "maxViolations", 5))
                .webcamRequired(getInt(payload, "webcamRequired", 1))
                .fullscreenRequired(getInt(payload, "fullscreenRequired", 1))
                .tabSwitchAllowed(getInt(payload, "tabSwitchAllowed", 0))
                .frameCaptureInterval(getInt(payload, "frameCaptureInterval", 10))
                .build();

        Exam saved = examRepository.save(exam);
        log.info("Exam created: id={} title='{}' by={}", saved.getId(), saved.getTitle(), adminEmail);
        return saved;
    }

    // ── Admin: update exam ───────────────────────────────────────────────────

    @Transactional
    public Exam updateExam(Long id, Map<String, Object> payload) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", id));

        if (payload.containsKey("title"))
            exam.setTitle((String) payload.get("title"));
        if (payload.containsKey("description"))
            exam.setDescription((String) payload.get("description"));
        if (payload.containsKey("durationMinutes"))
            exam.setDurationMinutes(getInt(payload, "durationMinutes", exam.getDurationMinutes()));
        if (payload.containsKey("startTime"))
            exam.setStartTime(LocalDateTime.parse((String) payload.get("startTime")));
        if (payload.containsKey("endTime"))
            exam.setEndTime(LocalDateTime.parse((String) payload.get("endTime")));
        if (payload.containsKey("isActive"))
            exam.setIsActive((Boolean) payload.get("isActive"));
        if (payload.containsKey("maxViolations"))
            exam.setMaxViolations(getInt(payload, "maxViolations", exam.getMaxViolations()));

        Exam updated = examRepository.save(exam);
        log.info("Exam updated: id={}", id);
        return updated;
    }

    // ── Admin: soft-delete (deactivate) ──────────────────────────────────────

    @Transactional
    public void deactivateExam(Long id) {
        Exam exam = examRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", id));
        exam.setIsActive(false);
        examRepository.save(exam);
        log.info("Exam deactivated: id={}", id);
    }

    // ── Admin: add question ───────────────────────────────────────────────────

    @Transactional
    public ExamQuestion addQuestion(Long examId, Map<String, Object> payload) {
        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam", examId));

        int nextOrder = exam.getQuestions().size() + 1;

        ExamQuestion q = ExamQuestion.builder()
                .exam(exam)
                .questionText((String) payload.get("questionText"))
                .optionA((String) payload.get("optionA"))
                .optionB((String) payload.get("optionB"))
                .optionC((String) payload.get("optionC"))
                .optionD((String) payload.get("optionD"))
                .correctAnswer(((String) payload.get("correctAnswer")).toUpperCase())
                .marks(getInt(payload, "marks", 1))
                .questionOrder(getInt(payload, "questionOrder", nextOrder))
                .build();

        exam.getQuestions().add(q);
        examRepository.save(exam);
        log.info("Question added to exam {}", examId);
        return q;
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private Integer getInt(Map<String, Object> map, String key, Integer defaultVal) {
        Object val = map.get(key);
        if (val == null) return defaultVal;
        if (val instanceof Integer i) return i;
        if (val instanceof Number n) return n.intValue();
        return Integer.parseInt(val.toString());
    }
}
