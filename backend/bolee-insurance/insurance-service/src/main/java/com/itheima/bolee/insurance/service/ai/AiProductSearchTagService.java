package com.itheima.bolee.insurance.service.ai;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagDTO;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagRow;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagSetRequest;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagSetVO;
import com.itheima.bolee.insurance.repository.ai.AiProductSearchTagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class AiProductSearchTagService {
    private static final Set<String> ALLOWED_TYPES = new HashSet<String>(Arrays.asList(
            "PRODUCT_TYPE", "AUDIENCE", "SCENARIO", "KEYWORD", "ALIAS"));

    private final AiProductSearchTagRepository repository;
    private final IdentifierGenerator identifierGenerator;

    public AiProductSearchTagService(AiProductSearchTagRepository repository,
                                     IdentifierGenerator identifierGenerator) {
        this.repository = repository;
        this.identifierGenerator = identifierGenerator;
    }

    public AiSearchTagSetVO getActive(Long productId) {
        validateProductId(productId);
        List<AiSearchTagRow> rows = repository.findActiveTags(productId);
        AiSearchTagSetVO result = new AiSearchTagSetVO();
        result.setProductId(productId);
        result.setVersion(rows.isEmpty() || rows.get(0).getVersionNo() == null ? 0 : rows.get(0).getVersionNo());
        result.setSourceType(rows.isEmpty() ? null : rows.get(0).getSourceType());
        result.setModelId(rows.isEmpty() ? null : rows.get(0).getModelId());
        result.setPromptVersion(rows.isEmpty() ? null : rows.get(0).getPromptVersion());
        List<AiSearchTagDTO> tags = new ArrayList<AiSearchTagDTO>();
        for (AiSearchTagRow row : rows) {
            if (!StringUtils.hasText(row.getTagValue())) continue;
            AiSearchTagDTO tag = new AiSearchTagDTO();
            tag.setTagType(row.getTagType());
            tag.setTagValue(row.getTagValue());
            tag.setEvidence(row.getEvidence());
            tags.add(tag);
        }
        result.setTags(tags);
        return result;
    }

    @Transactional
    public AiSearchTagSetVO replaceManualTags(Long productId, AiSearchTagSetRequest request, Long actorId) {
        if (request == null || request.getTags() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags are required; use an empty list to clear them");
        }
        return replaceTags(productId, request.getTags(), actorId, "MANUAL", null, null);
    }

    @Transactional
    public AiSearchTagSetVO replaceGeneratedTags(Long productId, List<AiSearchTagDTO> tags, Long actorId,
                                                 String modelId, String promptVersion) {
        if (tags == null || tags.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The generator found no sufficiently supported product tags; existing tags were kept");
        }
        for (AiSearchTagDTO tag : tags) {
            if (tag != null && "ALIAS".equalsIgnoreCase(tag.getTagType())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Product aliases must be reviewed and entered manually");
            }
        }
        if (!StringUtils.hasText(modelId) || modelId.length() > 128
                || !StringUtils.hasText(promptVersion) || promptVersion.length() > 32) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Tag generator metadata is invalid");
        }
        return replaceTags(productId, tags, actorId, "AI", modelId.trim(), promptVersion.trim());
    }

    private AiSearchTagSetVO replaceTags(Long productId, List<AiSearchTagDTO> tags, Long actorId,
                                         String sourceType, String modelId, String promptVersion) {
        validateProductId(productId);
        if (actorId == null || actorId <= 0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authenticated admin user is required");
        }
        if (tags == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tags are required; use an empty list to clear them");
        }
        if (tags.size() > 50) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 50 tags are allowed");
        }
        if (!repository.lockProduct(productId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        List<AiSearchTagDTO> tagInputs = new ArrayList<AiSearchTagDTO>();
        if ("AI".equals(sourceType)) {
            for (AiSearchTagRow existing : repository.findActiveTags(productId)) {
                if (!StringUtils.hasText(existing.getTagValue())) continue;
                AiSearchTagDTO preserved = new AiSearchTagDTO();
                preserved.setTagType(existing.getTagType());
                preserved.setTagValue(existing.getTagValue());
                preserved.setEvidence(existing.getEvidence());
                tagInputs.add(preserved);
            }
        }
        tagInputs.addAll(tags);
        List<AiSearchTagDTO> normalizedTags = normalizeTags(tagInputs);

        int version = repository.findLatestVersion(productId) + 1;
        repository.archiveActiveTagSets(productId);
        Long tagSetId = nextId();
        repository.insertTagSet(tagSetId, productId, version, sourceType, modelId, promptVersion, actorId);
        List<AiSearchTagRow> rows = new ArrayList<AiSearchTagRow>();
        for (AiSearchTagDTO tag : normalizedTags) {
            AiSearchTagRow row = new AiSearchTagRow();
            row.setTagSetId(tagSetId);
            row.setTagType(tag.getTagType());
            row.setTagValue(tag.getTagValue());
            row.setEvidence(tag.getEvidence());
            row.setNormalizedValue(normalizeValue(tag.getTagValue()));
            row.setId(nextId());
            rows.add(row);
        }
        repository.insertTags(rows);
        return getActive(productId);
    }

    private List<AiSearchTagDTO> normalizeTags(List<AiSearchTagDTO> input) {
        List<AiSearchTagDTO> result = new ArrayList<AiSearchTagDTO>();
        Set<String> keys = new HashSet<String>();
        for (AiSearchTagDTO source : input) {
            if (source == null || !StringUtils.hasText(source.getTagType())
                    || !StringUtils.hasText(source.getTagValue())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag type and value are required");
            }
            String type = source.getTagType().trim().toUpperCase(Locale.ROOT);
            String value = source.getTagValue().trim();
            if (!ALLOWED_TYPES.contains(type)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported tag type: " + type);
            }
            if (value.length() > 128 || (source.getEvidence() != null && source.getEvidence().length() > 512)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tag value or evidence is too long");
            }
            String key = type + "\u0000" + normalizeValue(value);
            if (!keys.add(key)) continue;
            AiSearchTagDTO tag = new AiSearchTagDTO();
            tag.setTagType(type);
            tag.setTagValue(value);
            tag.setEvidence(StringUtils.hasText(source.getEvidence()) ? source.getEvidence().trim() : null);
            result.add(tag);
        }
        return result;
    }

    private Long nextId() {
        return ((Number) identifierGenerator.nextId(this)).longValue();
    }

    private static String normalizeValue(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product id");
        }
    }
}
