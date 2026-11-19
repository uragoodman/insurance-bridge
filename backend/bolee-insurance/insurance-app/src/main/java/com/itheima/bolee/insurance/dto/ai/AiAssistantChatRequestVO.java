package com.itheima.bolee.insurance.dto.ai;

import lombok.Data;

/** Customer-facing chat request. User identity is resolved from the authenticated session. */
@Data
public class AiAssistantChatRequestVO {

    private String sessionId;

    private String message;

    /** Stable UUID generated once by the client and reused for retries. */
    private String clientMessageId;

    /** Optional policy selected in the authenticated policy browser; Java rechecks ownership. */
    private String selectedPolicyId;
}
