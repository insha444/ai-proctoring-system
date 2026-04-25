package com.aiproctor.service;

import com.aiproctor.dto.ViolationDTO;
import com.aiproctor.exception.ResourceNotFoundException;
import com.aiproctor.model.*;
import com.aiproctor.model.ExamSession.SessionStatus;
import com.aiproctor.repository.SessionRepository;
import com.aiproctor.repository.ViolationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViolationService {

    private final ViolationRepository violationRepository;
    private final SessionRepository sessionRepository;

    // ── Log a violation (student-side) ────────────────────────────────────────

    @Transactional
    public ViolationDTO logViolation(ViolationDTO dto, String studentEmail) {
        ExamSession session = sessionRepository.findById(dto.getSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Session", dto.getSessionId()));

        // Ownership check
        if (!session.getStudent().getEmail().equals(studentEmail)) {
            throw new IllegalStateException("Session does not belong to you");
        }
        // Guard: only log on active sessions
        if (session.getStatus() != SessionStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Session is not active (status=" + session.getStatus() + ")");
        }

        Violation violation = Violation.builder()
                .session(session)
                .student(session.getStudent())
                .exam(session.getExam())
                .violationType(dto.getViolationType())
                .severity(resolveSeverity(dto.getViolationType()))
                .description(dto.getDescription())
                .aiConfidence(dto.getAiConfidence() != null
                        ? BigDecimal.valueOf(dto.getAiConfidence()) : null)
                .build();

        violationRepository.save(violation);

        // Update denormalized count on session
        long totalCount = violationRepository.countBySessionId(session.getId());
        session.setViolationCount((int) totalCount);

        // Auto-terminate if threshold exceeded
        int maxViolations = session.getExam().getMaxViolations() != null
                ? session.getExam().getMaxViolations() : 5;
        boolean terminated = false;

        if (totalCount >= maxViolations) {
            session.setStatus(SessionStatus.TERMINATED);
            session.setTerminatedReason(
                    "Exceeded violation limit (" + maxViolations + ")");
            terminated = true;
            log.warn("Session {} TERMINATED — violation limit {} reached",
                    session.getId(), maxViolations);
        }

        sessionRepository.save(session);

        log.info("Violation logged: session={} type={} severity={} count={}/{}",
                session.getId(), violation.getViolationType(),
                violation.getSeverity(), totalCount, maxViolations);

        return ViolationDTO.fromEntity(violation, totalCount, terminated);
    }

    // ── Proctoring status heartbeat ───────────────────────────────────────────

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> getProctorStatus(Long sessionId, String studentEmail) {
        ExamSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session", sessionId));

        if (!session.getStudent().getEmail().equals(studentEmail)) {
            throw new IllegalStateException("Session does not belong to you");
        }

        Exam exam = session.getExam();
        long count = violationRepository.countBySessionId(sessionId);
        int max    = exam.getMaxViolations() != null ? exam.getMaxViolations() : 5;

        return java.util.Map.of(
                "sessionId",      sessionId,
                "violationCount", count,
                "maxViolations",  max,
                "webcamRequired", exam.getWebcamRequired() == 1,
                "fullscreenRequired", exam.getFullscreenRequired() == 1,
                "sessionActive",  session.getStatus() == SessionStatus.IN_PROGRESS,
                "sessionStatus",  session.getStatus().name(),
                "message",        session.getStatus() == SessionStatus.TERMINATED
                        ? "Session terminated: " + session.getTerminatedReason()
                        : "Active"
        );
    }

    // ── Get violations for a session ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Violation> getViolationsForSession(Long sessionId) {
        return violationRepository.findBySessionIdOrderByDetectedAtDesc(sessionId);
    }

    // ── Admin: get violations for an exam ─────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Violation> getViolationsForExam(Long examId) {
        return violationRepository.findByExamIdWithStudent(examId);
    }

    // ── Severity resolver ─────────────────────────────────────────────────────

    private String resolveSeverity(String violationType) {
        return switch (violationType) {
            case Violation.TYPE_MULTIPLE_FACES,
                 Violation.TYPE_PHONE_DETECTED   -> Violation.SEV_HIGH;
            case Violation.TYPE_TAB_SWITCH,
                 Violation.TYPE_FULLSCREEN_EXIT  -> Violation.SEV_MEDIUM;
            case Violation.TYPE_NO_FACE,
                 Violation.TYPE_LOOKING_AWAY     -> Violation.SEV_LOW;
            default                             -> Violation.SEV_MEDIUM;
        };
    }
}
