package com.aiproctor.controller;

import com.aiproctor.model.Exam;
import com.aiproctor.model.ExamQuestion;
import com.aiproctor.model.ExamSession;
import com.aiproctor.model.User;
import com.aiproctor.model.Violation;
import com.aiproctor.service.AdminService;
import com.aiproctor.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * All routes under /api/admin/** require ROLE_ADMIN.
 *
 * Exam management:
 *   GET    /api/admin/exams
 *   POST   /api/admin/exams
 *   GET    /api/admin/exams/{id}
 *   PUT    /api/admin/exams/{id}
 *   DELETE /api/admin/exams/{id}
 *   POST   /api/admin/exams/{id}/questions
 *
 * Session monitoring:
 *   GET    /api/admin/sessions/live
 *   GET    /api/admin/sessions/exam/{examId}
 *   GET    /api/admin/sessions/{id}
 *   POST   /api/admin/sessions/{id}/flag
 *   POST   /api/admin/sessions/{id}/terminate
 *
 * Results & violations:
 *   GET    /api/admin/exams/{id}/results
 *   GET    /api/admin/exams/{id}/stats
 *   GET    /api/admin/exams/{id}/violations
 *   GET    /api/admin/sessions/{id}/violations
 *
 * Users:
 *   GET    /api/admin/students
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final ExamService  examService;
    private final AdminService adminService;

    // ════════════════════════════════════════════════════════════════
    // EXAM MANAGEMENT
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/exams")
    public ResponseEntity<List<Exam>> getAllExams() {
        return ResponseEntity.ok(examService.getAllExams());
    }

    @PostMapping("/exams")
    public ResponseEntity<Exam> createExam(
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal User admin) {

        Exam exam = examService.createExam(payload, admin.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(exam);
    }

    @GetMapping("/exams/{id}")
    public ResponseEntity<Exam> getExam(@PathVariable Long id) {
        return ResponseEntity.ok(examService.getExamById(id));
    }

    @PutMapping("/exams/{id}")
    public ResponseEntity<Exam> updateExam(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        return ResponseEntity.ok(examService.updateExam(id, payload));
    }

    /** Soft-delete: sets is_active = false */
    @DeleteMapping("/exams/{id}")
    public ResponseEntity<Map<String, String>> deactivateExam(@PathVariable Long id) {
        examService.deactivateExam(id);
        return ResponseEntity.ok(Map.of("message", "Exam deactivated successfully"));
    }

    @PostMapping("/exams/{id}/questions")
    public ResponseEntity<ExamQuestion> addQuestion(
            @PathVariable Long id,
            @RequestBody Map<String, Object> payload) {

        ExamQuestion q = examService.addQuestion(id, payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(q);
    }

    // ════════════════════════════════════════════════════════════════
    // SESSION MONITORING
    // ════════════════════════════════════════════════════════════════

    /** All currently in-progress sessions */
    @GetMapping("/sessions/live")
    public ResponseEntity<List<ExamSession>> getLiveSessions() {
        return ResponseEntity.ok(adminService.getLiveSessions());
    }

    /** All sessions for a given exam */
    @GetMapping("/sessions/exam/{examId}")
    public ResponseEntity<List<ExamSession>> getSessionsByExam(@PathVariable Long examId) {
        return ResponseEntity.ok(adminService.getSessionsByExam(examId));
    }

    /** Single session detail */
    @GetMapping("/sessions/{id}")
    public ResponseEntity<ExamSession> getSession(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getSessionDetail(id));
    }

    /** Flag/unflag a session for review */
    @PostMapping("/sessions/{id}/flag")
    public ResponseEntity<ExamSession> flagSession(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        boolean flagged = body == null || !Boolean.FALSE.equals(body.get("flagged"));
        return ResponseEntity.ok(adminService.flagSession(id, flagged));
    }

    /** Manually terminate an in-progress session */
    @PostMapping("/sessions/{id}/terminate")
    public ResponseEntity<ExamSession> terminateSession(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {

        String reason = (body != null && body.get("reason") != null)
                ? body.get("reason").toString() : "Terminated by admin";
        return ResponseEntity.ok(adminService.terminateSession(id, reason));
    }

    // ════════════════════════════════════════════════════════════════
    // RESULTS & VIOLATIONS
    // ════════════════════════════════════════════════════════════════

    /** Submitted sessions ordered by score (leaderboard) */
    @GetMapping("/exams/{id}/results")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ExamSession>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getExamResults(id));
    }

    /** Aggregate statistics for an exam */
    @GetMapping("/exams/{id}/stats")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getExamStats(id));
    }

    /** All violations logged against an exam */
    @GetMapping("/exams/{id}/violations")
    public ResponseEntity<List<Violation>> getExamViolations(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getViolationsForExam(id));
    }

    /** All violations for a single session */
    @GetMapping("/sessions/{id}/violations")
    public ResponseEntity<List<Violation>> getSessionViolations(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getViolationsForSession(id));
    }

    // ════════════════════════════════════════════════════════════════
    // USERS
    // ════════════════════════════════════════════════════════════════

    @GetMapping("/students")
    public ResponseEntity<List<User>> getAllStudents() {
        return ResponseEntity.ok(adminService.getAllStudents());
    }
}
