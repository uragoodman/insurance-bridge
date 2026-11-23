package com.itheima.bolee.insurance.service.ai.terms;

import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceAuditRepository;
import com.itheima.bolee.insurance.repository.ai.terms.InsuranceTermSourceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** Persists deletion reservation/finalization separately from non-transactional object storage I/O. */
@Service
@ConditionalOnProperty(prefix = "insurance.terms.storage", name = "enabled", havingValue = "true")
public class InsuranceTermSourceDeletionLifecycleService {
    private final InsuranceTermSourceRepository sourceRepository;
    private final InsuranceTermSourceAuditRepository auditRepository;

    public InsuranceTermSourceDeletionLifecycleService(InsuranceTermSourceRepository sourceRepository,
                                                       InsuranceTermSourceAuditRepository auditRepository) {
        this.sourceRepository = sourceRepository;
        this.auditRepository = auditRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean reserve(Long sourceId, String fromStatus, Long actorId) {
        LocalDateTime now = LocalDateTime.now();
        if (!sourceRepository.reserveDraftDeletion(sourceId, actorId, now)) return false;
        auditRepository.append(sourceId, "DELETE_REQUESTED", fromStatus, "DELETING", actorId, null);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean finish(Long sourceId, Long actorId) {
        LocalDateTime now = LocalDateTime.now();
        if (!sourceRepository.softDeleteDraft(sourceId, actorId, now)) return false;
        auditRepository.append(sourceId, "DELETED", "DELETING", "DELETED", actorId, null);
        return true;
    }
}
