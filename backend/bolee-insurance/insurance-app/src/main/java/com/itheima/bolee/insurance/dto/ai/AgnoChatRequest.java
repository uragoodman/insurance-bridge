package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

/** Service-to-service payload for the private Agno endpoint. */
@Data
@AllArgsConstructor
public class AgnoChatRequest {

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("authorization_context")
    private String authorizationContext;

    @JsonProperty("selected_policy_id")
    private String selectedPolicyId;

    private String message;
}
