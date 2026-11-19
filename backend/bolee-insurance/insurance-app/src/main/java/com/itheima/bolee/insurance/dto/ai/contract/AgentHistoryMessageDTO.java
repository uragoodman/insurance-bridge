package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Private Python response. runId is consumed by Java and never returned to the customer. */
@Data
@NoArgsConstructor
public class AgentHistoryMessageDTO {
    @JsonProperty("message_id")
    private String messageId;
    @JsonProperty("run_id")
    private String runId;
    private AiMessageRole role;
    private String content;
    @JsonProperty("created_at")
    private Instant createdAt;
}
