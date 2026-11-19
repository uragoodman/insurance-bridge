package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Structured product fact returned by Java; absent facts stay absent, never model-generated. */
@Data
@NoArgsConstructor
public class ProductCandidateDTO {
    private String productId;
    private String name;
    private String categoryId;
    private List<String> matchReasons = new ArrayList<String>();
    private List<String> coverageHighlights = new ArrayList<String>();
    private List<String> importantLimits = new ArrayList<String>();
    private String detailUrl;
    private String productVersion;
    private ProductQuoteDTO quote;
}
