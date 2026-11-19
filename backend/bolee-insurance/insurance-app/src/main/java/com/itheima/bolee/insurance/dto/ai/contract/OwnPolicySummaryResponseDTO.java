package com.itheima.bolee.insurance.dto.ai.contract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Owner-scoped policy data projection returned by Java. */
@Data
@NoArgsConstructor
public class OwnPolicySummaryResponseDTO {
    private String status;
    private Instant asOf;
    private List<PolicySummaryDTO> policies = new ArrayList<PolicySummaryDTO>();
}
