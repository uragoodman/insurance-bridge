package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationResponseVO {
    private String requestId;
    private String status;
    private String evaluationCompleteness;
    private List<String> missingCriteria;
    private List<String> unsupportedCriteria;
    private List<AiRecommendationUnresolvedCriteriaVO> unresolvedCriteria;
    private Integer excludedProductCount;
    private List<AiRecommendationCandidateVO> candidates;
    private Instant evaluatedAt;
    private String catalogVersion;
    private Integer ruleVersion;
    private String engineVersion;
}
