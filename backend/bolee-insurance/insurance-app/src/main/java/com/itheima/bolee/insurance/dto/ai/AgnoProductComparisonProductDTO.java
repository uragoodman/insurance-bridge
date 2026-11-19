package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** One product and its Java-approved facts in a comparison response. */
@Data
@NoArgsConstructor
public class AgnoProductComparisonProductDTO {
    @JsonProperty("product_id")
    private String productId;

    private String name;

    @JsonProperty("product_version")
    private Integer productVersion;

    private List<AgnoRecommendationFactDTO> facts = new ArrayList<AgnoRecommendationFactDTO>();
}
