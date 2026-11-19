package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Strict wire DTO for a single Agent policy detail result. */
@Data
@NoArgsConstructor
public class AgnoOwnPolicyDetailDTO {

    private String status;

    @JsonProperty("as_of")
    private String asOf;

    private AgnoOwnPolicySummaryItemDTO policy;
}
