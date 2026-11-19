package com.itheima.bolee.insurance.pojo.ai;

import com.baomidou.mybatisplus.annotation.TableName;
import com.itheima.bolee.framework.mybatisplus.basic.BasePojo;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Java-owned conversation metadata. Message content remains in Agno PostgreSQL. */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("tab_ai_conversation")
public class AiConversationDO extends BasePojo {

    private Long ownerUserId;
    private String sessionId;
    private String agnoSessionId;
    private AiConversationStatus status;
    private LocalDateTime lastMessageAt;
    private LocalDateTime expiresAt;
    private LocalDateTime deletedAt;
}
