package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Reference to an approved and currently effective source document. */
@Data
@NoArgsConstructor
public class ProductSourceDTO {
    private String sourceId;
    private String productId;
    private String productVersion;
    private String documentId;
    private String title;
    private String section;
    private Integer page;
    private String url;
    private String contentHash;
}
