package com.itheima.bolee.insurance.dto.ai;

import lombok.Data;

import java.time.LocalDateTime;

/** Narrow projection of a customer's own active policy for the Agent API. */
@Data
public class AiOwnPolicyRecordDTO {

    private String policyId;
    private String productName;
    private String policyStatusCode;
    private LocalDateTime coverageStartTime;
    private LocalDateTime coverageEndTime;
    private String autoExtensionFlag;
    private LocalDateTime createTime;
}
