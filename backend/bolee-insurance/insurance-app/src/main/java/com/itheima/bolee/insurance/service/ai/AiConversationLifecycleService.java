package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.mapper.ai.AiCleanupTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.AiConversationMapper;
import com.itheima.bolee.insurance.pojo.ai.AiConversationDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Atomically blocks expired conversations and records their remote cleanup requests. */
@Service
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
public class AiConversationLifecycleService {
    private final AiConversationMapper conversationMapper;
    private final AiCleanupTaskMapper cleanupTaskMapper;

    public AiConversationLifecycleService(AiConversationMapper conversationMapper,
                                          AiCleanupTaskMapper cleanupTaskMapper) {
        this.conversationMapper = conversationMapper;
        this.cleanupTaskMapper = cleanupTaskMapper;
    }

    @Transactional
    public int enqueueExpired(int limit) {
        LocalDateTime now = AiPersistenceTime.now();
        List<AiConversationDO> expired = conversationMapper.findExpiredForUpdate(now, limit);
        int enqueued = 0;
        for (AiConversationDO conversation : expired) {
            if (conversationMapper.markDeletePending(conversation.getId(), conversation.getOwnerUserId(), now) == 1) {
                AiConversationService.enqueueCleanup(conversation, now, cleanupTaskMapper);
                enqueued++;
            }
        }
        return enqueued;
    }
}
