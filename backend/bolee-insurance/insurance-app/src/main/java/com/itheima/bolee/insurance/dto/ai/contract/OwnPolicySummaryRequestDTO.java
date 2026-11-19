package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

/** Owner-scoped policy request. Identity is derived from the Java grant, never this DTO. */
@Data
@NoArgsConstructor
public class OwnPolicySummaryRequestDTO {
    private String requestId;
    private String turnId;
    private String policyId;
}
