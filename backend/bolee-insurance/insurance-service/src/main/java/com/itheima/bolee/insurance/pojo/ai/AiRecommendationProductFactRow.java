package com.itheima.bolee.insurance.pojo.ai;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AiRecommendationProductFactRow {
    private Long id;
    private Long profileVersionId;
    private Long definitionId;
    private String attributeCode;
    private Integer definitionVersion;
    private String valueStatus;
    private String valueCode;
    private BigDecimal valueNumber;
    private BigDecimal valueMin;
    private BigDecimal valueMax;
}
