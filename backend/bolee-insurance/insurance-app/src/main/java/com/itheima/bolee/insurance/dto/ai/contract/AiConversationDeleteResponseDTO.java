package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.itheima.bolee.insurance.pojo.ai.AiConversationStatus;

import java.time.Instant;

/** Acknowledges an idempotent deletion request; does not expose internal session IDs. */
@Data
@NoArgsConstructor
public class AiConversationDeleteResponseDTO {
    private String sessionId;
    private AiConversationStatus status;
    private Instant acceptedAt;
}
