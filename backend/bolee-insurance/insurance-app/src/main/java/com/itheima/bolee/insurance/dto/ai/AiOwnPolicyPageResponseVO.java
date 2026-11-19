package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** A bounded, owner-scoped page of safe policy fields for the signed-in customer. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicyPageResponseVO {
    private String status;
    private String asOf;
    private List<AiOwnPolicySummaryItemVO> policies;
    private String nextCursor;
    private boolean hasMore;
}
