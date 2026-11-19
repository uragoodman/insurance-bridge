package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductRecommendationRequestDTO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonProductVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonRequestDTO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonResponseVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationCandidateVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationCriteriaCatalogVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationCriteriaVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationFactVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationOptionVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationResponseVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationUnresolvedCriteriaVO;
import com.itheima.bolee.insurance.pojo.Category;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationAttributeRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationMultiValueRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOperatorRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationOptionRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductFactRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationProductRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationRuleRow;
import com.itheima.bolee.insurance.pojo.ai.AiRecommendationSnapshotRow;
import com.itheima.bolee.insurance.repository.ai.AiProductCatalogRepository;
import com.itheima.bolee.insurance.repository.ai.AiRecommendationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AiProductRecommendationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiProductRecommendationService.class);
    private static final int MAX_CRITERIA = 10;
    private static final int MAX_VALUES_PER_CRITERION = 20;
    private static final String ENGINE_VERSION = "recommendation-engine-v1";
    private static final Set<String> SUPPORTED_OPERATORS = new HashSet<String>(
            Arrays.asList("EQ", "IN", "LTE", "GTE", "RANGE"));

    private final AiRecommendationRepository recommendationRepository;
    private final AiProductCatalogRepository catalogRepository;

    public AiProductRecommendationService(
            AiRecommendationRepository recommendationRepository,
            AiProductCatalogRepository catalogRepository) {
        this.recommendationRepository = recommendationRepository;
        this.catalogRepository = catalogRepository;
    }

    public AiRecommendationCriteriaCatalogVO findCriteria(String rawCategoryId) {
        Long categoryId = parseCategoryId(rawCategoryId);
        requireActiveCategory(categoryId);
        LocalDateTime now = LocalDateTime.now();
        AiRecommendationSnapshotRow snapshot = recommendationRepository.findCurrentSnapshot(categoryId, now);
        if (snapshot == null) {
            String status = recommendationRepository.hasSnapshotPointer(categoryId) ? "NOT_READY" : "NOT_PUBLISHED";
            return criteriaCatalog(status, categoryId, null, null, Collections.<AiRecommendationCriteriaVO>emptyList());
        }
        assertSupportedSnapshot(snapshot);

        List<AiRecommendationAttributeRow> attributes = recommendationRepository.findSnapshotAttributes(snapshot.getId());
        List<AiRecommendationRuleRow> ruleRows = recommendationRepository.findSnapshotRules(snapshot.getId());
        if (attributes.isEmpty() || ruleRows.isEmpty()) {
            return criteriaCatalog("NOT_READY", categoryId, snapshot.getCatalogVersion(), snapshot.getRuleSetVersion(),
                    Collections.<AiRecommendationCriteriaVO>emptyList());
        }
        assertSnapshotAttributesUsable(attributes, snapshot.getCategoryId(), now);
        Map<Long, AiRecommendationRuleRow> rules = indexRules(
                ruleRows);
        Map<Long, List<String>> operators = indexOperators(
                recommendationRepository.findSnapshotOperators(snapshot.getId()));
        List<AiRecommendationOptionRow> optionRows = recommendationRepository.findSnapshotOptions(snapshot.getId());
        assertSnapshotOptionsUsable(optionRows);
        Map<Long, List<AiRecommendationOptionVO>> options = indexOptions(optionRows);
        assertConsistentSnapshot(attributes, rules.values(), snapshot.getCategoryId());
        assertExecutableDefinitions(attributes, rules, operators, options);
        List<AiRecommendationCriteriaVO> result = new ArrayList<AiRecommendationCriteriaVO>();
        for (AiRecommendationAttributeRow attribute : attributes) {
            AiRecommendationRuleRow rule = rules.get(attribute.getId());
            if (rule == null) continue;
            List<String> allowedUses = allowedUses(attribute, rule);
            if (allowedUses.isEmpty()) continue;
            List<String> supportedOperators = "COMPARE_ONLY".equals(rule.getRuleMode())
                    ? Collections.<String>emptyList()
                    : operators.getOrDefault(attribute.getId(), Collections.<String>emptyList()).stream()
                            .filter(SUPPORTED_OPERATORS::contains)
                            .filter(operator -> isSupportedTypeAndOperator(attribute.getValueType(), operator))
                            .collect(Collectors.toList());
            if (("ENUM".equals(attribute.getValueType()) || "MULTI_ENUM".equals(attribute.getValueType()))
                    && options.getOrDefault(attribute.getId(), Collections.<AiRecommendationOptionVO>emptyList()).isEmpty()) {
                throw serverError("Published enum attribute has no options");
            }
            result.add(new AiRecommendationCriteriaVO(
                    attribute.getAttributeCode(),
                    attribute.getDefinitionVersion(),
                    attribute.getDisplayName(),
                    attribute.getValueType(),
                    attribute.getUnitCode(),
                    supportedOperators,
                    allowedUses,
                    rule.getRuleMode(),
                    Boolean.TRUE.equals(rule.getRequiredForClarification()),
                    options.getOrDefault(attribute.getId(), Collections.<AiRecommendationOptionVO>emptyList())));
        }
        String catalogStatus = result.isEmpty() ? "NOT_READY" : "READY";
        return criteriaCatalog(catalogStatus, categoryId, snapshot.getCatalogVersion(), snapshot.getRuleSetVersion(), result);
    }

    public AiRecommendationResponseVO recommend(AiProductRecommendationRequestDTO request) {
        validateRequest(request);
        Long categoryId = parseCategoryId(request.getCriteria().getCategoryId());
        Category category = requireActiveCategory(categoryId);
        LocalDateTime now = LocalDateTime.now();
        AiRecommendationSnapshotRow snapshot = recommendationRepository.findCurrentSnapshot(categoryId, now);
        if (snapshot == null) {
            return response(request.getRequestId(), "CATALOG_NOT_READY", "COMPLETE",
                    Collections.<String>emptyList(), Collections.<String>emptyList(),
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList(), 0,
                    Collections.<AiRecommendationCandidateVO>emptyList(), null, null);
        }
        assertSupportedSnapshot(snapshot);

        List<AiRecommendationAttributeRow> attributes = recommendationRepository.findSnapshotAttributes(snapshot.getId());
        List<AiRecommendationRuleRow> ruleRows = recommendationRepository.findSnapshotRules(snapshot.getId());
        if (attributes.isEmpty() || ruleRows.isEmpty()) {
            return response(request.getRequestId(), "CATALOG_NOT_READY", "COMPLETE",
                    Collections.<String>emptyList(), Collections.<String>emptyList(),
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList(), 0,
                    Collections.<AiRecommendationCandidateVO>emptyList(), snapshot.getCatalogVersion(),
                    snapshot.getRuleSetVersion());
        }
        assertSnapshotAttributesUsable(attributes, snapshot.getCategoryId(), now);
        assertConsistentSnapshot(attributes, ruleRows, snapshot.getCategoryId());

        Map<String, AiRecommendationAttributeRow> attributesByCode = new HashMap<String, AiRecommendationAttributeRow>();
        for (AiRecommendationAttributeRow attribute : attributes) {
            attributesByCode.put(attribute.getAttributeCode(), attribute);
        }
        if (attributesByCode.size() != attributes.size()) {
            throw serverError("Published recommendation snapshot contains duplicate attribute codes");
        }
        Map<Long, AiRecommendationRuleRow> rulesByDefinition = indexRules(ruleRows);
        Map<Long, List<String>> operatorsByDefinition = indexOperators(
                recommendationRepository.findSnapshotOperators(snapshot.getId()));
        List<AiRecommendationOptionRow> optionRows = recommendationRepository.findSnapshotOptions(snapshot.getId());
        assertSnapshotOptionsUsable(optionRows);
        Map<Long, List<AiRecommendationOptionVO>> optionsByDefinition = indexOptions(optionRows);
        assertExecutableDefinitions(attributes, rulesByDefinition, operatorsByDefinition, optionsByDefinition);

        List<AiProductRecommendationRequestDTO.Item> items = request.getCriteria().getItems();
        Map<String, AiProductRecommendationRequestDTO.Item> itemsByCode = new LinkedHashMap<String, AiProductRecommendationRequestDTO.Item>();
        List<String> unsupported = validateCriteriaItems(
                items, attributesByCode, rulesByDefinition, operatorsByDefinition, optionsByDefinition, itemsByCode);
        if (!unsupported.isEmpty()) {
            return response(request.getRequestId(), "UNSUPPORTED_CRITERIA", "COMPLETE",
                    Collections.<String>emptyList(), unsupported,
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList(), 0,
                    Collections.<AiRecommendationCandidateVO>emptyList(), snapshot.getCatalogVersion(),
                    snapshot.getRuleSetVersion());
        }

        List<String> missing = requiredClarifications(attributes, rulesByDefinition, itemsByCode);
        if (items.isEmpty() && missing.isEmpty()) {
            missing.add("CUSTOMER_REQUIREMENT");
        }
        if (!missing.isEmpty()) {
            return response(request.getRequestId(), "NEEDS_CLARIFICATION", "COMPLETE", missing,
                    Collections.<String>emptyList(), Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList(),
                    0, Collections.<AiRecommendationCandidateVO>emptyList(), snapshot.getCatalogVersion(),
                    snapshot.getRuleSetVersion());
        }

        List<AiRecommendationProductRow> products = recommendationRepository.findSnapshotProducts(snapshot.getId());
        assertSnapshotProductsUsable(products, snapshot.getCategoryId(), now);
        if (products.isEmpty()) {
            return response(request.getRequestId(), "NO_MATCH", "COMPLETE", Collections.<String>emptyList(),
                    Collections.<String>emptyList(), Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList(),
                    0, Collections.<AiRecommendationCandidateVO>emptyList(), snapshot.getCatalogVersion(),
                    snapshot.getRuleSetVersion());
        }

        List<Long> profileIds = products.stream().map(AiRecommendationProductRow::getProfileVersionId)
                .collect(Collectors.toList());
        List<AiRecommendationProductFactRow> factRows = recommendationRepository.findSnapshotProductFacts(
                snapshot.getId(), profileIds, now);
        List<AiRecommendationMultiValueRow> multiValueRows = recommendationRepository.findSnapshotMultiValues(profileIds);
        Map<Long, Set<String>> multiValuesByFact = new HashMap<Long, Set<String>>();
        for (AiRecommendationMultiValueRow row : multiValueRows) {
            multiValuesByFact.computeIfAbsent(row.getAttributeValueId(), key -> new LinkedHashSet<String>())
                    .add(row.getValueCode());
        }
        Map<Long, Map<Long, AiRecommendationProductFactRow>> factsByProfile = new HashMap<Long, Map<Long, AiRecommendationProductFactRow>>();
        for (AiRecommendationProductFactRow fact : factRows) {
            factsByProfile.computeIfAbsent(fact.getProfileVersionId(), key -> new HashMap<Long, AiRecommendationProductFactRow>())
                    .put(fact.getDefinitionId(), fact);
        }

        Map<String, Integer> unresolvedCounts = new LinkedHashMap<String, Integer>();
        List<ScoredCandidate> verified = new ArrayList<ScoredCandidate>();
        int excludedProductCount = 0;
        for (AiRecommendationProductRow product : products) {
            Map<Long, AiRecommendationProductFactRow> facts = factsByProfile.getOrDefault(
                    product.getProfileVersionId(), Collections.<Long, AiRecommendationProductFactRow>emptyMap());
            List<String> unresolvedForProduct = new ArrayList<String>();
            boolean rejectedByHardFilter = false;

            for (AiProductRecommendationRequestDTO.Item item : items) {
                AiRecommendationAttributeRow attribute = attributesByCode.get(item.getCode());
                AiRecommendationRuleRow rule = rulesByDefinition.get(attribute.getId());
                if (!"HARD_FILTER".equals(rule.getRuleMode())) {
                    continue;
                }
                AiRecommendationProductFactRow fact = facts.get(attribute.getId());
                if (isAllowedNotApplicable(fact, rule)) {
                    rejectedByHardFilter = true;
                    break;
                } else if (!isKnown(fact, attribute, multiValuesByFact)) {
                    unresolvedForProduct.add(item.getCode());
                } else if (!matches(item, attribute, fact, multiValuesByFact)) {
                    rejectedByHardFilter = true;
                    break;
                }
            }
            if (rejectedByHardFilter) {
                continue;
            }

            int score = 0;
            List<String> reasons = new ArrayList<String>();
            for (AiProductRecommendationRequestDTO.Item item : items) {
                AiRecommendationAttributeRow attribute = attributesByCode.get(item.getCode());
                AiRecommendationRuleRow rule = rulesByDefinition.get(attribute.getId());
                AiRecommendationProductFactRow fact = facts.get(attribute.getId());
                if (isAllowedNotApplicable(fact, rule)) {
                    continue;
                }
                if (!isKnown(fact, attribute, multiValuesByFact)) {
                    if (!unresolvedForProduct.contains(item.getCode())) {
                        unresolvedForProduct.add(item.getCode());
                    }
                    continue;
                }
                if (matches(item, attribute, fact, multiValuesByFact)) {
                    if ("PREFERENCE_SCORE".equals(rule.getRuleMode())) {
                        score += Math.max(0, rule.getScoreWeight() == null ? 0 : rule.getScoreWeight());
                    }
                    if (StringUtils.hasText(rule.getReasonMessage())) {
                        reasons.add(rule.getReasonMessage());
                    }
                }
            }

            boolean hasUnresolvedHardFilter = unresolvedForProduct.stream().anyMatch(code -> {
                AiRecommendationAttributeRow attribute = attributesByCode.get(code);
                AiRecommendationRuleRow rule = attribute == null ? null : rulesByDefinition.get(attribute.getId());
                return rule != null && "HARD_FILTER".equals(rule.getRuleMode());
            });
            if (hasUnresolvedHardFilter) {
                excludedProductCount++;
                for (String code : unresolvedForProduct) {
                    AiRecommendationAttributeRow attribute = attributesByCode.get(code);
                    AiRecommendationRuleRow rule = attribute == null ? null : rulesByDefinition.get(attribute.getId());
                    if (rule != null && "HARD_FILTER".equals(rule.getRuleMode())) {
                        unresolvedCounts.put(code, unresolvedCounts.getOrDefault(code, 0) + 1);
                    }
                }
                continue;
            }
            List<AiRecommendationFactVO> compareFacts = buildCompareFacts(
                    attributes, rulesByDefinition, facts, multiValuesByFact,
                    optionsByDefinition, unresolvedForProduct);
            for (String code : unresolvedForProduct) {
                unresolvedCounts.put(code, unresolvedCounts.getOrDefault(code, 0) + 1);
            }
            verified.add(new ScoredCandidate(product, score, reasons, compareFacts));
        }

        verified.sort(Comparator.comparingInt(ScoredCandidate::getScore).reversed()
                .thenComparing(candidate -> candidate.getProduct().getSortNo(), Comparator.nullsLast(Integer::compareTo))
                .thenComparing(candidate -> candidate.getProduct().getProductId()));
        int limit = request.getLimit() == null ? 3 : request.getLimit();
        List<AiRecommendationCandidateVO> candidates = verified.stream().limit(limit)
                .map(candidate -> new AiRecommendationCandidateVO(
                        String.valueOf(candidate.getProduct().getProductId()),
                        candidate.getProduct().getProductName(),
                        String.valueOf(category.getId()),
                        candidate.getProduct().getProductVersion(),
                        "VERIFIED_MATCH",
                        candidate.getFacts(),
                        candidate.getReasons()))
                .collect(Collectors.toList());

        List<AiRecommendationUnresolvedCriteriaVO> unresolved = unresolvedCounts.entrySet().stream()
                .map(entry -> new AiRecommendationUnresolvedCriteriaVO(
                        entry.getKey(), entry.getValue(), "PRODUCT_FACT_NOT_AVAILABLE"))
                .collect(Collectors.toList());
        boolean partial = !unresolvedCounts.isEmpty();
        String status = partial ? "CATALOG_INCOMPLETE" : (candidates.isEmpty() ? "NO_MATCH" : "MATCHED");
        return response(request.getRequestId(), status, partial ? "PARTIAL" : "COMPLETE",
                Collections.<String>emptyList(), Collections.<String>emptyList(), unresolved,
                excludedProductCount, candidates, snapshot.getCatalogVersion(), snapshot.getRuleSetVersion());
    }

    public AiProductComparisonResponseVO compareProducts(AiProductComparisonRequestDTO request) {
        List<Long> productIds = validateComparisonRequest(request);
        Long categoryId = parseCategoryId(request.getCategoryId());
        requireActiveCategory(categoryId);
        LocalDateTime now = LocalDateTime.now();
        AiRecommendationSnapshotRow snapshot = recommendationRepository.findCurrentSnapshot(categoryId, now);
        if (snapshot == null) {
            return comparisonResponse(request.getRequestId(), "CATALOG_NOT_READY", categoryId,
                    null, null, Collections.<AiProductComparisonProductVO>emptyList(),
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList());
        }
        assertSupportedSnapshot(snapshot);

        List<AiRecommendationAttributeRow> attributes =
                recommendationRepository.findSnapshotAttributes(snapshot.getId());
        List<AiRecommendationRuleRow> ruleRows = recommendationRepository.findSnapshotRules(snapshot.getId());
        if (attributes.isEmpty() || ruleRows.isEmpty()) {
            return comparisonResponse(request.getRequestId(), "CATALOG_NOT_READY", categoryId,
                    snapshot.getCatalogVersion(), snapshot.getRuleSetVersion(),
                    Collections.<AiProductComparisonProductVO>emptyList(),
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList());
        }
        assertSnapshotAttributesUsable(attributes, snapshot.getCategoryId(), now);
        assertConsistentSnapshot(attributes, ruleRows, snapshot.getCategoryId());

        Map<Long, AiRecommendationRuleRow> rules = indexRules(ruleRows);
        Map<Long, List<String>> operators = indexOperators(
                recommendationRepository.findSnapshotOperators(snapshot.getId()));
        List<AiRecommendationOptionRow> optionRows = recommendationRepository.findSnapshotOptions(snapshot.getId());
        assertSnapshotOptionsUsable(optionRows);
        Map<Long, List<AiRecommendationOptionVO>> options = indexOptions(optionRows);
        assertExecutableDefinitions(attributes, rules, operators, options);

        List<AiRecommendationAttributeRow> comparableAttributes = attributes.stream()
                .filter(attribute -> Boolean.TRUE.equals(attribute.getAllowCompare()))
                .collect(Collectors.toList());
        if (comparableAttributes.isEmpty()) {
            return comparisonResponse(request.getRequestId(), "CATALOG_NOT_READY", categoryId,
                    snapshot.getCatalogVersion(), snapshot.getRuleSetVersion(),
                    Collections.<AiProductComparisonProductVO>emptyList(),
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList());
        }

        List<AiRecommendationProductRow> products = recommendationRepository.findSnapshotProductsByIds(
                snapshot.getId(), productIds);
        Set<Long> resolvedProductIds = products.stream()
                .map(AiRecommendationProductRow::getProductId)
                .collect(Collectors.toSet());
        if (products.size() != productIds.size() || resolvedProductIds.size() != products.size()
                || !resolvedProductIds.equals(new HashSet<Long>(productIds))) {
            throw badRequest("One or more products are not available in the current published category");
        }
        Map<Long, Integer> requestedOrder = new HashMap<Long, Integer>();
        for (int index = 0; index < productIds.size(); index++) {
            requestedOrder.put(productIds.get(index), index);
        }
        products.sort(Comparator.comparingInt(product -> requestedOrder.get(product.getProductId())));
        assertSnapshotProductsUsable(products, categoryId, now);

        List<Long> profileIds = products.stream().map(AiRecommendationProductRow::getProfileVersionId)
                .collect(Collectors.toList());
        List<AiRecommendationProductFactRow> factRows = recommendationRepository.findSnapshotProductFacts(
                snapshot.getId(), profileIds, now);
        List<AiRecommendationMultiValueRow> multiValueRows =
                recommendationRepository.findSnapshotMultiValues(profileIds);
        Map<Long, Set<String>> multiValuesByFact = new HashMap<Long, Set<String>>();
        for (AiRecommendationMultiValueRow row : multiValueRows) {
            multiValuesByFact.computeIfAbsent(row.getAttributeValueId(), key -> new LinkedHashSet<String>())
                    .add(row.getValueCode());
        }
        Map<Long, Map<Long, AiRecommendationProductFactRow>> factsByProfile =
                new HashMap<Long, Map<Long, AiRecommendationProductFactRow>>();
        for (AiRecommendationProductFactRow fact : factRows) {
            factsByProfile.computeIfAbsent(fact.getProfileVersionId(),
                            key -> new HashMap<Long, AiRecommendationProductFactRow>())
                    .put(fact.getDefinitionId(), fact);
        }

        Map<String, Integer> unresolvedCounts = new LinkedHashMap<String, Integer>();
        List<AiProductComparisonProductVO> comparedProducts = new ArrayList<AiProductComparisonProductVO>();
        for (AiRecommendationProductRow product : products) {
            List<String> unresolvedForProduct = new ArrayList<String>();
            List<AiRecommendationFactVO> facts = buildCompareFacts(
                    comparableAttributes,
                    rules,
                    factsByProfile.getOrDefault(product.getProfileVersionId(),
                            Collections.<Long, AiRecommendationProductFactRow>emptyMap()),
                    multiValuesByFact,
                    options,
                    unresolvedForProduct);
            for (String code : unresolvedForProduct) {
                unresolvedCounts.put(code, unresolvedCounts.getOrDefault(code, 0) + 1);
            }
            comparedProducts.add(new AiProductComparisonProductVO(
                    String.valueOf(product.getProductId()),
                    product.getProductName(),
                    product.getProductVersion(),
                    facts));
        }

        List<AiRecommendationUnresolvedCriteriaVO> unresolved = unresolvedCounts.entrySet().stream()
                .map(entry -> new AiRecommendationUnresolvedCriteriaVO(
                        entry.getKey(), entry.getValue(), "PRODUCT_FACT_NOT_AVAILABLE"))
                .collect(Collectors.toList());
        return comparisonResponse(request.getRequestId(), unresolved.isEmpty() ? "COMPLETE" : "PARTIAL",
                categoryId, snapshot.getCatalogVersion(), snapshot.getRuleSetVersion(), comparedProducts, unresolved);
    }

    private List<Long> validateComparisonRequest(AiProductComparisonRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRequestId())
                || !isUuid(request.getRequestId()) || !StringUtils.hasText(request.getCategoryId())
                || request.getProductIds() == null || request.getProductIds().size() < 2
                || request.getProductIds().size() > 10) {
            throw badRequest("requestId, categoryId, and 2 to 10 productIds are required");
        }
        List<Long> productIds = new ArrayList<Long>(request.getProductIds().size());
        Set<Long> uniqueIds = new HashSet<Long>();
        for (String rawProductId : request.getProductIds()) {
            try {
                if (!StringUtils.hasText(rawProductId) || !rawProductId.matches("[0-9]{1,19}")) {
                    throw new NumberFormatException();
                }
                Long productId = Long.valueOf(rawProductId);
                if (productId <= 0 || !uniqueIds.add(productId)) {
                    throw new NumberFormatException();
                }
                productIds.add(productId);
            } catch (NumberFormatException exception) {
                throw badRequest("productIds must be unique positive numeric IDs");
            }
        }
        return productIds;
    }

    private AiProductComparisonResponseVO comparisonResponse(
            String requestId, String status, Long categoryId, String catalogVersion, Integer ruleVersion,
            List<AiProductComparisonProductVO> products,
            List<AiRecommendationUnresolvedCriteriaVO> unresolvedCriteria) {
        return new AiProductComparisonResponseVO(
                requestId, status, String.valueOf(categoryId), catalogVersion, ruleVersion,
                products, unresolvedCriteria, Instant.now());
    }

    private List<String> validateCriteriaItems(
            List<AiProductRecommendationRequestDTO.Item> items,
            Map<String, AiRecommendationAttributeRow> attributesByCode,
            Map<Long, AiRecommendationRuleRow> rulesByDefinition,
            Map<Long, List<String>> operatorsByDefinition,
            Map<Long, List<AiRecommendationOptionVO>> optionsByDefinition,
            Map<String, AiProductRecommendationRequestDTO.Item> itemsByCode) {
        if (items == null || items.size() > MAX_CRITERIA) {
            throw badRequest("At most " + MAX_CRITERIA + " criteria are allowed");
        }
        List<String> unsupported = new ArrayList<String>();
        for (AiProductRecommendationRequestDTO.Item item : items) {
            if (item == null || !StringUtils.hasText(item.getCode()) || !StringUtils.hasText(item.getOperator())
                    || !StringUtils.hasText(item.getCustomerIntent())) {
                throw badRequest("Each criterion requires code, operator, and customerIntent");
            }
            if (itemsByCode.put(item.getCode(), item) != null) {
                throw badRequest("A criterion code may only be supplied once");
            }
            AiRecommendationAttributeRow attribute = attributesByCode.get(item.getCode());
            if (attribute == null) {
                unsupported.add(item.getCode());
                continue;
            }
            AiRecommendationRuleRow rule = rulesByDefinition.get(attribute.getId());
            if (rule == null || !operatorsByDefinition.getOrDefault(attribute.getId(), Collections.<String>emptyList())
                    .contains(item.getOperator()) || !SUPPORTED_OPERATORS.contains(item.getOperator())) {
                unsupported.add(item.getCode());
                continue;
            }
            boolean intentSupported = ("MUST_HAVE".equals(item.getCustomerIntent())
                    && "HARD_FILTER".equals(rule.getRuleMode()) && Boolean.TRUE.equals(attribute.getAllowHardFilter()))
                    || ("PREFERENCE".equals(item.getCustomerIntent())
                    && "PREFERENCE_SCORE".equals(rule.getRuleMode()) && Boolean.TRUE.equals(attribute.getAllowPreference()));
            if (!intentSupported || !isSupportedTypeAndOperator(attribute.getValueType(), item.getOperator())) {
                unsupported.add(item.getCode());
                continue;
            }
            validateValueShape(item, attribute, optionsByDefinition.getOrDefault(
                    attribute.getId(), Collections.<AiRecommendationOptionVO>emptyList()));
        }
        return unsupported;
    }

    private void validateValueShape(AiProductRecommendationRequestDTO.Item item, AiRecommendationAttributeRow attribute,
                                    List<AiRecommendationOptionVO> options) {
        if ("EQ".equals(item.getOperator())) {
            if ("ENUM".equals(attribute.getValueType())) {
                requireOnly(item.getValueCode() != null, item, "valueCode");
                if (!StringUtils.hasText(item.getValueCode()) || item.getValueCode().length() > 80
                        || !hasOption(options, item.getValueCode())) {
                    throw badRequest("valueCode is not a published option");
                }
            } else {
                requireOnly(item.getNumberValue() != null, item, "numberValue");
                validateDecimal(item.getNumberValue());
            }
        } else if ("IN".equals(item.getOperator())) {
            List<String> codes = item.getValueCodes();
            if (codes == null || codes.isEmpty() || codes.size() > MAX_VALUES_PER_CRITERION
                    || codes.stream().anyMatch(code -> !StringUtils.hasText(code) || code.length() > 80)) {
                throw badRequest("IN requires between 1 and " + MAX_VALUES_PER_CRITERION + " valueCodes");
            }
            if (item.getValueCode() != null || item.getNumberValue() != null
                    || item.getMinValue() != null || item.getMaxValue() != null) {
                throw badRequest("Only valueCodes may be supplied for IN");
            }
            if (new HashSet<String>(codes).size() != codes.size()) {
                throw badRequest("IN valueCodes must not contain duplicates");
            }
            if (codes.stream().anyMatch(code -> !hasOption(options, code))) {
                throw badRequest("IN contains a valueCode that is not a published option");
            }
        } else if ("LTE".equals(item.getOperator()) || "GTE".equals(item.getOperator())) {
            requireOnly(item.getNumberValue() != null, item, "numberValue");
            validateDecimal(item.getNumberValue());
        } else if ("RANGE".equals(item.getOperator())) {
            if (item.getMinValue() == null || item.getMaxValue() == null
                    || item.getMinValue().compareTo(item.getMaxValue()) > 0) {
                throw badRequest("RANGE requires minValue less than or equal to maxValue");
            }
            requireNoOtherValues(item, "minValue", "maxValue");
            validateDecimal(item.getMinValue());
            validateDecimal(item.getMaxValue());
        }
        if ("NUMBER".equals(attribute.getValueType()) || "NUMBER_RANGE".equals(attribute.getValueType())) {
            if (!StringUtils.hasText(attribute.getUnitCode()) || !attribute.getUnitCode().equals(item.getUnit())) {
                throw badRequest("Criterion unit does not match the published attribute unit");
            }
        } else if (StringUtils.hasText(item.getUnit())) {
            throw badRequest("Unit is not accepted for this attribute type");
        }
    }

    private boolean matches(
            AiProductRecommendationRequestDTO.Item item,
            AiRecommendationAttributeRow attribute,
            AiRecommendationProductFactRow fact,
            Map<Long, Set<String>> multiValuesByFact) {
        String operator = item.getOperator();
        if ("EQ".equals(operator)) {
            if ("ENUM".equals(attribute.getValueType())) {
                return item.getValueCode().equals(fact.getValueCode());
            }
            return fact.getValueNumber() != null && fact.getValueNumber().compareTo(item.getNumberValue()) == 0;
        }
        if ("IN".equals(operator)) {
            if ("ENUM".equals(attribute.getValueType())) {
                return item.getValueCodes().contains(fact.getValueCode());
            }
            Set<String> values = multiValuesByFact.getOrDefault(fact.getId(), Collections.<String>emptySet());
            for (String requested : item.getValueCodes()) {
                if (values.contains(requested)) {
                    return true;
                }
            }
            return false;
        }
        if ("LTE".equals(operator)) {
            return fact.getValueNumber() != null && fact.getValueNumber().compareTo(item.getNumberValue()) <= 0;
        }
        if ("GTE".equals(operator)) {
            return fact.getValueNumber() != null && fact.getValueNumber().compareTo(item.getNumberValue()) >= 0;
        }
        if ("RANGE".equals(operator)) {
            if ("NUMBER_RANGE".equals(attribute.getValueType())) {
                return fact.getValueMin() != null && fact.getValueMax() != null
                        && fact.getValueMin().compareTo(item.getMinValue()) <= 0
                        && fact.getValueMax().compareTo(item.getMaxValue()) >= 0;
            }
            return fact.getValueNumber() != null
                    && fact.getValueNumber().compareTo(item.getMinValue()) >= 0
                    && fact.getValueNumber().compareTo(item.getMaxValue()) <= 0;
        }
        return false;
    }

    private List<AiRecommendationFactVO> buildCompareFacts(
            List<AiRecommendationAttributeRow> attributes,
            Map<Long, AiRecommendationRuleRow> rules,
            Map<Long, AiRecommendationProductFactRow> facts,
            Map<Long, Set<String>> multiValuesByFact,
            Map<Long, List<AiRecommendationOptionVO>> options,
            List<String> unresolvedForProduct) {
        List<AiRecommendationFactVO> result = new ArrayList<AiRecommendationFactVO>();
        for (AiRecommendationAttributeRow attribute : attributes) {
            if (!Boolean.TRUE.equals(attribute.getAllowCompare())) continue;
            AiRecommendationProductFactRow fact = facts.get(attribute.getId());
            AiRecommendationRuleRow rule = rules.get(attribute.getId());
            boolean known = isKnown(fact, attribute, multiValuesByFact);
            boolean notApplicable = isAllowedNotApplicable(fact, rule);
            String valueStatus = known ? "KNOWN" : (notApplicable ? "NOT_APPLICABLE" : "NOT_PROVIDED");
            if (!known && !notApplicable && !unresolvedForProduct.contains(attribute.getAttributeCode())) {
                unresolvedForProduct.add(attribute.getAttributeCode());
            }
            String valueCode = known && fact != null ? fact.getValueCode() : null;
            List<String> valueCodes = known && fact != null
                    ? new ArrayList<String>(multiValuesByFact.getOrDefault(fact.getId(), Collections.<String>emptySet()))
                    : Collections.<String>emptyList();
            String valueDisplay = null;
            if (known && "ENUM".equals(attribute.getValueType()) && valueCode != null) {
                valueDisplay = displayOptionValues(
                        options.getOrDefault(attribute.getId(), Collections.<AiRecommendationOptionVO>emptyList()),
                        Collections.singletonList(valueCode));
            } else if (known && "MULTI_ENUM".equals(attribute.getValueType())) {
                valueDisplay = displayOptionValues(
                        options.getOrDefault(attribute.getId(), Collections.<AiRecommendationOptionVO>emptyList()),
                        valueCodes);
            }
            result.add(new AiRecommendationFactVO(
                    attribute.getAttributeCode(), attribute.getDefinitionVersion(), attribute.getDisplayName(),
                    attribute.getValueType(), attribute.getUnitCode(), valueCode,
                    valueCodes, valueDisplay,
                    known ? fact.getValueNumber() : null,
                    known ? fact.getValueMin() : null,
                    known ? fact.getValueMax() : null,
                    valueStatus));
        }
        result.sort(Comparator.comparing(AiRecommendationFactVO::getCode));
        return result;
    }

    private String displayOptionValues(List<AiRecommendationOptionVO> options, List<String> valueCodes) {
        Map<String, String> displayNames = options.stream().collect(Collectors.toMap(
                AiRecommendationOptionVO::getValueCode,
                AiRecommendationOptionVO::getDisplayName,
                (first, ignored) -> first));
        return valueCodes.stream()
                .map(code -> displayNames.getOrDefault(code, code))
                .collect(Collectors.joining("、"));
    }

    private List<String> requiredClarifications(
            List<AiRecommendationAttributeRow> attributes,
            Map<Long, AiRecommendationRuleRow> rules,
            Map<String, AiProductRecommendationRequestDTO.Item> items) {
        List<String> missing = new ArrayList<String>();
        for (AiRecommendationAttributeRow attribute : attributes) {
            AiRecommendationRuleRow rule = rules.get(attribute.getId());
            if (rule != null && Boolean.TRUE.equals(rule.getRequiredForClarification())
                    && !items.containsKey(attribute.getAttributeCode())) {
                missing.add(attribute.getAttributeCode());
            }
        }
        return missing;
    }

    private List<String> allowedUses(
            AiRecommendationAttributeRow attribute, AiRecommendationRuleRow rule) {
        List<String> result = new ArrayList<String>();
        if ("HARD_FILTER".equals(rule.getRuleMode()) && Boolean.TRUE.equals(attribute.getAllowHardFilter())) {
            result.add("HARD_FILTER");
        }
        if ("PREFERENCE_SCORE".equals(rule.getRuleMode()) && Boolean.TRUE.equals(attribute.getAllowPreference())) {
            result.add("PREFERENCE_SCORE");
        }
        if ("COMPARE_ONLY".equals(rule.getRuleMode()) && Boolean.TRUE.equals(attribute.getAllowCompare())) {
            result.add("COMPARE_ONLY");
        }
        return result;
    }

    private void assertConsistentSnapshot(List<AiRecommendationAttributeRow> attributes,
                                          Iterable<AiRecommendationRuleRow> rules,
                                          Long snapshotCategoryId) {
        Map<Long, AiRecommendationAttributeRow> attributeIds = new HashMap<Long, AiRecommendationAttributeRow>();
        Set<String> codes = new HashSet<String>();
        for (AiRecommendationAttributeRow attribute : attributes) {
            if (!snapshotCategoryId.equals(attribute.getCategoryId())) {
                throw serverError("Published recommendation snapshot contains a cross-category attribute");
            }
            attributeIds.put(attribute.getId(), attribute);
            if (!codes.add(attribute.getAttributeCode())) {
                throw serverError("Published recommendation snapshot contains duplicate attribute codes");
            }
        }
        Set<Long> ruledDefinitionIds = new HashSet<Long>();
        for (AiRecommendationRuleRow rule : rules) {
            AiRecommendationAttributeRow attribute = attributeIds.get(rule.getDefinitionId());
            if (attribute == null) throw serverError("Published rule references an attribute outside its snapshot");
            if (!snapshotCategoryId.equals(rule.getDefinitionCategoryId())) {
                throw serverError("Published rule references an attribute outside its category");
            }
            ruledDefinitionIds.add(rule.getDefinitionId());
            boolean validMode = "HARD_FILTER".equals(rule.getRuleMode())
                    || "PREFERENCE_SCORE".equals(rule.getRuleMode())
                    || "COMPARE_ONLY".equals(rule.getRuleMode());
            boolean modeAllowed = ("HARD_FILTER".equals(rule.getRuleMode())
                    && Boolean.TRUE.equals(attribute.getAllowHardFilter()))
                    || ("PREFERENCE_SCORE".equals(rule.getRuleMode())
                    && Boolean.TRUE.equals(attribute.getAllowPreference()))
                    || ("COMPARE_ONLY".equals(rule.getRuleMode())
                    && Boolean.TRUE.equals(attribute.getAllowCompare()));
            if (!validMode || !modeAllowed) {
                throw serverError("Published recommendation rule is incompatible with its attribute definition");
            }
            int weight = rule.getScoreWeight() == null ? 0 : rule.getScoreWeight();
            if ("PREFERENCE_SCORE".equals(rule.getRuleMode()) ? weight < 1 || weight > 100 : weight != 0) {
                throw serverError("Published recommendation rule has an invalid score weight");
            }
            if (!"INCOMPLETE".equals(rule.getMissingPolicy())
                    && !"ALLOW_NOT_APPLICABLE".equals(rule.getMissingPolicy())) {
                throw serverError("Published recommendation rule has an unsupported missing-value policy");
            }
            if ("COMPARE_ONLY".equals(rule.getRuleMode())
                    && Boolean.TRUE.equals(rule.getRequiredForClarification())) {
                throw serverError("Compare-only attributes cannot be required criteria");
            }
        }
        if (ruledDefinitionIds.size() != attributeIds.size()) {
            throw serverError("Published recommendation snapshot contains an attribute without a rule");
        }
    }

    private void assertExecutableDefinitions(
            List<AiRecommendationAttributeRow> attributes,
            Map<Long, AiRecommendationRuleRow> rules,
            Map<Long, List<String>> operators,
            Map<Long, List<AiRecommendationOptionVO>> options) {
        for (AiRecommendationAttributeRow attribute : attributes) {
            AiRecommendationRuleRow rule = rules.get(attribute.getId());
            if (rule == null || "COMPARE_ONLY".equals(rule.getRuleMode())) continue;
            boolean hasSupportedOperator = operators.getOrDefault(attribute.getId(), Collections.<String>emptyList())
                    .stream().anyMatch(operator -> SUPPORTED_OPERATORS.contains(operator)
                            && isSupportedTypeAndOperator(attribute.getValueType(), operator));
            if (!hasSupportedOperator) throw serverError("Published recommendation attribute has no supported operator");
            if (("ENUM".equals(attribute.getValueType()) || "MULTI_ENUM".equals(attribute.getValueType()))
                    && options.getOrDefault(attribute.getId(), Collections.<AiRecommendationOptionVO>emptyList()).isEmpty()) {
                throw serverError("Published enum attribute has no options");
            }
            if (("NUMBER".equals(attribute.getValueType()) || "NUMBER_RANGE".equals(attribute.getValueType()))
                    && !StringUtils.hasText(attribute.getUnitCode())) {
                throw serverError("Published numeric attribute has no canonical unit");
            }
        }
    }

    private void assertSupportedSnapshot(AiRecommendationSnapshotRow snapshot) {
        if (!"SUM".equals(snapshot.getScoreStrategy())
                || snapshot.getCategoryId() == null
                || !snapshot.getCategoryId().equals(snapshot.getRuleSetCategoryId())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Published recommendation snapshot is not usable");
        }
    }

    private void assertSnapshotAttributesUsable(
            List<AiRecommendationAttributeRow> attributes, Long categoryId, LocalDateTime now) {
        for (AiRecommendationAttributeRow attribute : attributes) {
            boolean active = "PUBLISHED".equals(attribute.getStatus())
                    && Boolean.TRUE.equals(attribute.getAiEnabled())
                    && "NONE".equals(attribute.getSensitivityLevel())
                    && "0".equals(attribute.getDataState())
                    && (attribute.getEffectiveFrom() == null || !attribute.getEffectiveFrom().isAfter(now))
                    && (attribute.getEffectiveTo() == null || attribute.getEffectiveTo().isAfter(now));
            if (!categoryId.equals(attribute.getCategoryId()) || !active) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Published recommendation catalog is no longer usable");
            }
        }
    }

    private void assertSnapshotOptionsUsable(List<AiRecommendationOptionRow> options) {
        if (options.stream().anyMatch(option -> !"0".equals(option.getDataState()))) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Published recommendation catalog is no longer usable");
        }
    }

    private void assertSnapshotProductsUsable(
            List<AiRecommendationProductRow> products, Long categoryId, LocalDateTime now) {
        for (AiRecommendationProductRow product : products) {
            boolean active = "PUBLISHED".equals(product.getProfileStatus())
                    && "0".equals(product.getProfileDataState())
                    && (product.getProfileEffectiveFrom() == null || !product.getProfileEffectiveFrom().isAfter(now))
                    && (product.getProfileEffectiveTo() == null || product.getProfileEffectiveTo().isAfter(now));
            if (!categoryId.equals(product.getProfileCategoryId())
                    || !categoryId.equals(product.getActualCategoryId()) || !active) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Published recommendation catalog is no longer usable");
            }
        }
    }

    private Map<Long, AiRecommendationRuleRow> indexRules(List<AiRecommendationRuleRow> rules) {
        Map<Long, AiRecommendationRuleRow> result = new HashMap<Long, AiRecommendationRuleRow>();
        for (AiRecommendationRuleRow rule : rules) result.put(rule.getDefinitionId(), rule);
        return result;
    }

    private Map<Long, List<String>> indexOperators(List<AiRecommendationOperatorRow> operators) {
        Map<Long, List<String>> result = new HashMap<Long, List<String>>();
        for (AiRecommendationOperatorRow row : operators) {
            result.computeIfAbsent(row.getDefinitionId(), key -> new ArrayList<String>()).add(row.getOperatorCode());
        }
        return result;
    }

    private Map<Long, List<AiRecommendationOptionVO>> indexOptions(List<AiRecommendationOptionRow> options) {
        Map<Long, List<AiRecommendationOptionVO>> result = new HashMap<Long, List<AiRecommendationOptionVO>>();
        for (AiRecommendationOptionRow row : options) {
            result.computeIfAbsent(row.getDefinitionId(), key -> new ArrayList<AiRecommendationOptionVO>())
                    .add(new AiRecommendationOptionVO(row.getValueCode(), row.getDisplayName()));
        }
        return result;
    }

    private boolean hasOption(List<AiRecommendationOptionVO> options, String code) {
        return options.stream().anyMatch(option -> option.getValueCode().equals(code));
    }

    private boolean isKnown(AiRecommendationProductFactRow fact, AiRecommendationAttributeRow attribute,
                            Map<Long, Set<String>> multiValuesByFact) {
        if (fact == null || !"KNOWN".equals(fact.getValueStatus())) return false;
        if ("MULTI_ENUM".equals(attribute.getValueType())) {
            return !multiValuesByFact.getOrDefault(fact.getId(), Collections.<String>emptySet()).isEmpty();
        }
        if ("ENUM".equals(attribute.getValueType())) return StringUtils.hasText(fact.getValueCode());
        if ("NUMBER".equals(attribute.getValueType())) return fact.getValueNumber() != null;
        if ("NUMBER_RANGE".equals(attribute.getValueType())) {
            return fact.getValueMin() != null && fact.getValueMax() != null
                    && fact.getValueMin().compareTo(fact.getValueMax()) <= 0;
        }
        return false;
    }

    private boolean isAllowedNotApplicable(AiRecommendationProductFactRow fact, AiRecommendationRuleRow rule) {
        return fact != null && "NOT_APPLICABLE".equals(fact.getValueStatus())
                && "ALLOW_NOT_APPLICABLE".equals(rule.getMissingPolicy());
    }

    private boolean isSupportedTypeAndOperator(String type, String operator) {
        if ("ENUM".equals(type)) return "EQ".equals(operator) || "IN".equals(operator);
        if ("MULTI_ENUM".equals(type)) return "IN".equals(operator);
        if ("NUMBER".equals(type)) return "EQ".equals(operator) || "LTE".equals(operator)
                || "GTE".equals(operator) || "RANGE".equals(operator);
        if ("NUMBER_RANGE".equals(type)) return "RANGE".equals(operator);
        return false;
    }

    private void validateRequest(AiProductRecommendationRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRequestId()) || request.getCriteria() == null
                || !StringUtils.hasText(request.getCriteria().getCategoryId()) || request.getCriteria().getItems() == null) {
            throw badRequest("requestId, criteria.categoryId, and criteria.items are required");
        }
        if (!isUuid(request.getRequestId()) || (StringUtils.hasText(request.getTurnId()) && !isUuid(request.getTurnId()))) {
            throw badRequest("requestId and turnId must be UUIDs");
        }
        int limit = request.getLimit() == null ? 3 : request.getLimit();
        if (limit < 1 || limit > 3) throw badRequest("limit must be between 1 and 3");
    }

    private void requireOnly(boolean expectedValuePresent, AiProductRecommendationRequestDTO.Item item, String expected) {
        if (!expectedValuePresent) throw badRequest(expected + " is required for this operator");
        if ("valueCode".equals(expected)) {
            if (item.getValueCodes() != null || item.getNumberValue() != null || item.getMinValue() != null || item.getMaxValue() != null) {
                throw badRequest("Only valueCode may be supplied for this operator");
            }
        } else if (item.getValueCode() != null || item.getValueCodes() != null || item.getMinValue() != null || item.getMaxValue() != null) {
            throw badRequest("Only numberValue may be supplied for this operator");
        }
    }

    private void requireNoOtherValues(AiProductRecommendationRequestDTO.Item item, String first, String second) {
        if (item.getValueCode() != null || item.getValueCodes() != null || item.getNumberValue() != null) {
            throw badRequest("Only " + first + " and " + second + " may be supplied for RANGE");
        }
    }

    private void validateDecimal(BigDecimal value) {
        if (value.precision() > 20 || Math.max(0, value.scale()) > 6) {
            throw badRequest("Numeric criterion exceeds the published precision limit");
        }
    }

    private Long parseCategoryId(String value) {
        try {
            if (!StringUtils.hasText(value) || !value.matches("[0-9]{1,19}")) throw new NumberFormatException();
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            throw badRequest("Invalid product category id");
        }
    }

    private Category requireActiveCategory(Long categoryId) {
        Category category = catalogRepository.findActiveLeafProductCategory(categoryId);
        if (category == null) throw badRequest("Unknown or unavailable product category");
        return category;
    }

    private AiRecommendationResponseVO response(
            String requestId, String status, String completeness, List<String> missing,
            List<String> unsupported, List<AiRecommendationUnresolvedCriteriaVO> unresolved,
            int excludedCount, List<AiRecommendationCandidateVO> candidates,
            String catalogVersion, Integer ruleVersion) {
        LOGGER.info("AI product recommendation evaluated: requestId={}, status={}, completeness={}, catalogVersion={}, "
                        + "ruleVersion={}, candidateCount={}, excludedProductCount={}",
                requestId, status, completeness, catalogVersion, ruleVersion, candidates.size(), excludedCount);
        return new AiRecommendationResponseVO(requestId, status, completeness, missing, unsupported,
                unresolved, excludedCount, candidates, Instant.now(), catalogVersion, ruleVersion, ENGINE_VERSION);
    }

    private AiRecommendationCriteriaCatalogVO criteriaCatalog(
            String status, Long categoryId, String catalogVersion, Integer ruleVersion,
            List<AiRecommendationCriteriaVO> criteria) {
        return new AiRecommendationCriteriaCatalogVO(
                status, String.valueOf(categoryId), catalogVersion, ruleVersion, criteria);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException serverError(String message) {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    private boolean isUuid(String value) {
        try {
            return value != null && UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static class ScoredCandidate {
        private final AiRecommendationProductRow product;
        private final int score;
        private final List<String> reasons;
        private final List<AiRecommendationFactVO> facts;

        private ScoredCandidate(AiRecommendationProductRow product, int score, List<String> reasons,
                                List<AiRecommendationFactVO> facts) {
            this.product = product;
            this.score = score;
            this.reasons = reasons;
            this.facts = facts;
        }

        private AiRecommendationProductRow getProduct() { return product; }
        private int getScore() { return score; }
        private List<String> getReasons() { return reasons; }
        private List<AiRecommendationFactVO> getFacts() { return facts; }
    }
}
