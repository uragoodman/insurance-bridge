package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationCriteriaVO {
    private String code;
    private Integer definitionVersion;
    private String name;
    private String valueType;
    private String unit;
    private List<String> allowedOperators;
    private List<String> allowedUses;
    private String activeRuleMode;
    private Boolean requiredForClarification;
    private List<AiRecommendationOptionVO> options;
}
