package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Minimal policy projection; exact money/date mappings must be validated against warranty semantics. */
@Data
@NoArgsConstructor
public class PolicySummaryDTO {
    private String policyId;
    private String productId;
    private String productName;
    private String policyStatus;
    private BigDecimal premiumAmount;
    private String paymentFrequency;
    private LocalDate coverageStartDate;
    private LocalDate coverageEndDate;
    private LocalDate effectiveAt;
}
