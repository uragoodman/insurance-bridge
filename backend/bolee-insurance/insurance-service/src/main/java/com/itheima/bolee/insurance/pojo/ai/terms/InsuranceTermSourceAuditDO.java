package com.itheima.bolee.insurance.pojo.ai.terms;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InsuranceTermSourceAuditDO {
    private Long id;
    private Long sourceId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String note;
    private LocalDateTime createTime;
}
