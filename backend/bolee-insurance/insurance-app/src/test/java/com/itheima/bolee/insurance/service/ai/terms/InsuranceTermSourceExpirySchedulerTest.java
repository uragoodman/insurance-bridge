package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermSourceExpiryScheduler;
import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermSourceService;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class InsuranceTermSourceExpirySchedulerTest {

    @Test
    public void expirySchedulerContinuesBatchesUntilBacklogIsDrained() {
        InsuranceTermSourceService service = mock(InsuranceTermSourceService.class);
        when(service.withdrawExpiredSources(200)).thenReturn(200, 200, 17, 0);
        InsuranceTermSourceExpiryScheduler scheduler = new InsuranceTermSourceExpiryScheduler(service);

        scheduler.expirePublishedTerms();

        verify(service, times(3)).withdrawExpiredSources(200);
    }

    @Test
    public void expirySchedulerCapsWorkPerRunToAvoidUnboundedExecution() {
        InsuranceTermSourceService service = mock(InsuranceTermSourceService.class);
        when(service.withdrawExpiredSources(200)).thenReturn(200);
        InsuranceTermSourceExpiryScheduler scheduler = new InsuranceTermSourceExpiryScheduler(service);

        scheduler.expirePublishedTerms();

        verify(service, times(20)).withdrawExpiredSources(200);
    }
}
