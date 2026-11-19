package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationFactVO {
    private String code;
    private Integer definitionVersion;
    private String name;
    private String valueType;
    private String unit;
    private String valueCode;
    private List<String> valueCodes;
    private String valueDisplay;
    private BigDecimal valueNumber;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private String valueStatus;
}
