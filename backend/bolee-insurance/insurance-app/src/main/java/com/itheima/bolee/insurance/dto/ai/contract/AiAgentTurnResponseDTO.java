package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Python-to-Java response envelope for one Agent execution. */
@Data
@NoArgsConstructor
public class AiAgentTurnResponseDTO {
    @JsonProperty("session_id")
    private String sessionId;
    @JsonProperty("turn_id")
    private String turnId;
    @JsonProperty("run_id")
    private String runId;
    private String status;
    private String answer;
    private List<ProductCandidateDTO> recommendations = new ArrayList<ProductCandidateDTO>();
    private List<ProductSourceDTO> sources = new ArrayList<ProductSourceDTO>();
}
