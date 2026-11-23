package com.itheima.bolee.insurance.repository.ai.terms;

import com.itheima.bolee.insurance.mapper.ai.terms.InsuranceTermIndexTaskMapper;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermIndexTaskDO;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class InsuranceTermIndexTaskRepository {
    private final InsuranceTermIndexTaskMapper mapper;

    public InsuranceTermIndexTaskRepository(InsuranceTermIndexTaskMapper mapper) { this.mapper = mapper; }

    public void insert(InsuranceTermIndexTaskDO task) {
        if (mapper.insert(task) != 1) throw new IllegalStateException("Could not enqueue insurance term index task");
    }

    public InsuranceTermIndexTaskDO findNextClaimable(LocalDateTime now) { return mapper.findNextClaimable(now); }
    public boolean claim(Long id, LocalDateTime now, LocalDateTime leaseUntil, String leaseToken) {
        return mapper.claim(id, now, leaseUntil, leaseToken) == 1;
    }
    public InsuranceTermIndexTaskDO findById(Long id) { return mapper.findById(id); }
    public InsuranceTermIndexTaskDO findByIdForUpdate(Long id) { return mapper.findByIdForUpdate(id); }
    public boolean complete(Long id, String leaseToken, LocalDateTime now) { return mapper.complete(id, leaseToken, now) == 1; }
    public boolean fail(Long id, String status, LocalDateTime nextAttemptAt, String reason,
                        String leaseToken, LocalDateTime now) {
        return mapper.fail(id, status, nextAttemptAt, reason, leaseToken, now) == 1;
    }
    public boolean heartbeat(Long id, String leaseToken, LocalDateTime now, LocalDateTime leaseUntil) {
        return mapper.heartbeat(id, leaseToken, now, leaseUntil) == 1;
    }
    public InsuranceTermIndexTaskDO findNextExhaustedLease(LocalDateTime now, int maxAttempts) {
        return mapper.findNextExhaustedLease(now, maxAttempts);
    }
    public boolean failExpiredLease(Long id, LocalDateTime now, int maxAttempts, String reason) {
        return mapper.failExpiredLease(id, now, maxAttempts, reason) == 1;
    }
}
