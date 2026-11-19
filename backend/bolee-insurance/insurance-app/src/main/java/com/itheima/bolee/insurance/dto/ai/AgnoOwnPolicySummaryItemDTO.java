package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Strict wire DTO for one private Agent policy summary. */
@Data
@NoArgsConstructor
public class AgnoOwnPolicySummaryItemDTO {

    @JsonProperty("policy_id")
    private String policyId;

    @JsonProperty("product_name")
    private String productName;

    @JsonProperty("policy_status")
    private String policyStatus;

    @JsonProperty("policy_status_label")
    private String policyStatusLabel;

    @JsonProperty("coverage_start_time")
    private String coverageStartTime;

    @JsonProperty("coverage_end_time")
    private String coverageEndTime;

    @JsonProperty("auto_extension_enabled")
    private Boolean autoExtensionEnabled;
}
