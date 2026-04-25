package com.aiproctor.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ✅ ADD THIS IMPORT
import com.fasterxml.jackson.annotation.JsonIgnore;

@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    @Builder.Default
    private Integer durationMinutes = 60;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    // ✅ FIX 1: Prevent lazy loading issue
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ── Phase 3 proctoring config ─────────────────────────

    @Column(name = "max_violations", nullable = false)
    @Builder.Default
    private Integer maxViolations = 5;

    @Column(name = "webcam_required", nullable = false)
    @Builder.Default
    private Integer webcamRequired = 1;

    @Column(name = "fullscreen_required", nullable = false)
    @Builder.Default
    private Integer fullscreenRequired = 1;

    @Column(name = "tab_switch_allowed", nullable = false)
    @Builder.Default
    private Integer tabSwitchAllowed = 0;

    @Column(name = "frame_capture_interval", nullable = false)
    @Builder.Default
    private Integer frameCaptureInterval = 10;

    // ✅ FIX 2: Prevent recursion issue
    @JsonIgnore
    @OneToMany(mappedBy = "exam", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("questionOrder ASC")
    @Builder.Default
    private List<ExamQuestion> questions = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
