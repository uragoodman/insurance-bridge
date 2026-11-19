package com.itheima.bolee.insurance.pojo.ai;

import lombok.Data;

import java.util.List;

@Data
public class AiRecommendationAttributeRow {
    private Long id;
    private Long categoryId;
    private String attributeCode;
    private Integer definitionVersion;
    private String displayName;
    private String valueType;
    private String unitCode;
    private Boolean allowHardFilter;
    private Boolean allowPreference;
    private Boolean allowCompare;
    private Boolean aiEnabled;
    private String sensitivityLevel;
    private String status;
    private String dataState;
    private java.time.LocalDateTime effectiveFrom;
    private java.time.LocalDateTime effectiveTo;
    private List<String> operators;
}
