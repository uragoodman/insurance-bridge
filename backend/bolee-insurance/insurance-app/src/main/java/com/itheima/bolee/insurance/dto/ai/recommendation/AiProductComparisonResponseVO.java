package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProductComparisonResponseVO {
    private String requestId;
    private String status;
    private String categoryId;
    private String catalogVersion;
    private Integer ruleVersion;
    private List<AiProductComparisonProductVO> products;
    private List<AiRecommendationUnresolvedCriteriaVO> unresolvedCriteria;
    private Instant comparedAt;
}
