package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Stable business result contract for Java-owned candidate selection. */
@Data
@NoArgsConstructor
public class ProductRecommendationResponseDTO {
    private ProductRecommendationStatus status;
    private List<String> missingCriteria = new ArrayList<String>();
    private List<ProductCandidateDTO> candidates = new ArrayList<ProductCandidateDTO>();
    private String catalogVersion;
    private String ruleVersion;
}
