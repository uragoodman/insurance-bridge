package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Internal unresolved catalog criterion received from Python. */
@Data
@NoArgsConstructor
public class AgnoRecommendationUnresolvedCriterionDTO {
    private String code;

    @JsonProperty("affected_product_count")
    private Integer affectedProductCount;

    private String reason;
}
