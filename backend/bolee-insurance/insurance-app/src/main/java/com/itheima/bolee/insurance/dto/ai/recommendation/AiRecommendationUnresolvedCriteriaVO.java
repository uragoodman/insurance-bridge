package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationUnresolvedCriteriaVO {
    private String code;
    private Integer affectedProductCount;
    private String reason;
}
