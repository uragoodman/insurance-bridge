package com.itheima.bolee.insurance.service.ai;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.itheima.bolee.insurance.config.ai.properties.AiRetentionProperties;
import com.itheima.bolee.insurance.mapper.ai.AiCleanupTaskMapper;
import com.itheima.bolee.insurance.mapper.ai.AiConversationMapper;
import com.itheima.bolee.insurance.mapper.ai.AiTurnMapper;
import com.itheima.bolee.insurance.pojo.ai.AiCleanupTaskDO;
import com.itheima.bolee.insurance.pojo.ai.AiConversationDO;
import com.itheima.bolee.insurance.pojo.ai.AiConversationStatus;
import com.itheima.bolee.insurance.pojo.ai.AiTurnDO;
import com.itheima.bolee.insurance.pojo.ai.AiTurnStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Transactional owner-scoped write operations for AI conversation metadata. */
@Service
@ConditionalOnProperty(prefix = "ai.persistence", name = "enabled", havingValue = "true")
public class AiConversationService {
    private static final int MAX_MESSAGE_LENGTH = 4000;
    private final AiConversationMapper conversationMapper;
    private final AiTurnMapper turnMapper;
    private final AiCleanupTaskMapper cleanupTaskMapper;
    private final AiRetentionProperties retention;
    private final AiDeletionLedger deletionLedger;

    public AiConversationService(AiConversationMapper conversationMapper, AiTurnMapper turnMapper,
                                 AiCleanupTaskMapper cleanupTaskMapper, AiRetentionProperties retention,
                                 AiDeletionLedger deletionLedger) {
        this.conversationMapper = conversationMapper;
        this.turnMapper = turnMapper;
        this.cleanupTaskMapper = cleanupTaskMapper;
        this.retention = retention;
        this.deletionLedger = deletionLedger;
    }

    /** Creates a conversation if needed; sessionId is opaque and unique per authenticated owner. */
    @Transactional
    public AiConversationDO getOrCreateConversation(Long ownerUserId, String sessionId) {
        requireOwner(ownerUserId);
        requireText(sessionId, "sessionId", 64);
        AiConversationDO conversation = conversationMapper.findOwned(ownerUserId, sessionId);
        if (conversation != null) return conversation;
        LocalDateTime now = AiPersistenceTime.now();
        AiConversationDO candidate = new AiConversationDO();
        candidate.setId(IdWorker.getId());
        candidate.setOwnerUserId(ownerUserId);
        candidate.setSessionId(sessionId);
        candidate.setAgnoSessionId(UUID.randomUUID().toString());
        candidate.setStatus(AiConversationStatus.ACTIVE);
        candidate.setExpiresAt(retention.conversationExpiresAt(now));
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        candidate.setCreateBy(ownerUserId);
        candidate.setUpdateBy(ownerUserId);
        conversationMapper.insertIfAbsent(candidate);
        conversation = conversationMapper.findOwnedForUpdate(ownerUserId, sessionId);
        if (conversation == null || conversation.getStatus() != AiConversationStatus.ACTIVE
                || conversation.getExpiresAt() == null || !conversation.getExpiresAt().isAfter(AiPersistenceTime.now())) {
            throw new IllegalStateException("Conversation could not be opened");
        }
        return conversation;
    }

    /** Returns a live owner-scoped conversation for history reads, or null for inaccessible sessions. */
    public AiConversationDO findReadableConversation(Long ownerUserId, String sessionId) {
        requireOwner(ownerUserId);
        requireText(sessionId, "sessionId", 64);
        AiConversationDO conversation = conversationMapper.findOwned(ownerUserId, sessionId);
        if (conversation == null || (conversation.getStatus() != AiConversationStatus.ACTIVE
                && conversation.getStatus() != AiConversationStatus.CLOSED)
                || conversation.getExpiresAt() == null
                || !conversation.getExpiresAt().isAfter(AiPersistenceTime.now())) {
            return null;
        }
        return conversation;
    }

    /** Returns only live conversations owned by the authenticated customer. */
    public List<AiConversationDO> findReadablePage(Long ownerUserId, LocalDateTime cursorTime,
                                                   Long cursorId, int limit) {
        requireOwner(ownerUserId);
        if (limit < 1 || limit > 51 || (cursorTime == null) != (cursorId == null)) {
            throw new IllegalArgumentException("conversation page request is invalid");
        }
        return conversationMapper.findReadablePage(ownerUserId, AiPersistenceTime.now(),
                cursorTime, cursorId, limit);
    }

    /** Maps only completed Agno runs to Java turn IDs within this owner's conversation. */
    public List<AiTurnDO> findCompletedTurnsByAgnoRunIds(Long ownerUserId, Long conversationId,
                                                         List<String> runIds) {
        requireOwner(ownerUserId);
        if (conversationId == null || runIds == null || runIds.isEmpty()) return Collections.emptyList();
        return turnMapper.findCompletedByAgnoRunIds(ownerUserId, conversationId, runIds);
    }

    /** Starts a turn atomically; only a one-way fingerprint of message text is persisted. */
    @Transactional
    public AiTurnStartResult startTurn(Long ownerUserId, String sessionId, String clientMessageId,
                                       String traceId, String message) {
        return startTurn(ownerUserId, sessionId, clientMessageId, traceId, message, null);
    }

    @Transactional
    public AiTurnStartResult startTurn(Long ownerUserId, String sessionId, String clientMessageId,
                                       String traceId, String message, String selectedPolicyId) {
        requireOwner(ownerUserId);
        requireText(sessionId, "sessionId", 64);
        requireUuid(clientMessageId, "clientMessageId");
        requireUuid(traceId, "traceId");
        if (message == null || message.trim().isEmpty() || message.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("message must contain 1 to 4000 characters");
        }
        AiConversationDO conversation = getOrCreateConversation(ownerUserId, sessionId);
        conversation = conversationMapper.findOwnedByIdForUpdate(ownerUserId, conversation.getId());
        if (conversation == null || conversation.getStatus() != AiConversationStatus.ACTIVE
                || conversation.getExpiresAt() == null || !conversation.getExpiresAt().isAfter(AiPersistenceTime.now())) {
            return new AiTurnStartResult(AiTurnStartResult.Outcome.CONVERSATION_UNAVAILABLE, null);
        }
        String requestHash = AiRequestFingerprint.sha256ChatRequest(message, selectedPolicyId);
        AiTurnDO existing = turnMapper.findByIdempotencyKey(ownerUserId, conversation.getId(), clientMessageId);
        if (existing != null) return existingResult(existing, requestHash);

        LocalDateTime now = AiPersistenceTime.now();
        AiTurnDO candidate = new AiTurnDO();
        candidate.setId(IdWorker.getId());
        candidate.setOwnerUserId(ownerUserId);
        candidate.setConversationId(conversation.getId());
        candidate.setTurnId(UUID.randomUUID().toString());
        candidate.setClientMessageId(clientMessageId);
        candidate.setRequestHash(requestHash);
        candidate.setTraceId(traceId);
        candidate.setStatus(AiTurnStatus.PROCESSING);
        candidate.setStartedAt(now);
        candidate.setExpiresAt(retention.conversationExpiresAt(now));
        candidate.setCreateTime(now);
        candidate.setUpdateTime(now);
        candidate.setCreateBy(ownerUserId);
        candidate.setUpdateBy(ownerUserId);
        if (turnMapper.insertIfAbsent(candidate) == 0) {
            AiTurnDO raced = turnMapper.findByIdempotencyKey(ownerUserId, conversation.getId(), clientMessageId);
            return raced == null ? new AiTurnStartResult(AiTurnStartResult.Outcome.IDEMPOTENCY_CONFLICT, null)
                    : existingResult(raced, requestHash);
        }
        conversationMapper.updateActivity(conversation.getId(), ownerUserId, now, retention.conversationExpiresAt(now));
        return new AiTurnStartResult(AiTurnStartResult.Outcome.CREATED, candidate);
    }

    @Transactional
    public boolean completeTurn(Long ownerUserId, String turnId, String agnoRunId, String encryptedResponse) {
        requireOwner(ownerUserId); requireText(turnId, "turnId", 36); requireText(agnoRunId, "agnoRunId", 64);
        requireText(encryptedResponse, "encryptedResponse", 2000000);
        return turnMapper.completeProcessing(ownerUserId, turnId, agnoRunId,
                encryptedResponse, AiPersistenceTime.now()) == 1;
    }

    @Transactional
    public boolean failTurn(Long ownerUserId, String turnId, String safeFailureCode) {
        requireOwner(ownerUserId); requireText(turnId, "turnId", 36); requireText(safeFailureCode, "failureCode", 64);
        return turnMapper.failProcessing(ownerUserId, turnId, safeFailureCode, AiPersistenceTime.now()) == 1;
    }

    /** Immediately denies future access and records a durable remote deletion request. */
    @Transactional
    public boolean requestDelete(Long ownerUserId, String sessionId) {
        requireOwner(ownerUserId); requireText(sessionId, "sessionId", 64);
        AiConversationDO conversation = conversationMapper.findOwnedForUpdate(ownerUserId, sessionId);
        if (conversation == null) return false;
        // The external journal is durable independently of SQL backups. A failed append aborts
        // this transaction, so deletion is never acknowledged without a restorable tombstone.
        deletionLedger.record(conversation.getAgnoSessionId());
        LocalDateTime now = AiPersistenceTime.now();
        if (conversation.getStatus() == AiConversationStatus.DELETE_PENDING) {
            enqueueCleanup(conversation, now);
            return true;
        }
        if (conversationMapper.markDeletePending(conversation.getId(), ownerUserId, now) == 0) return false;
        enqueueCleanup(conversation, now);
        return true;
    }

    static AiTurnStartResult existingResult(AiTurnDO turn, String requestedHash) {
        return new AiTurnStartResult(turn.getRequestHash().equals(requestedHash)
                ? AiTurnStartResult.Outcome.EXISTING : AiTurnStartResult.Outcome.IDEMPOTENCY_CONFLICT, turn);
    }

    static void enqueueCleanup(AiConversationDO conversation, LocalDateTime now, AiCleanupTaskMapper mapper) {
        AiCleanupTaskDO task = new AiCleanupTaskDO();
        task.setId(IdWorker.getId()); task.setOwnerUserId(conversation.getOwnerUserId());
        task.setConversationId(conversation.getId()); task.setAgnoSessionId(conversation.getAgnoSessionId());
        task.setCreateTime(now); task.setUpdateTime(now); task.setCreateBy(conversation.getOwnerUserId());
        task.setUpdateBy(conversation.getOwnerUserId());
        mapper.insertIfAbsent(task);
    }

    private void enqueueCleanup(AiConversationDO conversation, LocalDateTime now) {
        enqueueCleanup(conversation, now, cleanupTaskMapper);
    }

    static void requireOwner(Long ownerUserId) {
        if (ownerUserId == null || ownerUserId <= 0) throw new IllegalArgumentException("ownerUserId is required");
    }
    static void requireText(String value, String name, int maxLength) {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength)
            throw new IllegalArgumentException(name + " is invalid");
    }
    static void requireUuid(String value, String name) {
        try {
            if (value == null || value.length() != 36 || !UUID.fromString(value).toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException(name + " must be a UUID");
            }
        }
        catch (RuntimeException exception) { throw new IllegalArgumentException(name + " must be a UUID"); }
    }
}
