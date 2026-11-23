package com.itheima.bolee.insurance.service.ai.terms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceDeletionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(InsuranceTermSourceDeletionScheduler.class);
    private final InsuranceTermSourceService sourceService;

    public InsuranceTermSourceDeletionScheduler(InsuranceTermSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Scheduled(fixedDelayString = "${insurance.terms.deletion-retry-delay-ms:60000}")
    public void retryPendingDeletions() {
        int completed = sourceService.retryReservedDeletions(100);
        if (completed > 0) LOGGER.info("Completed {} reserved insurance term source deletions", completed);
    }
}
