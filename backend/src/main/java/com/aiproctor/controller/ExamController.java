package com.aiproctor.controller;

import com.aiproctor.model.Exam;
import com.aiproctor.model.ExamQuestion;
import com.aiproctor.model.User;
import com.aiproctor.service.ExamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Student + Admin accessible exam endpoints.
 *
 * GET  /api/exams               — list available exams (student: active/not-ended)
 * GET  /api/exams/{id}          — exam detail (with metadata, no questions)
 * GET  /api/exams/{id}/questions — exam questions (correctAnswer hidden for students)
 */
@RestController
@RequestMapping("/api/exams")
@RequiredArgsConstructor
public class ExamController {

    private final ExamService examService;

    /** Available exams (active and not yet ended) */
    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<List<Exam>> getAvailableExams() {
        return ResponseEntity.ok(examService.getAvailableExams());
    }

    /** Exam detail — returns metadata (not questions) */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<Map<String, Object>> getExam(@PathVariable Long id) {
        Exam exam = examService.getExamById(id);
        return ResponseEntity.ok(toExamSummary(exam));
    }

    /**
     * Exam questions for students — correctAnswer is stripped.
     * Admins receive the full question including correctAnswer.
     */
    @GetMapping("/{id}/questions")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getQuestions(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {

        List<ExamQuestion> questions = examService.getQuestionsForStudent(id);
        boolean isAdmin = user.getRole() == User.Role.ADMIN;

        List<Map<String, Object>> result = questions.stream()
                .map(q -> questionToMap(q, isAdmin))
                .toList();

        return ResponseEntity.ok(result);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Map<String, Object> toExamSummary(Exam exam) {
        return Map.of(
                "id",                   exam.getId(),
                "title",                exam.getTitle(),
                "description",          exam.getDescription() != null ? exam.getDescription() : "",
                "durationMinutes",      exam.getDurationMinutes(),
                "startTime",            exam.getStartTime(),
                "endTime",              exam.getEndTime(),
                "isActive",             exam.getIsActive(),
                "maxViolations",        exam.getMaxViolations(),
                "webcamRequired",       exam.getWebcamRequired() == 1,
                "fullscreenRequired",   exam.getFullscreenRequired() == 1
        );
    }

    private Map<String, Object> questionToMap(ExamQuestion q, boolean includeAnswer) {
        var map = new java.util.LinkedHashMap<String, Object>();
        map.put("id",            q.getId());
        map.put("questionText",  q.getQuestionText());
        map.put("optionA",       q.getOptionA());
        map.put("optionB",       q.getOptionB());
        map.put("optionC",       q.getOptionC());
        map.put("optionD",       q.getOptionD());
        map.put("marks",         q.getMarks());
        map.put("questionOrder", q.getQuestionOrder());
        if (includeAnswer) {
            map.put("correctAnswer", q.getCorrectAnswer());
        }
        return map;
    }
}
