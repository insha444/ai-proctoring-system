package com.aiproctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FrameAnalysisRequest {

    @NotNull(message = "sessionId is required")
    @JsonProperty("session_id")
    private Long sessionId;

    @NotNull(message = "examId is required")
    @JsonProperty("exam_id")
    private Long examId;

    @NotBlank(message = "frameBase64 is required")
    @JsonProperty("frame_base64")
    private String frameBase64;

    @JsonProperty("sequence_num")
    private Integer sequenceNum;

    @JsonProperty("timestamp_ms")
    private Long timestampMs;
}
