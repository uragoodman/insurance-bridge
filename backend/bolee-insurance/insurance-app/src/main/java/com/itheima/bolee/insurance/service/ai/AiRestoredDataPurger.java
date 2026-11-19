package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.mapper.ai.AiCleanupTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.AiConversationMapper;
import com.itheima.bolee.insurance.mapper.ai.AiTurnMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes restored local rows for a tombstoned AgentOS session. */
@Service
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
public class AiRestoredDataPurger {
    private final AiTurnMapper turnMapper;
    private final AiCleanupTaskMapper cleanupTaskMapper;
    private final AiConversationMapper conversationMapper;

    public AiRestoredDataPurger(AiTurnMapper turnMapper, AiCleanupTaskMapper cleanupTaskMapper,
                                AiConversationMapper conversationMapper) {
        this.turnMapper = turnMapper;
        this.cleanupTaskMapper = cleanupTaskMapper;
        this.conversationMapper = conversationMapper;
    }

    @Transactional
    public void purge(String agnoSessionId) {
        turnMapper.deleteByAgnoSessionId(agnoSessionId);
        cleanupTaskMapper.deleteByAgnoSessionId(agnoSessionId);
        conversationMapper.deleteByAgnoSessionId(agnoSessionId);
    }
}
