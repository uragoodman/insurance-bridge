package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRecommendationCandidateVO {
    private String productId;
    private String name;
    private String categoryId;
    private Integer productVersion;
    private String verificationStatus;
    private List<AiRecommendationFactVO> facts;
    private List<String> matchReasons;
}
