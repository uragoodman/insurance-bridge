package com.itheima.bolee.insurance.pojo.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.itheima.bolee.framework.mybatisplus.basic.BasePojo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Durable retry record for deleting one Agno session and its associated traces. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("tab_ai_cleanup_task")
public class AiCleanupTaskDO extends BasePojo {

    private Long ownerUserId;
    private Long conversationId;
    private String agnoSessionId;
    private AiCleanupTaskStatus status;
    private Integer attemptCount;
    private LocalDateTime nextAttemptAt;
    private LocalDateTime lastAttemptAt;
    private LocalDateTime leaseUntil;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private String lastErrorCode;
}
