package com.aiproctor.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "exam_sessions",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_student_exam",
        columnNames = {"student_id", "exam_id"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('IN_PROGRESS','SUBMITTED','TERMINATED')")
    @Builder.Default
    private SessionStatus status = SessionStatus.IN_PROGRESS;

    @Column(name = "score", precision = 6, scale = 2)
    private BigDecimal score;

    @Column(name = "total_marks")
    private Integer totalMarks;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "violation_count", nullable = false)
    @Builder.Default
    private Integer violationCount = 0;

    @Column(name = "is_flagged", nullable = false)
    @Builder.Default
    private Boolean isFlagged = false;

    @Column(name = "terminated_reason", length = 255)
    private String terminatedReason;

    // ── Relationships ────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<StudentAnswer> answers = new ArrayList<>();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Violation> violations = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
    }

    // ── Status enum ──────────────────────────────────────────────────────────

    public enum SessionStatus { IN_PROGRESS, SUBMITTED, TERMINATED }
}
