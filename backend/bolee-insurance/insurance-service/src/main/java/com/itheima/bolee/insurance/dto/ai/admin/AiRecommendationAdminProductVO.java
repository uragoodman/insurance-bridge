package com.itheima.bolee.insurance.dto.ai.admin;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

@Data
public class AiRecommendationAdminProductVO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long productId;
    private String productName;
    private String publishNumber;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long categoryId;
    private String categoryName;
    private Integer sortNo;
    private Boolean aiRecommendEnabled;
    private String insuranceState;
}
