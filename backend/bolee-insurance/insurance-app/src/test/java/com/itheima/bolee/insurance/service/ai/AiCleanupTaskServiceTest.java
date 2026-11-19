package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.config.ai.properties.AiRetentionProperties;
import com.itheima.bolee.insurance.mapper.ai.AiCleanupTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.AiConversationMapper;
import com.itheima.bolee.insurance.mapper.ai.AiTurnMapper;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskStatus;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class AiCleanupTaskServiceTest {
    private AiCleanupTaskMapper taskMapper;
    private AiTurnMapper turnMapper;
    private AiConversationMapper conversationMapper;
    private AiCleanupTaskService service;

    @Before
    public void setUp() {
        taskMapper = mock(AiCleanupTaskMapper.class);
        turnMapper = mock(AiTurnMapper.class);
        conversationMapper = mock(AiConversationMapper.class);
        AiRetentionProperties retention = new AiRetentionProperties();
        retention.setConversationDays(90);
        retention.setTraceDays(30);
        retention.setCleanupTaskDays(30);
        service = new AiCleanupTaskService(taskMapper, turnMapper, conversationMapper, retention);
    }

    @Test
    public void claimsDueTaskWithLease() {
        AiCleanupTaskDO task = new AiCleanupTaskDO();
        task.setId(55L);
        when(taskMapper.findNextClaimableForUpdate(any(LocalDateTime.class))).thenReturn(task);
        when(taskMapper.claim(eq(55L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);

        assertSame(task, service.claimNext());
        verify(taskMapper).claim(eq(55L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void successfulRemoteDeletePurgesTurnsAndConversationThenCompletesTask() {
        AiCleanupTaskDO task = processingTask();
        when(taskMapper.findById(55L)).thenReturn(task);
        when(taskMapper.markCompleted(eq(55L), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.complete(55L));

        verify(turnMapper).deleteForConversation(77L, 7L);
        verify(conversationMapper).deletePendingOwned(77L, 7L);
        verify(taskMapper).markCompleted(eq(55L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void retryUsesSafeErrorCodeAndDeadLettersAtAttemptLimit() {
        AiCleanupTaskDO task = processingTask();
        task.setAttemptCount(10);
        when(taskMapper.findById(55L)).thenReturn(task);

        assertTrue(service.failed(55L));

        verify(taskMapper).scheduleRetry(eq(55L), eq("DEAD_LETTER"), isNull(), eq("AGENTOS_DELETE_FAILED"),
                any(LocalDateTime.class));
    }

    @Test
    public void failsStaleInFlightTurnsBeforeDeletingTheirSession() {
        when(turnMapper.failAbandonedProcessingForPendingDeletes(any(LocalDateTime.class),
                any(LocalDateTime.class))).thenReturn(1);

        assertEquals(1, service.failAbandonedTurnsForPendingDeletes());

        verify(turnMapper).failAbandonedProcessingForPendingDeletes(any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    private AiCleanupTaskDO processingTask() {
        AiCleanupTaskDO task = new AiCleanupTaskDO();
        task.setId(55L);
        task.setOwnerUserId(7L);
        task.setConversationId(77L);
        task.setAgnoSessionId("agno-session-1");
        task.setAttemptCount(1);
        task.setStatus(AiCleanupTaskStatus.PROCESSING);
        return task;
    }
}
