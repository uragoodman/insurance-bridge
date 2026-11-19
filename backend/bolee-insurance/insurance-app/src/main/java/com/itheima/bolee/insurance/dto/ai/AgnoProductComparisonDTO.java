package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Internal product comparison payload received from the Python Agent service. */
@Data
@NoArgsConstructor
public class AgnoProductComparisonDTO {
    @JsonProperty("request_id")
    private String requestId;

    private String status;

    @JsonProperty("category_id")
    private String categoryId;

    @JsonProperty("catalog_version")
    private String catalogVersion;

    @JsonProperty("rule_version")
    private Integer ruleVersion;

    private List<AgnoProductComparisonProductDTO> products = new ArrayList<AgnoProductComparisonProductDTO>();

    @JsonProperty("unresolved_criteria")
    private List<AgnoRecommendationUnresolvedCriterionDTO> unresolvedCriteria =
            new ArrayList<AgnoRecommendationUnresolvedCriterionDTO>();

    @JsonProperty("compared_at")
    private Instant comparedAt;
}
