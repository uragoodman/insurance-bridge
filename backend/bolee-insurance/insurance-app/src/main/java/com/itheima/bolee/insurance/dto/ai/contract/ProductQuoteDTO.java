package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** A Java-calculated quote, never a product base price or an LLM estimate. */
@Data
@NoArgsConstructor
public class ProductQuoteDTO {
    private BigDecimal amount;
    private String currencyCode;
    private String paymentFrequency;
    private String quoteId;
    private Instant asOf;
}
