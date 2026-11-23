package com.itheima.bolee.insurance.service.ai.terms;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.config.terms.InsuranceTermsStorageProperties;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import org.junit.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Collections;

public class InsuranceTermSourcePublishTest {
    @Test
    public void auditRemainsReadableAfterSourceSoftDelete() {
        InsuranceTermSourceRepository repository = mock(InsuranceTermSourceRepository.class);
        InsuranceTermSourceAuditRepository audits = mock(InsuranceTermSourceAuditRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(repository.findById(71L)).thenReturn(null);
        when(audits.hasEvents(71L)).thenReturn(true);
        when(audits.list(71L)).thenReturn(Collections.emptyList());

        InsuranceTermSourceService service = new InsuranceTermSourceService(repository,
                mock(InsuranceTermObjectStorage.class), new InsuranceTermsStorageProperties(),
                mock(IdentifierGenerator.class), mock(InsuranceTermIndexTaskService.class), audits,
                mock(InsuranceTermSourceRegistrationService.class),
                mock(InsuranceTermSourceContentReader.class),
                mock(InsuranceTermSourceDeletionLifecycleService.class), transactionManager);

        assertEquals(Collections.emptyList(), service.listAudit(71L));
        verify(audits).hasEvents(71L);
    }

    @Test
    public void verifiesObjectBeforeOpeningDatabaseTransactionForPublish() {
        InsuranceTermSourceRepository repository = mock(InsuranceTermSourceRepository.class);
        InsuranceTermObjectStorage storage = mock(InsuranceTermObjectStorage.class);
        InsuranceTermIndexTaskService indexTasks = mock(InsuranceTermIndexTaskService.class);
        InsuranceTermSourceAuditRepository audits = mock(InsuranceTermSourceAuditRepository.class);
        InsuranceTermSourceContentReader contentReader = mock(InsuranceTermSourceContentReader.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(mock(TransactionStatus.class));
        when(repository.publish(eq(71L), eq(9L), any())).thenReturn(true);
        InsuranceTermSourceDO source = new InsuranceTermSourceDO();
        source.setId(71L);
        source.setInsuranceId(81L);
        source.setProfileVersionId(91L);
        source.setSourceType("OFFICIAL_PRODUCT_TERM");
        source.setStatus("APPROVED");
        source.setObjectKey("products/81/source.pdf");
        when(repository.findById(71L)).thenReturn(source);

        InsuranceTermSourceService service = new InsuranceTermSourceService(repository, storage,
                new InsuranceTermsStorageProperties(), mock(IdentifierGenerator.class), indexTasks,
                audits, mock(InsuranceTermSourceRegistrationService.class), contentReader,
                mock(InsuranceTermSourceDeletionLifecycleService.class), transactionManager);

        assertEquals("PUBLISHING", service.publish(71L, 9L).getStatus());

        org.mockito.InOrder order = inOrder(contentReader, transactionManager, repository);
        order.verify(contentReader).verify(source);
        order.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
        order.verify(repository).publish(eq(71L), eq(9L), any());
    }
}
