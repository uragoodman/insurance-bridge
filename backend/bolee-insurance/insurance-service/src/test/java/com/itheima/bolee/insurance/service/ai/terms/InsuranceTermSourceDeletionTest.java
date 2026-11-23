package com.itheima.bolee.insurance.service.ai.terms;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermIndexTaskRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class InsuranceTermSourceDeletionTest {
    private InsuranceTermSourceRepository repository;
    private InsuranceTermObjectStorage objectStorage;
    private InsuranceTermSourceDeletionLifecycleService lifecycle;
    private InsuranceTermSourceService service;

    @Before
    public void setUp() {
        repository = mock(InsuranceTermSourceRepository.class);
        objectStorage = mock(InsuranceTermObjectStorage.class);
        lifecycle = mock(InsuranceTermSourceDeletionLifecycleService.class);
        service = new InsuranceTermSourceService(repository, objectStorage,
                mock(InsuranceTermsStorageProperties.class), mock(IdentifierGenerator.class),
                mock(InsuranceTermIndexTaskService.class), mock(InsuranceTermSourceAuditRepository.class),
                mock(InsuranceTermSourceRegistrationService.class),
                mock(InsuranceTermSourceContentReader.class), lifecycle,
                mock(org.springframework.transaction.PlatformTransactionManager.class));
    }

    @Test
    public void failedObjectRemovalLeavesCommittedDeletionReservationForRetry() {
        InsuranceTermSourceDO draft = source("DRAFT");
        InsuranceTermSourceDO reserved = source("DELETING");
        when(repository.findById(51L)).thenReturn(draft, reserved);
        when(lifecycle.reserve(51L, "DRAFT", 7L)).thenReturn(true);
        doThrow(new IllegalStateException("MinIO unavailable")).when(objectStorage).delete("products/51/source.pdf");

        try {
            service.deleteDraft(51L, 7L);
            org.junit.Assert.fail("Expected a retryable storage failure");
        } catch (org.springframework.web.server.ResponseStatusException expected) {
            assertEquals(502, expected.getStatus().value());
        }

        verify(lifecycle).reserve(51L, "DRAFT", 7L);
        verify(lifecycle, never()).finish(anyLong(), anyLong());
    }

    @Test
    public void reservedDeletionIsRetriedAfterDatabaseFinalizeFailure() {
        InsuranceTermSourceDO reserved = source("DELETING");
        when(repository.findDeleting(100)).thenReturn(Collections.singletonList(reserved));
        when(repository.findById(51L)).thenReturn(reserved);
        when(lifecycle.finish(51L, 7L)).thenThrow(new IllegalStateException("Database unavailable"));

        int completed = service.retryReservedDeletions(100);

        assertEquals(0, completed);
        verify(objectStorage).delete("products/51/source.pdf");
        verify(lifecycle).finish(51L, 7L);
    }

    @Test
    public void successfulRetryFinishesTheSoftDeleteAndAuditLifecycle() {
        InsuranceTermSourceDO reserved = source("DELETING");
        when(repository.findDeleting(100)).thenReturn(Collections.singletonList(reserved));
        when(repository.findById(51L)).thenReturn(reserved);
        when(lifecycle.finish(51L, 7L)).thenReturn(true);

        int completed = service.retryReservedDeletions(100);

        assertEquals(1, completed);
        verify(objectStorage).delete("products/51/source.pdf");
        verify(lifecycle).finish(51L, 7L);
    }

    private InsuranceTermSourceDO source(String status) {
        InsuranceTermSourceDO source = new InsuranceTermSourceDO();
        source.setId(51L);
        source.setStatus(status);
        source.setObjectKey("products/51/source.pdf");
        source.setUpdateBy(7L);
        return source;
    }
}
