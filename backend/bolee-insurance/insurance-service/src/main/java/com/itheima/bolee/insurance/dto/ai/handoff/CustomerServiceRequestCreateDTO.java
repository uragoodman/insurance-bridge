package com.itheima.bolee.insurance.dto.ai.handoff;

import lombok.Data;


/** Customer input deliberately excludes owner, tenant, status, contact data and free text. */
@Data
public class CustomerServiceRequestCreateDTO {
    private String clientRequestId;

    private String requestType;

    private String reasonCode;

    private String preferredContactMethod;

    private Long channelId;
}
