package com.itheima.bolee.insurance.dto.ai.contract;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class AgentSessionPreviewsResponseDTO {
    private List<AgentSessionPreviewDTO> sessions = new ArrayList<AgentSessionPreviewDTO>();
}
