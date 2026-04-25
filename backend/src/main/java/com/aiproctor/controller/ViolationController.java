package com.aiproctor.controller;

import com.aiproctor.dto.FrameAnalysisRequest;
import com.aiproctor.dto.FrameAnalysisResponse;
import com.aiproctor.dto.ViolationDTO;
import com.aiproctor.model.User;
import com.aiproctor.model.Violation;
import com.aiproctor.service.AiProxyService;
import com.aiproctor.service.ViolationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * POST /api/violations              — log a browser-detected violation (tab switch, fullscreen exit)
 * GET  /api/violations/status/{sid} — proctoring heartbeat
 * POST /api/violations/analyze-frame — proxy webcam frame to Python AI service
 * GET  /api/violations/session/{sid} — all violations for a session (admin/student)
 */
@RestController
@RequestMapping("/api/violations")
@RequiredArgsConstructor
public class ViolationController {

    private final ViolationService violationService;
    private final AiProxyService   aiProxyService;

    /** Log a client-detected violation (tab switch, fullscreen exit, etc.) */
    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<ViolationDTO> logViolation(
            @Valid @RequestBody ViolationDTO dto,
            @AuthenticationPrincipal User user) {

        ViolationDTO response = violationService.logViolation(dto, user.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** Proctoring heartbeat: returns current violation count + session status */
    @GetMapping("/status/{sessionId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> getProctorStatus(
            @PathVariable Long sessionId,
            @AuthenticationPrincipal User user) {

        return ResponseEntity.ok(violationService.getProctorStatus(sessionId, user.getEmail()));
    }

    /**
     * Proxy a webcam frame to the Python AI Detection Service.
     * Returns 503 if the AI service is offline (exam continues unaffected).
     */
    @PostMapping("/analyze-frame")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<FrameAnalysisResponse> analyzeFrame(
            @Valid @RequestBody FrameAnalysisRequest request,
            @AuthenticationPrincipal User user) {

        FrameAnalysisResponse response = aiProxyService.analyzeFrame(
                request, user.getId(), user.getEmail());

        if (response == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok(response);
    }

    /** All violations for a session — accessible by the owning student or any admin */
    @GetMapping("/session/{sessionId}")
    @PreAuthorize("hasAnyRole('STUDENT','ADMIN')")
    public ResponseEntity<List<Violation>> getSessionViolations(
            @PathVariable Long sessionId) {

        return ResponseEntity.ok(violationService.getViolationsForSession(sessionId));
    }
}
