package com.aiproctor.service;

import com.aiproctor.exception.ResourceNotFoundException;
import com.aiproctor.model.ExamSession;
import com.aiproctor.model.ExamSession.SessionStatus;
import com.aiproctor.model.User;
import com.aiproctor.model.Violation;
import com.aiproctor.repository.SessionRepository;
import com.aiproctor.repository.UserRepository;
import com.aiproctor.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final SessionRepository sessionRepository;
    private final ViolationRepository violationRepository;
    private final UserRepository userRepository;

    // ── Live monitoring: all in-progress sessions ─────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamSession> getLiveSessions() {
        return sessionRepository.findAllInProgress();
    }

    // ── All sessions for a specific exam ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamSession> getSessionsByExam(Long examId) {
        return sessionRepository.findByExamIdOrderByStartedAtDesc(examId);
    }

    // ── Exam results leaderboard ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamSession> getExamResults(Long examId) {
        return sessionRepository.findSubmittedByExamIdOrderByScore(examId);
    }

    // ── Session detail ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExamSession getSessionDetail(Long sessionId) {
        return sessionRepository.findByIdWithDetails(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
    }

    // ── Violations for a session ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Violation> getViolationsForSession(Long sessionId) {
        return violationRepository.findBySessionIdWithDetails(sessionId);
    }

    // ── Violations for an exam ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Violation> getViolationsForExam(Long examId) {
        return violationRepository.findByExamIdWithStudent(examId);
    }

    // ── Flag / unflag a session ───────────────────────────────────────────────

    @Transactional
    public ExamSession flagSession(Long sessionId, boolean flagged) {
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));
        session.setIsFlagged(flagged);
        ExamSession saved = sessionRepository.save(session);
        log.info("Session {} {} by admin", sessionId, flagged ? "FLAGGED" : "UNFLAGGED");
        return saved;
    }

    // ── Terminate a session manually ──────────────────────────────────────────

    @Transactional
    public ExamSession terminateSession(Long sessionId, String reason) {
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Session is not in progress (status=" + session.getStatus() + ")");
        }

        session.setStatus(SessionStatus.TERMINATED);
        session.setTerminatedReason(reason != null ? reason : "Terminated by admin");
        ExamSession saved = sessionRepository.save(session);
        log.warn("Session {} TERMINATED by admin: {}", sessionId, reason);
        return saved;
    }

    // ── All students ──────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<User> getAllStudents() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.STUDENT)
                .toList();
    }

    // ── Summary statistics for an exam ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Object> getExamStats(Long examId) {
        List<ExamSession> sessions = sessionRepository.findByExamIdOrderByStartedAtDesc(examId);

        long total      = sessions.size();
        long submitted  = sessions.stream().filter(s -> s.getStatus() == SessionStatus.SUBMITTED).count();
        long terminated = sessions.stream().filter(s -> s.getStatus() == SessionStatus.TERMINATED).count();
        long inProgress = sessions.stream().filter(s -> s.getStatus() == SessionStatus.IN_PROGRESS).count();
        long flagged    = sessions.stream().filter(ExamSession::getIsFlagged).count();

        double avgScore = sessions.stream()
                .filter(s -> s.getScore() != null && s.getStatus() == SessionStatus.SUBMITTED)
                .mapToDouble(s -> s.getScore().doubleValue())
                .average()
                .orElse(0.0);

        long totalViolations = violationRepository.findByExamIdWithStudent(examId).size();

        return Map.of(
                "examId",          examId,
                "totalSessions",   total,
                "submitted",       submitted,
                "terminated",      terminated,
                "inProgress",      inProgress,
                "flagged",         flagged,
                "averageScore",    Math.round(avgScore * 100.0) / 100.0,
                "totalViolations", totalViolations
        );
    }
}
