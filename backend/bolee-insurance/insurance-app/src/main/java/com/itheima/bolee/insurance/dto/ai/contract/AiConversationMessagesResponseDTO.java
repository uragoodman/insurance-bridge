package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Cursor-paged customer-visible conversation history. */
@Data
@NoArgsConstructor
public class AiConversationMessagesResponseDTO {
    private String sessionId;
    private List<AiMessageDTO> messages = new ArrayList<AiMessageDTO>();
    private String nextCursor;
    private Boolean hasMore = Boolean.FALSE;
}
