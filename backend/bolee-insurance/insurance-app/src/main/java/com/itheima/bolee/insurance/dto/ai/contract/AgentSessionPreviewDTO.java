package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AgentSessionPreviewDTO {
    @JsonProperty("session_id")
    private String sessionId;
    private String title;
    private String preview;
}
