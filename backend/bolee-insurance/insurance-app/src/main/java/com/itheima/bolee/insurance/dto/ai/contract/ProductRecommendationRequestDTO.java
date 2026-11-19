package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Typed Java tool request. Criteria are allowlisted; no user ID or free-form query expression. */
@Data
@NoArgsConstructor
public class ProductRecommendationRequestDTO {
    private String requestId;
    private String turnId;
    private ProductCriteriaDTO criteria;
    private Integer limit;
}
