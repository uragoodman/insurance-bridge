package com.itheima.bolee.insurance.dto.ai.admin;

import lombok.Data;

@Data
public class AiRecommendationAdminProductVO {
    private Long productId;
    private String productName;
    private String publishNumber;
    private Long categoryId;
    private String categoryName;
    private Integer sortNo;
    private Boolean aiRecommendEnabled;
    private String insuranceState;
}
