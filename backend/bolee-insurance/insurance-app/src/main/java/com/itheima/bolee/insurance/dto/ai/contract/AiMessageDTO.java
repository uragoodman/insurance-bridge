package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Customer-visible chat message; tool output and hidden reasoning are excluded. */
@Data
@NoArgsConstructor
public class AiMessageDTO {
    private String messageId;
    private String turnId;
    private AiMessageRole role;
    private String content;
    private Instant createdAt;
    private AiMessageStructuredDataDTO structuredData;
}
