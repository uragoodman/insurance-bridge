package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

/** Allowlisted live product category exposed to the Agent. */
@Data
@AllArgsConstructor
public class AiProductCategoryVO {
    private String categoryId;
    private String categoryCode;
    private String categoryName;
}
