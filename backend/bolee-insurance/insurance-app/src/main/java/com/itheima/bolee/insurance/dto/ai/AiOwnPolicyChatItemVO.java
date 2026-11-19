package com.itheima.bolee.insurance.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Customer-facing policy fields; deliberately excludes the internal policy reference. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiOwnPolicyChatItemVO {

    private String productName;
    private String policyStatus;
    private String policyStatusLabel;
    private String coverageStartTime;
    private String coverageEndTime;
    private Boolean autoExtensionEnabled;
}
