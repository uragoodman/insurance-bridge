package com.itheima.bolee.insurance.pojo.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.itheima.bolee.framework.mybatisplus.basic.BasePojo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Idempotency and lifecycle metadata for one AI chat turn; contains no prompt or answer text. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("tab_ai_turn")
public class AiTurnDO extends BasePojo {

    private Long ownerUserId;
    private Long conversationId;
    private String turnId;
    private String clientMessageId;
    private String requestHash;
    private String agnoRunId;
    private String encryptedResponse;
    private String traceId;
    private AiTurnStatus status;
    private String failureCode;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
}
