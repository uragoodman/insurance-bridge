package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Read-only result for the current authenticated customer's active policies. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicySummaryResponseVO {

    private String status;
    private String asOf;
    private List<AiOwnPolicySummaryItemVO> policies;
    private int returnedCount;
    private boolean truncated;
}
