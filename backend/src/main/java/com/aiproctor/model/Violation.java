package com.aiproctor.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "violations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Violation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ExamSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Column(name = "violation_type", nullable = false, length = 50)
    private String violationType;

    @Column(name = "severity", nullable = false, length = 10)
    @Builder.Default
    private String severity = "MEDIUM";

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "snapshot_url", length = 1000)
    private String snapshotUrl;

    @Column(name = "ai_confidence", precision = 5, scale = 2)
    private BigDecimal aiConfidence;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
    }

    // ── Constants for violation types ────────────────────────────────────────
    public static final String TYPE_TAB_SWITCH      = "TAB_SWITCH";
    public static final String TYPE_FULLSCREEN_EXIT  = "FULLSCREEN_EXIT";
    public static final String TYPE_MULTIPLE_FACES   = "MULTIPLE_FACES";
    public static final String TYPE_NO_FACE          = "NO_FACE";
    public static final String TYPE_PHONE_DETECTED   = "PHONE_DETECTED";
    public static final String TYPE_LOOKING_AWAY     = "LOOKING_AWAY";
    public static final String TYPE_UNKNOWN          = "UNKNOWN";

    // ── Constants for severity ───────────────────────────────────────────────
    public static final String SEV_LOW      = "LOW";
    public static final String SEV_MEDIUM   = "MEDIUM";
    public static final String SEV_HIGH     = "HIGH";
    public static final String SEV_CRITICAL = "CRITICAL";
}
