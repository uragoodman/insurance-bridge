package com.itheima.bolee.insurance.pojo.ai.terms;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class InsuranceTermIndexTaskDO {
    private Long id;
    private Long sourceId;
    private String action;
    private String sourceSha256;
    private String taskStatus;
    private Integer attempts;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime leaseUntil;
    private String leaseToken;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
