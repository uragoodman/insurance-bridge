package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs expiry bookkeeping and optional AgentOS session deletion. */
@Component
@ConditionalOnExpression("'${ai.persistence.enabled:false}' == 'true' && " +
        "'${ai.deletion-ledger.replay-on-startup:false}' != 'true'")
public class AiCleanupScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(AiCleanupScheduler.class);
    private final AiConversationLifecycleService lifecycleService;
    private final AiCleanupTaskService taskService;
    private final ObjectProvider<AiAgentDataCleaner> cleanerProvider;

    public AiCleanupScheduler(AiConversationLifecycleService lifecycleService, AiCleanupTaskService taskService,
                              ObjectProvider<AiAgentDataCleaner> cleanerProvider) {
        this.lifecycleService = lifecycleService;
        this.taskService = taskService;
        this.cleanerProvider = cleanerProvider;
    }

    @Scheduled(fixedDelayString = "${ai.persistence.cleanup-interval-ms:5000}")
    public void runCleanupCycle() {
        taskService.failAbandonedTurnsForPendingDeletes();
        lifecycleService.enqueueExpired(100);
        AiAgentDataCleaner cleaner = cleanerProvider.getIfAvailable();
        if (cleaner == null) return; // Leave tasks durable until the private HTTP contract exists.
        taskService.purgeExpiredCompleted(100);
        for (int i = 0; i < 20; i++) {
            AiCleanupTaskDO task = taskService.claimNext();
            if (task == null) return;
            try {
                cleaner.deleteSessionAndTraces(task.getAgnoSessionId());
                if (!taskService.complete(task.getId())) {
                    LOGGER.warn("AI cleanup task {} was remotely deleted but local completion was not recorded", task.getId());
                }
            } catch (Exception exception) {
                LOGGER.warn("AI AgentOS cleanup failed for task {}", task.getId());
                if (taskService.failed(task.getId())) {
                    LOGGER.error("AI AgentOS cleanup task {} entered DEAD_LETTER and requires operator action",
                            task.getId());
                }
            }
        }
    }

    @Scheduled(cron = "${ai.persistence.trace-cleanup-cron:0 17 3 * * *}")
    public void purgeExpiredTraces() {
        AiAgentDataCleaner cleaner = cleanerProvider.getIfAvailable();
        if (cleaner == null) return;
        try {
            cleaner.deleteExpiredSessions(taskService.getConversationRetentionDays());
            cleaner.deleteExpiredTraces(taskService.getTraceRetentionDays());
        } catch (Exception exception) {
            LOGGER.warn("AI trace retention cleanup failed");
        }
    }
}
