package com.aiproctor.dto;

import com.aiproctor.model.Violation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ViolationDTO {

    // ── For incoming requests ────────────────────────────────────────────────

    @NotNull(message = "sessionId is required")
    private Long sessionId;

    @NotBlank(message = "violationType is required")
    private String violationType;

    private String description;
    private String frameBase64;   // optional; stored in Phase 5
    private Double aiConfidence;

    // ── For outgoing responses ───────────────────────────────────────────────

    private Long id;
    private Long studentId;
    private String studentName;
    private Long examId;
    private String severity;
    private LocalDateTime detectedAt;
    private long sessionViolationCount;
    private boolean sessionTerminated;

    public static ViolationDTO fromEntity(Violation v,
                                          long totalCount,
                                          boolean terminated) {
        return ViolationDTO.builder()
                .id(v.getId())
                .sessionId(v.getSession().getId())
                .studentId(v.getStudent().getId())
                .studentName(v.getStudent().getName())
                .examId(v.getExam().getId())
                .violationType(v.getViolationType())
                .severity(v.getSeverity())
                .description(v.getDescription())
                .aiConfidence(v.getAiConfidence() != null
                        ? v.getAiConfidence().doubleValue() : null)
                .detectedAt(v.getDetectedAt())
                .sessionViolationCount(totalCount)
                .sessionTerminated(terminated)
                .build();
    }
}
