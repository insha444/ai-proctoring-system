package com.aiproctor.controller;

import com.aiproctor.model.ExamSession;
import com.aiproctor.model.StudentAnswer;
import com.aiproctor.model.User;
import com.aiproctor.service.SessionService;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping("/start")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> startExam(
            @RequestBody StartExamBody body,
            @AuthenticationPrincipal User user) {

        ExamSession session = sessionService.startExam(body.getExamId(), user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(sessionToMap(session));
    }

    @PostMapping("/{sessionId}/answer")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> saveAnswer(
            @PathVariable Long sessionId,
            @RequestBody AnswerBody body,
            @AuthenticationPrincipal User user) {

        StudentAnswer answer = sessionService.saveAnswer(
                sessionId, body.getQuestionId(),
                body.getSelectedAnswer(), user.getEmail());

        return ResponseEntity.ok(Map.of(
                "sessionId",      sessionId,
                "questionId",     answer.getQuestion().getId(),
                "selectedAnswer", answer.getSelectedAnswer() != null ? answer.getSelectedAnswer() : "",
                "answeredAt",     answer.getAnsweredAt(),
                "message",        "Answer saved"
        ));
    }

    @PostMapping("/{sessionId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> submitExam(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user) {

        Map<String, Object> result = sessionService.submitExam(sessionId, user.getEmail());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getSessionResult(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user) {

        ExamSession session = sessionService.getSessionResult(sessionId, user.getEmail());
        return ResponseEntity.ok(sessionToMap(session));
    }

    @GetMapping("/{sessionId}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Map<String, Object>>> getSavedAnswers(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user) {

        List<StudentAnswer> answers =
                sessionService.getSavedAnswers(sessionId, user.getEmail());

        List<Map<String, Object>> result = answers.stream()
                .map(a -> Map.<String, Object>of(
                        "questionId",     a.getQuestion().getId(),
                        "selectedAnswer", a.getSelectedAnswer() != null ? a.getSelectedAnswer() : "",
                        "answeredAt",     a.getAnsweredAt()
                ))
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> mySessions(
            @AuthenticationPrincipal User user) {

        List<ExamSession> sessions = sessionService.getStudentSessions(user.getEmail());
        return ResponseEntity.ok(sessions.stream().map(this::sessionToMap).toList());
    }


    // ── Inner request bodies ──────────────────────────────────────────────────

    @Data
    static class StartExamBody {
        @NotNull private Long examId;
    }

    @Data
    static class AnswerBody {
        @NotNull private Long   questionId;
        @NotNull private String selectedAnswer;
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private Map<String, Object> sessionToMap(ExamSession s) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id",             s.getId());
        map.put("examId",         s.getExam().getId());
        map.put("examTitle",      s.getExam().getTitle());
        map.put("studentId",      s.getStudent().getId());
        map.put("status",         s.getStatus().name());
        map.put("score",          s.getScore());
        map.put("totalMarks",     s.getTotalMarks());
        map.put("startedAt",      s.getStartedAt());
        map.put("submittedAt",    s.getSubmittedAt());
        map.put("violationCount", s.getViolationCount());
        map.put("isFlagged",      s.getIsFlagged());
        if (s.getTerminatedReason() != null)
            map.put("terminatedReason", s.getTerminatedReason());
        return map;
    }
}
