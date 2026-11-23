package com.itheima.bolee.insurance.repository.ai.terms;

import com.itheima.bolee.insurance.mapper.ai.terms.InsuranceTermSourceMapper;
import com.itheima.bolee.insurance.pojo.ai.terms.InsuranceTermSourceDO;
import com.itheima.bolee.insurance.dto.ai.terms.InsuranceTermProductVersionVO;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class InsuranceTermSourceRepository {

    private final InsuranceTermSourceMapper mapper;

    public InsuranceTermSourceRepository(InsuranceTermSourceMapper mapper) {
        this.mapper = mapper;
    }

    public Integer findProductVersion(Long insuranceId, Long profileVersionId) {
        return mapper.findProductVersion(insuranceId, profileVersionId);
    }

    public void insert(InsuranceTermSourceDO source) {
        if (mapper.insertSource(source) != 1) {
            throw new IllegalStateException("Insurance source metadata was not saved");
        }
    }

    public InsuranceTermSourceDO findById(Long id) { return mapper.findById(id); }

    public long count(Long insuranceId, Long profileVersionId, String status) {
        return mapper.countSources(insuranceId, profileVersionId, status);
    }

    public List<InsuranceTermSourceDO> list(Long insuranceId, Long profileVersionId,
                                            String status, int limit, long offset) {
        return mapper.listSources(insuranceId, profileVersionId, status, limit, offset);
    }

    public boolean submitForReview(Long id, Long userId, LocalDateTime now) {
        return mapper.submitForReview(id, userId, now) == 1;
    }

    public boolean review(Long id, String nextStatus, String note, Long userId, LocalDateTime now) {
        return mapper.review(id, nextStatus, note, userId, now) == 1;
    }

    public boolean publish(Long id, Long userId, LocalDateTime now) {
        return mapper.publish(id, userId, now) == 1;
    }

    public boolean withdraw(Long id, String reason, Long userId, LocalDateTime now) {
        return mapper.withdraw(id, reason, userId, now) == 1;
    }

    public boolean updateDraft(Long id, String title, String sourceUri, LocalDate effectiveFrom,
                               LocalDate effectiveTo, Long userId, LocalDateTime now) {
        return mapper.updateDraft(id, title, sourceUri, effectiveFrom, effectiveTo, userId, now) == 1;
    }

    public boolean softDeleteDraft(Long id, Long userId, LocalDateTime now) {
        return mapper.softDeleteDraft(id, userId, now) == 1;
    }

    public boolean reserveDraftDeletion(Long id, Long userId, LocalDateTime now) {
        return mapper.reserveDraftDeletion(id, userId, now) == 1;
    }

    public List<InsuranceTermSourceDO> findDeleting(int limit) { return mapper.findDeleting(limit); }

    public List<InsuranceTermSourceDO> listActivePublished(Long insuranceId, Long profileVersionId,
                                                            boolean includeSynthetic) {
        return mapper.listActivePublished(insuranceId, profileVersionId, includeSynthetic);
    }

    public List<InsuranceTermProductVersionVO> listActivePublishedVersions(Long insuranceId,
                                                                            boolean includeSynthetic) {
        return mapper.listActivePublishedVersions(insuranceId, includeSynthetic);
    }

    public boolean isActivePublished(Long sourceId, Long insuranceId, Long profileVersionId,
                                     boolean includeSynthetic) {
        return mapper.isActivePublished(sourceId, insuranceId, profileVersionId, includeSynthetic) == 1;
    }

    public boolean markIndexed(Long sourceId, String sha256, LocalDateTime now) {
        return mapper.markIndexedIfValid(sourceId, sha256, now) == 1;
    }

    public boolean withdrawInvalidPublishing(Long sourceId, String sha256, LocalDateTime now) {
        return mapper.withdrawInvalidPublishing(sourceId, sha256, now) == 1;
    }

    public boolean markIndexFailed(Long sourceId, String sha256, String reason, LocalDateTime now) {
        return mapper.markIndexFailed(sourceId, sha256, reason, now) == 1;
    }

    public boolean markIndexRemoved(Long sourceId, String sha256, LocalDateTime now) {
        return mapper.markIndexRemoved(sourceId, sha256, now) == 1;
    }

    public boolean markIndexRemovalFailed(Long sourceId, String sha256, String reason, LocalDateTime now) {
        return mapper.markIndexRemovalFailed(sourceId, sha256, reason, now) == 1;
    }

    public boolean retryIndexSync(Long id, Long userId, LocalDateTime now) {
        return mapper.retryIndexSync(id, userId, now) == 1;
    }

    public List<InsuranceTermSourceDO> findSourcesNeedingExpiryCleanup(LocalDateTime now, int limit) {
        return mapper.findSourcesNeedingExpiryCleanup(now, limit);
    }

    public boolean withdrawExpired(Long id, LocalDateTime now) {
        return mapper.withdrawExpired(id, now) == 1;
    }
}
