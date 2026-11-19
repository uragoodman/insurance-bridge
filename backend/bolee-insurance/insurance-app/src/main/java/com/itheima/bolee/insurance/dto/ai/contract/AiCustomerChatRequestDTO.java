package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Customer-facing chat request. User identity is always resolved by Java authentication. */
@Data
@NoArgsConstructor
public class AiCustomerChatRequestDTO {
    private String clientMessageId;
    private String sessionId;
    private String message;
}
