package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentSessionPreviewsRequestDTO {
    @JsonProperty("session_ids")
    private List<String> sessionIds;
}
