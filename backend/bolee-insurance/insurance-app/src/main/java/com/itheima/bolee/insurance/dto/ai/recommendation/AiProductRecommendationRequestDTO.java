package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class AiProductRecommendationRequestDTO {
    @NotBlank
    private String requestId;
    private String turnId;
    @Valid
    @NotNull
    private Criteria criteria;
    @Min(1)
    @Max(3)
    private Integer limit = 3;

    @Data
    public static class Criteria {
        @NotBlank
        private String categoryId;
        @Valid
        @NotNull
        private java.util.List<Item> items;
    }

    @Data
    public static class Item {
        @NotBlank
        private String code;
        @NotBlank
        private String operator;
        @NotBlank
        private String customerIntent;
        private String valueCode;
        private java.util.List<String> valueCodes;
        private java.math.BigDecimal numberValue;
        private java.math.BigDecimal minValue;
        private java.math.BigDecimal maxValue;
        private String unit;
    }
}
