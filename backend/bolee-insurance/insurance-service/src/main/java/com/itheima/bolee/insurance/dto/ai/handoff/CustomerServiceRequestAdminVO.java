package com.itheima.bolee.insurance.dto.ai.handoff;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CustomerServiceRequestAdminVO {
    private Long id;
    private String requestNo;
    private Long ownerUserId;
    private String requestType;
    private String reasonCode;
    private String preferredContactMethod;
    private Long channelId;
    private String status;
    private Long assignedOperatorId;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime expiresAt;
}
