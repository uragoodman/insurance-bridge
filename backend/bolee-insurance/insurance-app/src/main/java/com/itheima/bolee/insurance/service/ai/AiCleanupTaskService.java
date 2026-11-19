package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.config.ai.properties.AiRetentionProperties;
import com.itheima.bolee.insurance.mapper.ai.AiCleanupTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.AiConversationMapper;
import com.itheima.bolee.insurance.mapper.ai.AiTurnMapper;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Database-side lifecycle of the durable Java-to-AgentOS cleanup outbox. */
@Service
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
public class AiCleanupTaskService {
    private static final int MAX_ATTEMPTS = 10;
    private static final int CLAIM_LEASE_MINUTES = 3;
    private static final int ACTIVE_TURN_CLEANUP_TIMEOUT_MINUTES = 3;
    private final AiCleanupTaskMapper taskMapper;
    private final AiTurnMapper turnMapper;
    private final AiConversationMapper conversationMapper;
    private final AiRetentionProperties retention;

    public AiCleanupTaskService(AiCleanupTaskMapper taskMapper, AiTurnMapper turnMapper,
                                AiConversationMapper conversationMapper, AiRetentionProperties retention) {
        this.taskMapper = taskMapper;
        this.turnMapper = turnMapper;
        this.conversationMapper = conversationMapper;
        this.retention = retention;
    }

    /** Claims a due row with a database lock and a lease for multi-instance recovery. */
    @Transactional
    public AiCleanupTaskDO claimNext() {
        LocalDateTime now = AiPersistenceTime.now();
        AiCleanupTaskDO task = taskMapper.findNextClaimableForUpdate(now);
        if (task == null) return null;
        return taskMapper.claim(task.getId(), now, now.plusMinutes(CLAIM_LEASE_MINUTES)) == 1 ? task : null;
    }

    /** Deletes local metadata only after the remote AgentOS delete has succeeded. */
    @Transactional
    public boolean complete(Long taskId) {
        LocalDateTime now = AiPersistenceTime.now();
        AiCleanupTaskDO task = taskMapper.findById(taskId);
        if (task == null || task.getStatus() == null || !"PROCESSING".equals(task.getStatus().getValue())) return false;
        turnMapper.deleteForConversation(task.getConversationId(), task.getOwnerUserId());
        conversationMapper.deletePendingOwned(task.getConversationId(), task.getOwnerUserId());
        return taskMapper.markCompleted(taskId, now, retention.cleanupTaskExpiresAt(now)) == 1;
    }

    @Transactional
    public boolean failed(Long taskId) {
        AiCleanupTaskDO task = taskMapper.findById(taskId);
        if (task == null || task.getStatus() == null || !"PROCESSING".equals(task.getStatus().getValue())) return false;
        LocalDateTime now = AiPersistenceTime.now();
        int attempts = task.getAttemptCount() == null ? MAX_ATTEMPTS : task.getAttemptCount();
        boolean exhausted = attempts >= MAX_ATTEMPTS;
        int delayMinutes = Math.min(360, 1 << Math.min(Math.max(attempts, 0), 8));
        taskMapper.scheduleRetry(taskId, exhausted ? "DEAD_LETTER" : "RETRY_WAIT",
                exhausted ? null : now.plusMinutes(delayMinutes), "AGENTOS_DELETE_FAILED", now);
        return exhausted;
    }

    @Transactional
    public int purgeExpiredCompleted(int limit) {
        LocalDateTime now = AiPersistenceTime.now();
        int deleted = 0;
        for (AiCleanupTaskDO task : taskMapper.findExpiredCompleted(now, limit)) {
            deleted += taskMapper.deleteExpiredCompleted(task.getId(), now);
        }
        return deleted;
    }

    public int getTraceRetentionDays() {
        return retention.getTraceDays();
    }

    public int getConversationRetentionDays() {
        return retention.getConversationDays();
    }

    @Transactional
    public int failAbandonedTurnsForPendingDeletes() {
        LocalDateTime now = AiPersistenceTime.now();
        return turnMapper.failAbandonedProcessingForPendingDeletes(
                now.minusMinutes(ACTIVE_TURN_CLEANUP_TIMEOUT_MINUTES), now);
    }
}
