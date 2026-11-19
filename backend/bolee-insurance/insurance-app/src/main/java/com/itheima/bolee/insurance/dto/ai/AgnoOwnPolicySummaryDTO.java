package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Strict wire DTO for the private Agent own-policy response. */
@Data
@NoArgsConstructor
public class AgnoOwnPolicySummaryDTO {

    private String status;

    @JsonProperty("as_of")
    private String asOf;

    private List<AgnoOwnPolicySummaryItemDTO> policies = new ArrayList<AgnoOwnPolicySummaryItemDTO>();

    @JsonProperty("returned_count")
    private Integer returnedCount;

    private Boolean truncated;
}
