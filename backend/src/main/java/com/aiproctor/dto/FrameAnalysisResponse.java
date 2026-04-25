package com.aiproctor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FrameAnalysisResponse {

    @JsonProperty("session_id")
    private Long sessionId;

    @JsonProperty("student_id")
    private Long studentId;

    @JsonProperty("exam_id")
    private Long examId;

    @JsonProperty("face_count")
    private int faceCount;

    @JsonProperty("violations")
    private List<AiViolation> violations;

    @JsonProperty("processing_time_ms")
    private double processingTimeMs;

    @JsonProperty("frame_width")
    private int frameWidth;

    @JsonProperty("frame_height")
    private int frameHeight;

    @JsonProperty("clean")
    private boolean clean;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AiViolation {

        @JsonProperty("violation_type")
        private String violationType;

        @JsonProperty("severity")
        private String severity;

        @JsonProperty("confidence")
        private double confidence;

        @JsonProperty("description")
        private String description;
    }
}
