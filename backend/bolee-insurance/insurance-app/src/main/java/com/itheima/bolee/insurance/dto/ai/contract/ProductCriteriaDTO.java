package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

/** V1 accepts only a Java-published category identifier; future criteria require explicit allowlists. */
@Data
@NoArgsConstructor
public class ProductCriteriaDTO {
    private String categoryId;
}
