package com.itheima.bolee.insurance.repository.ai;

import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagRow;
import com.itheima.bolee.insurance.mapper.ai.AiProductSearchTagMapper;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
public class AiProductSearchTagRepository {
    private final AiProductSearchTagMapper mapper;

    public AiProductSearchTagRepository(AiProductSearchTagMapper mapper) {
        this.mapper = mapper;
    }

    public boolean lockProduct(Long productId) { return mapper.lockProduct(productId) != null; }
    public int findLatestVersion(Long productId) { return mapper.findLatestVersion(productId); }
    public Long findActiveTagSetId(Long productId) { return mapper.findActiveTagSetId(productId); }
    public void archiveActiveTagSets(Long productId) { mapper.archiveActiveTagSets(productId); }
    public void insertTagSet(Long id, Long productId, int version, String sourceType,
                             String modelId, String promptVersion, Long actorId) {
        mapper.insertTagSet(id, productId, version, sourceType, modelId, promptVersion, actorId);
    }
    public void insertTags(List<AiSearchTagRow> tags) {
        if (tags != null && !tags.isEmpty()) mapper.insertTags(tags);
    }
    public List<AiSearchTagRow> findActiveTags(Long productId) { return mapper.findActiveTags(productId); }
    public List<AiSearchTagRow> findActiveTagsByProductIds(List<Long> productIds) {
        return productIds == null || productIds.isEmpty()
                ? Collections.<AiSearchTagRow>emptyList() : mapper.findActiveTagsByProductIds(productIds);
    }
}
