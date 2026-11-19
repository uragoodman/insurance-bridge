package com.itheima.bolee.insurance.repository.ai;

import com.itheima.bolee.insurance.mapper.ai.AiRecommendationMapper;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationAttributeRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationMultiValueRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOperatorRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOptionRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductFactRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationRuleRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationSnapshotRow;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/** Read-side persistence boundary for the versioned AI recommendation catalog. */
@Repository
public class AiRecommendationRepository {

    private final AiRecommendationMapper mapper;

    public AiRecommendationRepository(AiRecommendationMapper mapper) {
        this.mapper = mapper;
    }

    public AiRecommendationSnapshotRow findCurrentSnapshot(Long categoryId, LocalDateTime now) {
        return mapper.findCurrentSnapshot(categoryId, now);
    }

    public boolean hasSnapshotPointer(Long categoryId) {
        return mapper.countSnapshotPointers(categoryId) > 0;
    }

    public List<AiRecommendationAttributeRow> findSnapshotAttributes(Long snapshotId) {
        return mapper.findSnapshotAttributes(snapshotId);
    }

    public List<AiRecommendationRuleRow> findSnapshotRules(Long snapshotId) {
        return mapper.findSnapshotRules(snapshotId);
    }

    public List<AiRecommendationOperatorRow> findSnapshotOperators(Long snapshotId) {
        return mapper.findSnapshotOperators(snapshotId);
    }

    public List<AiRecommendationOptionRow> findSnapshotOptions(Long snapshotId) {
        return mapper.findSnapshotOptions(snapshotId);
    }

    public List<AiRecommendationProductRow> findSnapshotProducts(Long snapshotId) {
        return mapper.findSnapshotProducts(snapshotId);
    }

    public List<AiRecommendationProductRow> findSnapshotProductsByIds(Long snapshotId, List<Long> productIds) {
        return mapper.findSnapshotProductsByIds(snapshotId, productIds);
    }

    public List<AiRecommendationProductFactRow> findSnapshotProductFacts(
            Long snapshotId, List<Long> profileVersionIds, LocalDateTime now) {
        return mapper.findSnapshotProductFacts(snapshotId, profileVersionIds, now);
    }

    public List<AiRecommendationMultiValueRow> findSnapshotMultiValues(List<Long> profileVersionIds) {
        return mapper.findSnapshotMultiValues(profileVersionIds);
    }
}
