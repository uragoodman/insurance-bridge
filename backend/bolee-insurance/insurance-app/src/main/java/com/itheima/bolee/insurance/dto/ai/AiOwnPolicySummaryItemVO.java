package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Private Java-to-Agent tool result; policyId is never returned to the customer API. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicySummaryItemVO {

    private String policyId;
    private String productName;
    private String policyStatus;
    private String policyStatusLabel;
    private String coverageStartTime;
    private String coverageEndTime;
    private Boolean autoExtensionEnabled;
}
