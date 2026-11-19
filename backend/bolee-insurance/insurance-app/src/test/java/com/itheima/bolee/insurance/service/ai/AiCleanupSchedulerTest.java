package com.itheima.bolee.insurance.service.ai;

import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.Mockito.*;

public class AiCleanupSchedulerTest {
    @Test
    @SuppressWarnings("unchecked")
    public void requestsTraceCleanupUsingConfiguredRetentionWindow() throws Exception {
        AiConversationLifecycleService lifecycle = mock(AiConversationLifecycleService.class);
        AiCleanupTaskService taskService = mock(AiCleanupTaskService.class);
        AiAgentDataCleaner cleaner = mock(AiAgentDataCleaner.class);
        ObjectProvider<AiAgentDataCleaner> cleanerProvider = mock(ObjectProvider.class);
        when(cleanerProvider.getIfAvailable()).thenReturn(cleaner);
        when(taskService.getTraceRetentionDays()).thenReturn(30);
        when(taskService.getConversationRetentionDays()).thenReturn(90);

        AiCleanupScheduler scheduler = new AiCleanupScheduler(lifecycle, taskService, cleanerProvider);
        scheduler.purgeExpiredTraces();

        verify(cleaner).deleteExpiredTraces(30);
        verify(cleaner).deleteExpiredSessions(90);
        verifyNoInteractions(lifecycle);
    }
}
