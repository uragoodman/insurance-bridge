package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Java-to-Python turn envelope. Sensitive grants are runtime dependencies, not prompt text. */
@Data
@NoArgsConstructor
public class AiAgentTurnRequestDTO {
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("session_id")
    private String sessionId;
    @JsonProperty("client_message_id")
    private String clientMessageId;
    @JsonProperty("turn_id")
    private String turnId;
    @JsonProperty("trace_id")
    private String traceId;
    @JsonProperty("authorization_context")
    private String authorizationContext;
    private String message;
}
