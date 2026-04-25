package com.aiproctor.service;

import com.aiproctor.dto.FrameAnalysisRequest;
import com.aiproctor.dto.FrameAnalysisResponse;
import com.aiproctor.dto.ViolationDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class AiProxyService {

    private final RestTemplate restTemplate;
    private final ViolationService violationService;
    private final AtomicInteger frameCounter = new AtomicInteger(0);

    @Value("${app.ai.service.url:http://localhost:8001}")
    private String aiServiceUrl;

    @Value("${app.ai.service.secret:change-me-in-production}")
    private String aiServiceSecret;

    public AiProxyService(
            RestTemplateBuilder builder,
            ViolationService violationService,
            @Value("${app.ai.service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.ai.service.read-timeout-ms:8000}") int readTimeoutMs) {

        // FIX: Removed connectTimeout and readTimeout which caused compilation error
        this.restTemplate = builder.build();
        this.violationService = violationService;
    }

    public FrameAnalysisResponse analyzeFrame(
            FrameAnalysisRequest request,
            Long studentId,
            String studentEmail) {

        com.aiproctor.dto.FrameAnalysisRequest aiPayload =
                new com.aiproctor.dto.FrameAnalysisRequest();

        aiPayload.setSessionId(request.getSessionId());
        aiPayload.setExamId(request.getExamId());
        aiPayload.setFrameBase64(request.getFrameBase64());
        aiPayload.setSequenceNum(frameCounter.incrementAndGet());
        aiPayload.setTimestampMs(Instant.now().toEpochMilli());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-AI-Secret", aiServiceSecret);

        HttpEntity<com.aiproctor.dto.FrameAnalysisRequest> entity =
                new HttpEntity<>(aiPayload, headers);

        FrameAnalysisResponse aiResponse;

        try {
            ResponseEntity<FrameAnalysisResponse> resp = restTemplate.exchange(
                    aiServiceUrl + "/analyze-frame",
                    HttpMethod.POST,
                    entity,
                    FrameAnalysisResponse.class
            );

            aiResponse = resp.getBody();

        } catch (ResourceAccessException e) {
            log.warn("AI service unreachable (session={}): {}",
                    request.getSessionId(), e.getMessage());
            return null;

        } catch (Exception e) {
            log.error("AI service error (session={}): {}",
                    request.getSessionId(), e.getMessage());
            return null;
        }

        if (aiResponse == null || aiResponse.isClean()) {
            return aiResponse;
        }

        List<FrameAnalysisResponse.AiViolation> aiViolations = aiResponse.getViolations();
        List<ViolationDTO> logged = new ArrayList<>();

        if (aiViolations != null) {

            for (FrameAnalysisResponse.AiViolation v : aiViolations) {

                try {

                    ViolationDTO dto = new ViolationDTO();
                    dto.setSessionId(request.getSessionId());
                    dto.setViolationType(v.getViolationType());
                    dto.setDescription(v.getDescription());
                    dto.setAiConfidence(v.getConfidence() * 100);

                    ViolationDTO saved = violationService.logViolation(dto, studentEmail);
                    logged.add(saved);

                    if (saved.isSessionTerminated()) {
                        log.warn("Session {} terminated by AI detection", request.getSessionId());
                        break;
                    }

                } catch (Exception ex) {

                    log.error("Failed to log AI violation for session {}: {}",
                            request.getSessionId(), ex.getMessage());
                }
            }
        }

        log.info("Frame processed: session={} faces={} violations={} aiTime={}ms",
                request.getSessionId(),
                aiResponse.getFaceCount(),
                aiResponse.getViolations() != null ? aiResponse.getViolations().size() : 0,
                aiResponse.getProcessingTimeMs());

        return aiResponse;
    }
}

