package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Customer-facing response; structured facts are separate from generated prose. */
@Data
@NoArgsConstructor
public class AiCustomerChatResponseDTO {
    private String sessionId;
    private String clientMessageId;
    private String turnId;
    private String answer;
    private List<ProductCandidateDTO> recommendations = new ArrayList<ProductCandidateDTO>();
    private List<ProductSourceDTO> sources = new ArrayList<ProductSourceDTO>();
}
