package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class AiConversationListItemDTO {
    private String sessionId;
    private String title;
    private String preview;
    private Instant createdAt;
    private Instant lastMessageAt;
}
