package com.itheima.bolee.insurance.pojo.ai;

import lombok.Data;

@Data
public class AiRecommendationProductRow {
    private Long productId;
    private Long profileVersionId;
    private Long profileCategoryId;
    private Long actualCategoryId;
    private Integer productVersion;
    private String productName;
    private Integer sortNo;
    private String profileStatus;
    private String profileDataState;
    private java.time.LocalDateTime profileEffectiveFrom;
    private java.time.LocalDateTime profileEffectiveTo;
}
