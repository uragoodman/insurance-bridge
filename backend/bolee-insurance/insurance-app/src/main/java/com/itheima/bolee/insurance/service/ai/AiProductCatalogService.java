package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.AiProductCandidateVO;
import com.itheima.bolee.insurance.dto.ai.AiProductCategoryVO;
import com.itheima.bolee.insurance.dto.ai.AiProductSearchResponseVO;
import com.itheima.bolee.insurance.dto.ai.admin.AiSearchTagRow;
import com.itheima.bolee.insurance.pojo.Category;
import com.itheima.bolee.insurance.pojo.Insurance;
import com.itheima.bolee.insurance.repository.ai.AiProductCatalogRepository;
import com.itheima.bolee.insurance.repository.ai.AiProductSearchTagRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Application service for the safe, keyword-ranked Agent catalog query. */
@Service
public class AiProductCatalogService {

    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_LIMIT = 3;

    private final AiProductCatalogRepository repository;
    private final AiProductSearchTagRepository searchTagRepository;

    public AiProductCatalogService(AiProductCatalogRepository repository,
                                   AiProductSearchTagRepository searchTagRepository) {
        this.repository = repository;
        this.searchTagRepository = searchTagRepository;
    }

    public List<AiProductCategoryVO> findCategories() {
        return repository.findActiveLeafProductCategories().stream()
                .map(category -> new AiProductCategoryVO(
                        String.valueOf(category.getId()),
                        category.getCategoryNo(),
                        category.getCategoryName()))
                .collect(Collectors.toList());
    }

    public AiProductSearchResponseVO search(String categoryId, String keyword, Integer requestedLimit) {
        return search(categoryId, keyword == null ? Collections.<String>emptyList()
                : Collections.singletonList(keyword), requestedLimit);
    }

    public AiProductSearchResponseVO search(String categoryId, List<String> requestedKeywords, Integer requestedLimit) {
        List<String> keywords = normalizeKeywords(requestedKeywords);
        if (!StringUtils.hasText(categoryId) && keywords.isEmpty()) {
            return new AiProductSearchResponseVO(
                    "NEEDS_CLARIFICATION",
                    Collections.singletonList("PRODUCT_CATEGORY"),
                    Collections.emptyList());
        }

        Long parsedCategoryId = null;
        if (StringUtils.hasText(categoryId)) {
            try {
                if (!categoryId.matches("[0-9]{1,19}")) {
                    throw new NumberFormatException("category id must be numeric");
                }
                parsedCategoryId = Long.valueOf(categoryId);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product category id");
            }
        }

        int limit = requestedLimit == null ? DEFAULT_LIMIT : requestedLimit;
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 3");
        }

        List<Category> activeCategories = repository.findActiveLeafProductCategories();
        Category selectedCategory = null;
        if (parsedCategoryId != null) {
            selectedCategory = repository.findActiveLeafProductCategory(parsedCategoryId);
            if (selectedCategory == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Unknown or unavailable product category");
            }
        }

        Map<Long, Category> categoriesByNo = new HashMap<Long, Category>();
        List<Long> activeCategoryNos = new ArrayList<Long>();
        for (Category category : activeCategories) {
            try {
                Long categoryNo = Long.valueOf(category.getCategoryNo());
                categoriesByNo.put(categoryNo, category);
                activeCategoryNos.add(categoryNo);
            } catch (RuntimeException exception) {
                // Ignore malformed categories; they cannot safely identify an insurance product.
            }
        }
        Long selectedCategoryNo = null;
        if (selectedCategory != null) {
            try {
                selectedCategoryNo = Long.valueOf(selectedCategory.getCategoryNo());
            } catch (RuntimeException exception) {
                selectedCategoryNo = null;
            }
        }
        if (selectedCategory != null && selectedCategoryNo == null) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Product category is not configured correctly");
        }

        List<Insurance> products = repository.findActiveProducts(
                activeCategoryNos, selectedCategoryNo, keywords, limit);
        if (products.isEmpty()) {
            return new AiProductSearchResponseVO(
                    "NO_MATCH", Collections.emptyList(), Collections.emptyList());
        }

        List<Long> productIds = products.stream().map(Insurance::getId).collect(Collectors.toList());
        Map<Long, List<AiSearchTagRow>> tagsByProduct = new HashMap<Long, List<AiSearchTagRow>>();
        for (AiSearchTagRow tag : searchTagRepository.findActiveTagsByProductIds(productIds)) {
            tagsByProduct.computeIfAbsent(tag.getProductId(), ignored -> new ArrayList<AiSearchTagRow>()).add(tag);
        }
        List<RankedProduct> ranked = new ArrayList<RankedProduct>();
        for (Insurance product : products) {
            Category category = selectedCategory == null
                    ? categoriesByNo.get(product.getCategoryNo()) : selectedCategory;
            if (category == null) continue;
            List<AiSearchTagRow> tags = tagsByProduct.getOrDefault(product.getId(), Collections.<AiSearchTagRow>emptyList());
            Match match = rank(product, category, tags, keywords);
            if (!keywords.isEmpty() && !match.coversEnoughTerms(keywords.size())) continue;
            ranked.add(new RankedProduct(product, category, match));
        }
        ranked.sort(Comparator.comparingInt((RankedProduct item) -> item.match.score).reversed()
                .thenComparing(item -> item.product.getSortNo(), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(item -> item.product.getId()));
        List<AiProductCandidateVO> candidates = new ArrayList<AiProductCandidateVO>();
        for (RankedProduct item : ranked.stream().limit(limit).collect(Collectors.toList())) {
            candidates.add(new AiProductCandidateVO(
                    String.valueOf(item.product.getId()),
                    item.product.getInsuranceName(),
                    String.valueOf(item.category.getId()),
                    item.category.getCategoryNo(),
                    item.category.getCategoryName(),
                    item.match.reasons,
                    null,
                    "CATALOG_MATCH",
                    Collections.emptyList(),
                    productSummary(item.product)));
        }
        if (candidates.isEmpty()) return new AiProductSearchResponseVO("NO_MATCH", Collections.emptyList(), candidates);
        return new AiProductSearchResponseVO("MATCHED", Collections.emptyList(), candidates);
    }

    private static List<String> normalizeKeywords(List<String> requested) {
        Set<String> values = new LinkedHashSet<String>();
        if (requested == null) return new ArrayList<String>();
        for (String value : requested) {
            if (!StringUtils.hasText(value)) continue;
            String normalized = value.trim();
            if (normalized.length() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search keyword is too long");
            }
            values.add(normalized);
            if (values.size() > 10) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At most 10 search keywords are allowed");
            }
        }
        return new ArrayList<String>(values);
    }

    private static Match rank(Insurance product, Category category, List<AiSearchTagRow> tags,
                              List<String> keywords) {
        int score = 0;
        int matchedTermCount = 0;
        List<String> reasons = new ArrayList<String>();
        for (String keyword : keywords) {
            String query = normalize(keyword);
            int best = 0;
            String bestReason = null;
            for (AiSearchTagRow tag : tags) {
                if (matchesTag(tag.getTagValue(), query)) {
                    int weight = "PRODUCT_TYPE".equals(tag.getTagType()) ? 100
                            : "AUDIENCE".equals(tag.getTagType()) ? 90
                            : "SCENARIO".equals(tag.getTagType()) ? 80 : 70;
                    if (weight > best) {
                        best = weight;
                        bestReason = "产品标签匹配：" + tag.getTagValue();
                    }
                }
            }
            if (contains(product.getInsuranceName(), query) && best < 60) {
                best = 60;
                bestReason = "产品名称匹配：" + keyword;
            }
            if (contains(category.getCategoryName(), query) && best < 55) {
                best = 55;
                bestReason = "产品类别匹配：" + keyword;
            }
            if ((contains(product.getLabelsJson(), query) || contains(product.getRemakeJson(), query)) && best < 40) {
                best = 40;
                bestReason = "产品标签或投保说明匹配：" + keyword;
            }
            if ((contains(product.getComment(), query) || contains(product.getRemake(), query)
                    || contains(product.getPublishNumber(), query)) && best < 20) {
                best = 20;
                bestReason = "产品描述匹配：" + keyword;
            }
            score += best;
            if (best > 0) matchedTermCount++;
            if (bestReason != null && !reasons.contains(bestReason)) reasons.add(bestReason);
        }
        if (keywords.isEmpty()) reasons.add("管理员已加入 AI 推荐候选池");
        return new Match(score, matchedTermCount, reasons);
    }

    private static boolean contains(String source, String query) {
        return StringUtils.hasText(source) && StringUtils.hasText(query)
                && normalize(source).contains(query);
    }

    private static boolean matchesTag(String tagValue, String query) {
        if (!StringUtils.hasText(tagValue) || !StringUtils.hasText(query)) return false;
        String tag = normalize(tagValue);
        if (tag.length() < 2 || query.length() < 2) return tag.equals(query);
        return tag.contains(query) || query.contains(tag);
    }

    private static String normalize(String value) { return value.trim().toLowerCase(Locale.ROOT); }

    private static final class Match {
        private final int score;
        private final int matchedTermCount;
        private final List<String> reasons;
        private Match(int score, int matchedTermCount, List<String> reasons) {
            this.score = score;
            this.matchedTermCount = matchedTermCount;
            this.reasons = reasons;
        }
        private boolean coversEnoughTerms(int totalTerms) {
            return matchedTermCount >= Math.max(1, (totalTerms * 2 + 2) / 3);
        }
    }

    private static final class RankedProduct {
        private final Insurance product;
        private final Category category;
        private final Match match;
        private RankedProduct(Insurance product, Category category, Match match) {
            this.product = product; this.category = category; this.match = match;
        }
    }

    private static String productSummary(Insurance product) {
        List<String> parts = new ArrayList<String>();
        addSummaryPart(parts, "产品标签", product.getLabelsJson());
        addSummaryPart(parts, "投保说明", product.getRemakeJson());
        addSummaryPart(parts, "产品点评", product.getComment());
        addSummaryPart(parts, "产品描述", product.getRemake());
        String summary = String.join("；", parts);
        return summary.length() <= 4000 ? summary : summary.substring(0, 4000);
    }

    private static void addSummaryPart(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            String normalized = value.trim();
            if (normalized.length() > 1000) normalized = normalized.substring(0, 1000);
            parts.add(label + "：" + normalized);
        }
    }
}
