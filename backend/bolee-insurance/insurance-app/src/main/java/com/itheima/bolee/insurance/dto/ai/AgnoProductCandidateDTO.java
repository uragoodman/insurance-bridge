package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Product facts returned by Python from the validated Java catalog tool result. */
@Data
@NoArgsConstructor
public class AgnoProductCandidateDTO {

    @JsonProperty("product_id")
    private String productId;

    private String name;

    @JsonProperty("category_id")
    private String categoryId;

    @JsonProperty("category_code")
    private String categoryCode;

    @JsonProperty("category_name")
    private String categoryName;

    @JsonProperty("match_reasons")
    private List<String> matchReasons = new ArrayList<String>();

    @JsonProperty("product_version")
    private Integer productVersion;

    @JsonProperty("verification_status")
    private String verificationStatus;

    private String summary;

    private List<AgnoRecommendationFactDTO> facts = new ArrayList<AgnoRecommendationFactDTO>();
}
