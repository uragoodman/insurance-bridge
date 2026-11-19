package com.itheima.bolee.insurance.pojo.ai;

import lombok.Data;

@Data
public class AiRecommendationSnapshotRow {
    private Long id;
    private Long categoryId;
    private String catalogVersion;
    private Long ruleSetId;
    private Long ruleSetCategoryId;
    private Integer ruleSetVersion;
    private String scoreStrategy;
}
