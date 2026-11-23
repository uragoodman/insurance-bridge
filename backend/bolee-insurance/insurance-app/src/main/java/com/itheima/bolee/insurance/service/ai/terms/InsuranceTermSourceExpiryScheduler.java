package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.service.ai.terms.InsuranceTermSourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceExpiryScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(InsuranceTermSourceExpiryScheduler.class);
    private final InsuranceTermSourceService sourceService;

    public InsuranceTermSourceExpiryScheduler(InsuranceTermSourceService sourceService) {
        this.sourceService = sourceService;
    }

    @Scheduled(cron = "${insurance.terms.expiry-scan-cron:0 */5 * * * *}")
    public void expirePublishedTerms() {
        int total = 0;
        int changed;
        int batches = 0;
        do {
            changed = sourceService.withdrawExpiredSources(200);
            total += changed;
            batches++;
        } while (changed == 200 && batches < 20);
        if (total > 0) LOGGER.info("Expired and queued removal for {} insurance term sources", total);
    }
}
