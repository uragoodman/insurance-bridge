package com.itheima.bolee.insurance.pojo.ai;

import com.itheima.bolee.framework.mybatisplus.basic.BasePojo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CustomerServiceRequestDO extends BasePojo {
    private String requestNo;
    private String companyNo;
    private Long ownerUserId;
    private String clientRequestId;
    private String requestHash;
    private String sourceType;
    private Long sourceConversationId;
    private String sourceTurnId;
    private Long channelId;
    private String requestType;
    private String reasonCode;
    private String preferredContactMethod;
    private String status;
    private Long assignedOperatorId;
    private LocalDateTime submittedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiresAt;
}
