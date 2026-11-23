package com.itheima.bolee.insurance.dto.ai;

import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationFactVO;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/** Minimal catalog facts; this DTO intentionally excludes prices and policy terms. */
@Data
@AllArgsConstructor
public class AiProductCandidateVO {
    private String productId;
    private String name;
    private String categoryId;
    private String categoryCode;
    private String categoryName;
    private List<String> matchReasons;
    /** EXACT_NAME, APPROVED_ALIAS, or RELATED. */
    private String matchType;
    private Integer productVersion;
    private String verificationStatus;
    private List<AiRecommendationFactVO> facts;
    private String summary;
}
