package com.itheima.bolee.insurance.pojo.ai;

import lombok.Data;

@Data
public class AiRecommendationRuleRow {
    private Long id;
    private Long definitionId;
    private Long definitionCategoryId;
    private String ruleMode;
    private Boolean requiredForClarification;
    private Integer scoreWeight;
    private Integer priority;
    private String reasonCode;
    private String reasonMessage;
    private String missingPolicy;
}
