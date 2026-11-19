package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Customer-facing single policy result using the Agent policy whitelist. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicyDetailResponseVO {

    private String status;
    private String asOf;
    private AiOwnPolicySummaryItemVO policy;
}
