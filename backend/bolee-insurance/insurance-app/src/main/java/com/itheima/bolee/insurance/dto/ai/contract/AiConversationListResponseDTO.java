package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AiConversationListResponseDTO {
    private List<AiConversationListItemDTO> conversations = new ArrayList<AiConversationListItemDTO>();
    private String nextCursor;
    private Boolean hasMore = Boolean.FALSE;
}
