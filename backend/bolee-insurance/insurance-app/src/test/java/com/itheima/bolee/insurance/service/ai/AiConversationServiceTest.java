package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.config.ai.properties.AiRetentionProperties;
import com.itheima.bolee.insurance.mapper.ai.AiCleanupTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.AiConversationMapper;
import com.itheima.bolee.insurance.mapper.ai.AiTurnMapper;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import com.itheima.bolee.insurance.pojo.ai.AiConversationDO;
import com.itheima.bolee.insurance.pojo.ai.AiConversationStatus;
import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AiConversationServiceTest {
    private AiConversationMapper conversationMapper;
    private AiTurnMapper turnMapper;
    private AiCleanupTaskMapper cleanupTaskMapper;
    private AiConversationService service;
    private AiConversationDO conversation;
    private Path ledgerPath;

    @Before
    public void setUp() {
        conversationMapper = mock(AiConversationMapper.class);
        turnMapper = mock(AiTurnMapper.class);
        cleanupTaskMapper = mock(AiCleanupTaskMapper.class);
        AiRetentionProperties retention = new AiRetentionProperties();
        retention.setConversationDays(90);
        retention.setTraceDays(30);
        retention.setCleanupTaskDays(30);
        try {
            ledgerPath = Files.createTempFile("ai-deletion-ledger-test", ".log");
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
        service = new AiConversationService(conversationMapper, turnMapper, cleanupTaskMapper, retention,
                new AiDeletionLedger(ledgerPath.toString()));
        conversation = new AiConversationDO();
        conversation.setId(101L);
        conversation.setOwnerUserId(7L);
        conversation.setSessionId("session-1");
        conversation.setAgnoSessionId("8c1f27f4-02ee-4dd3-8f8c-5314fab0da55");
        conversation.setStatus(AiConversationStatus.ACTIVE);
        conversation.setExpiresAt(LocalDateTime.now().plusDays(3));
    }

    @Test
    public void startTurnPersistsOnlyFingerprintAndUpdatesActivity() {
        when(conversationMapper.findOwned(7L, "session-1")).thenReturn(conversation);
        when(conversationMapper.findOwnedByIdForUpdate(7L, 101L)).thenReturn(conversation);
        when(turnMapper.findByIdempotencyKey(7L, 101L, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(null);
        when(turnMapper.insertIfAbsent(any(AiTurnDO.class))).thenReturn(1);

        AiTurnStartResult result = service.startTurn(7L, "session-1",
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", "hello there");

        assertEquals(AiTurnStartResult.Outcome.CREATED, result.getOutcome());
        assertEquals(AiTurnStatus.PROCESSING, result.getTurn().getStatus());
        assertEquals(AiRequestFingerprint.sha256ChatMessage("hello there"), result.getTurn().getRequestHash());
        assertNull(result.getTurn().getAgnoRunId());
        verify(turnMapper).insertIfAbsent(any(AiTurnDO.class));
        verify(conversationMapper).updateActivity(eq(101L), eq(7L), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void duplicateMessageIdReturnsOriginalTurnWithoutAnotherInsert() {
        String message = "same payload";
        AiTurnDO existing = new AiTurnDO();
        existing.setRequestHash(AiRequestFingerprint.sha256ChatMessage(message));
        existing.setStatus(AiTurnStatus.COMPLETED);
        when(conversationMapper.findOwned(7L, "session-1")).thenReturn(conversation);
        when(conversationMapper.findOwnedByIdForUpdate(7L, 101L)).thenReturn(conversation);
        when(turnMapper.findByIdempotencyKey(7L, 101L, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(existing);

        AiTurnStartResult result = service.startTurn(7L, "session-1",
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", message);

        assertEquals(AiTurnStartResult.Outcome.EXISTING, result.getOutcome());
        assertSame(existing, result.getTurn());
        verify(turnMapper, never()).insertIfAbsent(any(AiTurnDO.class));
        verify(conversationMapper, never()).updateActivity(anyLong(), anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    public void duplicateMessageIdWithDifferentPayloadConflicts() {
        AiTurnDO existing = new AiTurnDO();
        existing.setRequestHash(AiRequestFingerprint.sha256ChatMessage("old payload"));
        existing.setStatus(AiTurnStatus.PROCESSING);
        when(conversationMapper.findOwned(7L, "session-1")).thenReturn(conversation);
        when(conversationMapper.findOwnedByIdForUpdate(7L, 101L)).thenReturn(conversation);
        when(turnMapper.findByIdempotencyKey(7L, 101L, "11111111-1111-4111-8111-111111111111"))
                .thenReturn(existing);

        AiTurnStartResult result = service.startTurn(7L, "session-1",
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", "new payload");

        assertEquals(AiTurnStartResult.Outcome.IDEMPOTENCY_CONFLICT, result.getOutcome());
        verify(turnMapper, never()).insertIfAbsent(any(AiTurnDO.class));
    }

    @Test
    public void expiredConversationCannotBeReactivatedByNewTurn() {
        conversation.setExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(conversationMapper.findOwned(7L, "session-1")).thenReturn(conversation);
        when(conversationMapper.findOwnedByIdForUpdate(7L, 101L)).thenReturn(conversation);

        AiTurnStartResult result = service.startTurn(7L, "session-1",
                "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222", "hello");

        assertEquals(AiTurnStartResult.Outcome.CONVERSATION_UNAVAILABLE, result.getOutcome());
        verify(turnMapper, never()).insertIfAbsent(any(AiTurnDO.class));
    }

    @Test
    public void deleteMarksOwnerScopedConversationAndEnqueuesTask() {
        when(conversationMapper.findOwnedForUpdate(7L, "session-1")).thenReturn(conversation);
        when(conversationMapper.markDeletePending(eq(101L), eq(7L), any(LocalDateTime.class))).thenReturn(1);

        assertTrue(service.requestDelete(7L, "session-1"));

        assertTrue(new AiDeletionLedger(ledgerPath.toString()).readAll().contains(conversation.getAgnoSessionId()));

        ArgumentCaptor<AiCleanupTaskDO> task = ArgumentCaptor.forClass(AiCleanupTaskDO.class);
        verify(cleanupTaskMapper).insertIfAbsent(task.capture());
        assertEquals(Long.valueOf(7L), task.getValue().getOwnerUserId());
        assertEquals(Long.valueOf(101L), task.getValue().getConversationId());
        assertEquals("8c1f27f4-02ee-4dd3-8f8c-5314fab0da55", task.getValue().getAgnoSessionId());
    }

    @Test
    public void repeatedDeleteRepairsMissingOutboxTask() {
        conversation.setStatus(AiConversationStatus.DELETE_PENDING);
        when(conversationMapper.findOwnedForUpdate(7L, "session-1")).thenReturn(conversation);

        assertTrue(service.requestDelete(7L, "session-1"));

        verify(conversationMapper, never()).markDeletePending(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(cleanupTaskMapper).insertIfAbsent(any(AiCleanupTaskDO.class));
    }

    @Test
    public void malformedDeletionTombstonePreventsDatabaseDeleteTransition() {
        conversation.setAgnoSessionId("not-a-uuid");
        when(conversationMapper.findOwnedForUpdate(7L, "session-1")).thenReturn(conversation);

        try {
            service.requestDelete(7L, "session-1");
            fail("invalid tombstone must prevent acknowledging deletion");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("session UUID"));
        }

        verify(conversationMapper, never()).markDeletePending(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(cleanupTaskMapper, never()).insertIfAbsent(any(AiCleanupTaskDO.class));
    }
}
