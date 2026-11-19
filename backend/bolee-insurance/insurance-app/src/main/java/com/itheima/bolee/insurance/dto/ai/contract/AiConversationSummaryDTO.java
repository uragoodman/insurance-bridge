package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;
import com.itheima.bolee.insurance.pojo.ai.AiConversationStatus;

import java.time.Instant;

/** Customer-visible conversation metadata; never includes the Agno session identifier. */
@Data
@NoArgsConstructor
public class AiConversationSummaryDTO {
    private String sessionId;
    private AiConversationStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastMessageAt;
}
