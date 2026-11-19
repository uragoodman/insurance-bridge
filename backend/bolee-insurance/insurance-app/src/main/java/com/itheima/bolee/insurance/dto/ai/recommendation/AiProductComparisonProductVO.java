package com.itheima.bolee.insurance.dto.ai.recommendation;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiProductComparisonProductVO {
    private String productId;
    private String name;
    private Integer productVersion;
    private List<AiRecommendationFactVO> facts;
}
