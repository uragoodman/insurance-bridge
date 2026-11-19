package com.itheima.bolee.insurance.dto.ai.handoff;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerServiceRequestVO {
    private Long id;
    private String requestNo;
    private String requestType;
    private String reasonCode;
    private String preferredContactMethod;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime cancelledAt;
}
