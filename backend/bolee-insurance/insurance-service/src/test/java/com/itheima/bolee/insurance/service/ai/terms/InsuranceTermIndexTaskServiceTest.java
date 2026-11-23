package com.itheima.bolee.insurance.service.ai.terms;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermIndexTaskVO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermIndexTaskDO;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermIndexTaskRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InsuranceTermIndexTaskServiceTest {
    private InsuranceTermIndexTaskRepository taskRepository;
    private InsuranceTermSourceRepository sourceRepository;
    private InsuranceTermSourceAuditRepository auditRepository;
    private IdentifierGenerator identifierGenerator;
    private InsuranceTermIndexTaskService service;

    @Before
    public void setUp() {
        taskRepository = mock(InsuranceTermIndexTaskRepository.class);
        sourceRepository = mock(InsuranceTermSourceRepository.class);
        auditRepository = mock(InsuranceTermSourceAuditRepository.class);
        identifierGenerator = mock(IdentifierGenerator.class);
        when(identifierGenerator.nextId(any())).thenReturn(3001L);
        service = new InsuranceTermIndexTaskService(taskRepository, sourceRepository, null,
                auditRepository, identifierGenerator);
    }

    @Test
    public void expiredSourceDuringIndexingIsWithdrawnAndRemovalIsQueuedAtomically() {
        InsuranceTermIndexTaskDO task = processingTask("lease-a");
        InsuranceTermSourceDO source = source("PUBLISHING");
        when(taskRepository.findByIdForUpdate(11L)).thenReturn(task);
        when(sourceRepository.findById(22L)).thenReturn(source);
        when(sourceRepository.markIndexed(eq(22L), eq("a".repeat(64)), any(LocalDateTime.class)))
                .thenReturn(false);
        when(sourceRepository.withdrawInvalidPublishing(eq(22L), eq("a".repeat(64)), any(LocalDateTime.class)))
                .thenAnswer(invocation -> {
                    source.setStatus("WITHDRAWN");
                    return true;
                });
        when(taskRepository.complete(eq(11L), eq("lease-a"), any(LocalDateTime.class))).thenReturn(true);

        service.complete(11L, "lease-a", "a".repeat(64));

        ArgumentCaptor<InsuranceTermIndexTaskDO> queuedTask = ArgumentCaptor.forClass(InsuranceTermIndexTaskDO.class);
        verify(taskRepository).insert(queuedTask.capture());
        assertEquals("REMOVE", queuedTask.getValue().getAction());
        assertEquals(22L, queuedTask.getValue().getSourceId().longValue());
        verify(auditRepository).append(eq(22L), eq("EXPIRED_DURING_INDEXING"), eq("PUBLISHING"),
                eq("WITHDRAWN"), isNull(), anyString());
        verify(taskRepository).complete(eq(11L), eq("lease-a"), any(LocalDateTime.class));
    }

    @Test
    public void staleLeaseCannotCompleteOrChangeSource() {
        InsuranceTermIndexTaskDO task = processingTask("new-lease");
        when(taskRepository.findByIdForUpdate(11L)).thenReturn(task);

        try {
            service.complete(11L, "old-lease", "a".repeat(64));
            fail("Expected stale lease to be rejected");
        } catch (org.springframework.web.server.ResponseStatusException expected) {
            assertEquals(409, expected.getStatus().value());
        }

        verifyNoInteractions(sourceRepository, auditRepository);
        verify(taskRepository, never()).complete(anyLong(), anyString(), any(LocalDateTime.class));
    }

    @Test
    public void claimRetriesTransientCasConflictsAcrossTheWorkerPool() {
        InsuranceTermIndexTaskDO candidate = new InsuranceTermIndexTaskDO();
        candidate.setId(11L);
        candidate.setSourceId(22L);
        candidate.setAction("INDEX");
        candidate.setSourceSha256("a".repeat(64));
        candidate.setTaskStatus("PENDING");
        when(taskRepository.findNextExhaustedLease(any(LocalDateTime.class), anyInt())).thenReturn(null);
        when(taskRepository.findNextClaimable(any(LocalDateTime.class))).thenReturn(candidate);
        when(taskRepository.claim(eq(11L), any(LocalDateTime.class), any(LocalDateTime.class), anyString()))
                .thenReturn(false);

        assertNull(service.claimNext());

        verify(taskRepository, times(32)).claim(eq(11L), any(LocalDateTime.class),
                any(LocalDateTime.class), anyString());
        verifyNoInteractions(sourceRepository);
    }

    @Test
    public void validIndexedSourceCompletesWithoutSchedulingRemoval() {
        InsuranceTermIndexTaskDO task = processingTask("lease-a");
        InsuranceTermSourceDO source = source("PUBLISHING");
        when(taskRepository.findByIdForUpdate(11L)).thenReturn(task);
        when(sourceRepository.findById(22L)).thenReturn(source);
        when(sourceRepository.markIndexed(eq(22L), eq("a".repeat(64)), any(LocalDateTime.class)))
                .thenReturn(true);
        when(taskRepository.complete(eq(11L), eq("lease-a"), any(LocalDateTime.class))).thenReturn(true);

        service.complete(11L, "lease-a", "a".repeat(64));

        verify(taskRepository, never()).insert(any(InsuranceTermIndexTaskDO.class));
        verify(auditRepository, never()).append(anyLong(), anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    public void staleIndexCompletionAfterWithdrawalQueuesRemovalAgain() {
        InsuranceTermIndexTaskDO task = processingTask("lease-a");
        InsuranceTermSourceDO source = source("WITHDRAWN");
        when(taskRepository.findByIdForUpdate(11L)).thenReturn(task);
        when(sourceRepository.findById(22L)).thenReturn(source);
        when(taskRepository.complete(eq(11L), eq("lease-a"), any(LocalDateTime.class))).thenReturn(true);

        service.complete(11L, "lease-a", "a".repeat(64));

        ArgumentCaptor<InsuranceTermIndexTaskDO> queuedTask = ArgumentCaptor.forClass(InsuranceTermIndexTaskDO.class);
        verify(taskRepository).insert(queuedTask.capture());
        assertEquals("REMOVE", queuedTask.getValue().getAction());
        assertEquals(22L, queuedTask.getValue().getSourceId().longValue());
        verify(taskRepository).complete(eq(11L), eq("lease-a"), any(LocalDateTime.class));
    }

    @Test
    public void claimedTaskExposesLeaseExpiryAndDuration() {
        InsuranceTermIndexTaskDO candidate = new InsuranceTermIndexTaskDO();
        candidate.setId(11L);
        candidate.setSourceId(22L);
        candidate.setAction("INDEX");
        candidate.setSourceSha256("a".repeat(64));
        candidate.setTaskStatus("PENDING");
        InsuranceTermSourceDO source = source("PUBLISHING");
        when(taskRepository.findNextExhaustedLease(any(LocalDateTime.class), anyInt())).thenReturn(null);
        when(taskRepository.findNextClaimable(any(LocalDateTime.class))).thenReturn(candidate);
        when(taskRepository.claim(eq(11L), any(LocalDateTime.class), any(LocalDateTime.class), anyString())).thenReturn(true);
        when(sourceRepository.findById(22L)).thenReturn(source);

        InsuranceTermIndexTaskVO claimed = service.claimNext();

        assertNotNull(claimed.getLeaseUntil());
        assertEquals(Integer.valueOf(180), claimed.getLeaseDurationSeconds());
        assertEquals(claimed.getLeaseToken(), candidate.getLeaseToken());
    }

    private InsuranceTermIndexTaskDO processingTask(String leaseToken) {
        InsuranceTermIndexTaskDO task = new InsuranceTermIndexTaskDO();
        task.setId(11L);
        task.setSourceId(22L);
        task.setAction("INDEX");
        task.setSourceSha256("a".repeat(64));
        task.setTaskStatus("PROCESSING");
        task.setAttempts(1);
        task.setLeaseToken(leaseToken);
        task.setLeaseUntil(LocalDateTime.now().plusMinutes(2));
        return task;
    }

    private InsuranceTermSourceDO source(String status) {
        InsuranceTermSourceDO source = new InsuranceTermSourceDO();
        source.setId(22L);
        source.setInsuranceId(33L);
        source.setProfileVersionId(44L);
        source.setSha256("a".repeat(64));
        source.setStatus(status);
        source.setFileSize(128L);
        source.setObjectKey("test/terms.pdf");
        return source;
    }
}
