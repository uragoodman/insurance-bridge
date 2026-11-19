package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Python-to-Java private history response. Java must filter tool-only records before customer output. */
@Data
@NoArgsConstructor
public class AgentHistoryPageDTO {
    private List<AgentHistoryMessageDTO> messages = new ArrayList<AgentHistoryMessageDTO>();
    @JsonProperty("next_cursor")
    private String nextCursor;
    @JsonProperty("has_more")
    private Boolean hasMore;
}
