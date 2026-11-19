package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationCriteriaCatalogVO {
    private String status;
    private String categoryId;
    private String catalogVersion;
    private Integer ruleVersion;
    private List<AiRecommendationCriteriaVO> criteria;
}
