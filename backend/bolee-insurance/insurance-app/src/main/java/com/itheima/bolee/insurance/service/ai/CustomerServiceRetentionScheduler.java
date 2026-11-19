package com.itheima.bolee.insurance.service.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomerServiceRetentionScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerServiceRetentionScheduler.class);
    private final CustomerServiceHandoffService handoffService;

    public CustomerServiceRetentionScheduler(CustomerServiceHandoffService handoffService) {
        this.handoffService = handoffService;
    }

    @Scheduled(cron = "${customer-service.retention-cleanup-cron:0 29 3 * * *}")
    public void purgeExpiredRequests() {
        try {
            int deleted;
            do {
                deleted = handoffService.purgeExpired(200);
            } while (deleted == 200);
            if (deleted > 0) LOGGER.info("Purged {} expired customer-service requests", deleted);
        } catch (Exception exception) {
            LOGGER.warn("Customer-service retention cleanup failed", exception);
        }
    }
}
