package com.itheima.bolee.insurance.service.ai;

import com.itheima.bolee.framework.commons.enums.basic.BaseEnum;
import com.itheima.bolee.framework.commons.exception.ProjectException;
import com.itheima.bolee.insurance.dto.ai.AgnoChatResponse;
import com.itheima.bolee.insurance.dto.ai.AgnoOwnPolicySummaryDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoOwnPolicyDetailDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoOwnPolicySummaryItemDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoProductCandidateDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoProductComparisonDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoProductComparisonProductDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoRecommendationFactDTO;
import com.itheima.bolee.insurance.dto.ai.AgnoRecommendationUnresolvedCriterionDTO;
import com.itheima.bolee.insurance.dto.ai.AiAssistantChatResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiProductCandidateVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyChatItemVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyChatSummaryResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyChatDetailResponseVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationFactVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonProductVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonResponseVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationUnresolvedCriteriaVO;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Maps the private Python response into the customer-facing Java DTO. */
public final class AgnoChatResponseMapper {

    private static final int MAX_RECOMMENDATIONS = 3;

    private AgnoChatResponseMapper() {
    }

    public static AiAssistantChatResponseVO toCustomerResponse(
            String clientSessionId,
            AgnoChatResponse response) {
        if (response == null || !StringUtils.hasText(response.getAnswer())
                || response.getAnswer().length() > 12000) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        validateEnvelope(response);
        AiProductComparisonResponseVO comparison = mapComparison(response.getProductComparison());
        AiOwnPolicyChatSummaryResponseVO ownPolicySummary = mapOwnPolicy(response.getOwnPolicySummary());
        AiOwnPolicyChatDetailResponseVO ownPolicyDetail = mapOwnPolicyDetail(response.getOwnPolicyDetail());
        if (ownPolicySummary != null && ownPolicyDetail != null) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }

        List<AgnoProductCandidateDTO> upstream = response.getRecommendations();
        if (upstream == null || upstream.isEmpty()) {
            AiAssistantChatResponseVO mapped = new AiAssistantChatResponseVO(
                    clientSessionId,
                    response.getAnswer(),
                    Collections.<AiProductCandidateVO>emptyList(),
                    response.getRecommendationStatus(),
                    response.getEvaluationCompleteness(),
                    safeList(response.getMissingCriteria()),
                    safeList(response.getUnsupportedCriteria()),
                    mapUnresolved(response.getUnresolvedCriteria()),
                    response.getExcludedProductCount() == null ? 0 : response.getExcludedProductCount(),
                    response.getCatalogVersion(),
                    response.getRuleVersion(),
                    comparison);
            mapped.setOwnPolicySummary(ownPolicySummary);
            mapped.setOwnPolicyDetail(ownPolicyDetail);
            return mapped;
        }
        if (upstream.size() > MAX_RECOMMENDATIONS) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }

        List<AiProductCandidateVO> recommendations = new ArrayList<AiProductCandidateVO>();
        Set<String> productIds = new HashSet<String>();
        for (AgnoProductCandidateDTO candidate : upstream) {
            if (candidate == null
                    || !StringUtils.hasText(candidate.getProductId())
                    || candidate.getProductId().length() > 64
                    || !StringUtils.hasText(candidate.getName())
                    || candidate.getName().length() > 255
                    || (candidate.getSummary() != null && candidate.getSummary().length() > 4000)
                    || !StringUtils.hasText(candidate.getCategoryId())
                    || !candidate.getCategoryId().matches("[0-9]{1,19}")
                    || !productIds.add(candidate.getProductId())) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            validateCandidate(response, candidate);
            recommendations.add(new AiProductCandidateVO(
                    candidate.getProductId(),
                    candidate.getName(),
                    candidate.getCategoryId(),
                    candidate.getCategoryCode(),
                    candidate.getCategoryName(),
                    candidate.getMatchReasons() == null
                            ? Collections.<String>emptyList()
                            : candidate.getMatchReasons(),
                    candidate.getProductVersion(),
                    candidate.getVerificationStatus(),
                    mapFacts(candidate.getFacts()),
                    candidate.getSummary()));
        }
        AiAssistantChatResponseVO mapped = new AiAssistantChatResponseVO(
                clientSessionId,
                response.getAnswer(),
                recommendations,
                response.getRecommendationStatus(),
                response.getEvaluationCompleteness(),
                safeList(response.getMissingCriteria()),
                safeList(response.getUnsupportedCriteria()),
                mapUnresolved(response.getUnresolvedCriteria()),
                response.getExcludedProductCount() == null ? 0 : response.getExcludedProductCount(),
                response.getCatalogVersion(),
                response.getRuleVersion(),
                comparison);
        mapped.setOwnPolicySummary(ownPolicySummary);
        mapped.setOwnPolicyDetail(ownPolicyDetail);
        return mapped;
    }

    private static AiOwnPolicyChatSummaryResponseVO mapOwnPolicy(AgnoOwnPolicySummaryDTO source) {
        if (source == null) return null;
        List<AgnoOwnPolicySummaryItemDTO> sourcePolicies = source.getPolicies();
        if (!Arrays.asList("FOUND", "NO_ACTIVE_POLICIES", "PROFILE_NOT_LINKED").contains(source.getStatus())
                || !StringUtils.hasText(source.getAsOf()) || source.getAsOf().length() > 40
                || source.getReturnedCount() == null || source.getReturnedCount() < 0
                || source.getReturnedCount() > 20 || source.getTruncated() == null
                || sourcePolicies == null || sourcePolicies.size() != source.getReturnedCount()
                || sourcePolicies.size() > 20
                || (source.getTruncated() && sourcePolicies.size() != 20)
                || ("FOUND".equals(source.getStatus()) && sourcePolicies.isEmpty())
                || (("NO_ACTIVE_POLICIES".equals(source.getStatus())
                || "PROFILE_NOT_LINKED".equals(source.getStatus())) && !sourcePolicies.isEmpty())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }

        List<AiOwnPolicyChatItemVO> policies = new ArrayList<AiOwnPolicyChatItemVO>(sourcePolicies.size());
        for (AgnoOwnPolicySummaryItemDTO item : sourcePolicies) {
            if (item == null || !StringUtils.hasText(item.getPolicyId())
                    || !item.getPolicyId().matches("[0-9]{1,19}")
                    || (item.getProductName() != null && item.getProductName().length() > 200)
                    || (item.getCoverageStartTime() != null && item.getCoverageStartTime().length() > 40)
                    || (item.getCoverageEndTime() != null && item.getCoverageEndTime().length() > 40)) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            String expectedLabel;
            if ("PENDING_PAYMENT".equals(item.getPolicyStatus())) expectedLabel = "待付款";
            else if ("PENDING_EFFECTIVE".equals(item.getPolicyStatus())) expectedLabel = "待生效";
            else if ("IN_FORCE".equals(item.getPolicyStatus())) expectedLabel = "保障中";
            else throw new ProjectException(BaseEnum.REMOTE_FAIL);
            if (!expectedLabel.equals(item.getPolicyStatusLabel())) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            policies.add(new AiOwnPolicyChatItemVO(
                    item.getProductName(), item.getPolicyStatus(), expectedLabel,
                    item.getCoverageStartTime(), item.getCoverageEndTime(),
                    item.getAutoExtensionEnabled()));
        }
        return new AiOwnPolicyChatSummaryResponseVO(
                source.getStatus(), source.getAsOf(), policies, source.getReturnedCount(), source.getTruncated());
    }

    private static AiOwnPolicyChatDetailResponseVO mapOwnPolicyDetail(AgnoOwnPolicyDetailDTO source) {
        if (source == null) return null;
        if (!StringUtils.hasText(source.getAsOf()) || source.getAsOf().length() > 40) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if ("NOT_FOUND".equals(source.getStatus())) {
            if (source.getPolicy() != null) throw new ProjectException(BaseEnum.REMOTE_FAIL);
            return new AiOwnPolicyChatDetailResponseVO("NOT_FOUND", source.getAsOf(), null);
        }
        if (!"FOUND".equals(source.getStatus()) || source.getPolicy() == null) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        AgnoOwnPolicySummaryDTO summary = new AgnoOwnPolicySummaryDTO();
        summary.setStatus("FOUND");
        summary.setAsOf(source.getAsOf());
        summary.setReturnedCount(1);
        summary.setTruncated(false);
        summary.setPolicies(Collections.singletonList(source.getPolicy()));
        AiOwnPolicyChatSummaryResponseVO mapped = mapOwnPolicy(summary);
        return new AiOwnPolicyChatDetailResponseVO("FOUND", mapped.getAsOf(), mapped.getPolicies().get(0));
    }

    private static AiProductComparisonResponseVO mapComparison(AgnoProductComparisonDTO comparison) {
        if (comparison == null) return null;
        if (!StringUtils.hasText(comparison.getRequestId()) || !isUuid(comparison.getRequestId())
                || !StringUtils.hasText(comparison.getCategoryId())
                || !comparison.getCategoryId().matches("[0-9]{1,19}")) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        List<AgnoProductComparisonProductDTO> sourceProducts = comparison.getProducts();
        List<AgnoRecommendationUnresolvedCriterionDTO> sourceUnresolved = comparison.getUnresolvedCriteria();
        boolean unavailable = "CATALOG_NOT_READY".equals(comparison.getStatus());
        boolean partial = "PARTIAL".equals(comparison.getStatus());
        boolean complete = "COMPLETE".equals(comparison.getStatus());
        if (!unavailable && !partial && !complete) throw new ProjectException(BaseEnum.REMOTE_FAIL);
        if (sourceProducts == null || sourceUnresolved == null) throw new ProjectException(BaseEnum.REMOTE_FAIL);
        if (unavailable) {
            if (!sourceProducts.isEmpty() || !sourceUnresolved.isEmpty()) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            return new AiProductComparisonResponseVO(
                    comparison.getRequestId(), comparison.getStatus(), comparison.getCategoryId(),
                    comparison.getCatalogVersion(), comparison.getRuleVersion(),
                    Collections.<AiProductComparisonProductVO>emptyList(),
                    Collections.<AiRecommendationUnresolvedCriteriaVO>emptyList(), comparison.getComparedAt());
        }
        if (sourceProducts.size() < 2 || sourceProducts.size() > 10
                || !StringUtils.hasText(comparison.getCatalogVersion())
                || comparison.getCatalogVersion().length() > 64
                || comparison.getRuleVersion() == null || comparison.getRuleVersion() < 1
                || (partial && sourceUnresolved.isEmpty()) || (complete && !sourceUnresolved.isEmpty())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }

        List<AiProductComparisonProductVO> products = new ArrayList<AiProductComparisonProductVO>();
        Set<String> productIds = new HashSet<String>();
        Set<String> expectedFactCodes = null;
        for (AgnoProductComparisonProductDTO product : sourceProducts) {
            if (product == null || !StringUtils.hasText(product.getProductId())
                    || !product.getProductId().matches("[0-9]{1,19}")
                    || !productIds.add(product.getProductId())
                    || !StringUtils.hasText(product.getName()) || product.getName().length() > 255
                    || product.getProductVersion() == null || product.getProductVersion() < 1
                    || product.getFacts() == null || product.getFacts().size() > 100) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            Set<String> factCodes = new HashSet<String>();
            for (AgnoRecommendationFactDTO fact : product.getFacts()) {
                validateFact(fact);
                if (!factCodes.add(fact.getCode())) throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            if (expectedFactCodes == null) expectedFactCodes = factCodes;
            else if (!expectedFactCodes.equals(factCodes)) throw new ProjectException(BaseEnum.REMOTE_FAIL);
            products.add(new AiProductComparisonProductVO(
                    product.getProductId(), product.getName(), product.getProductVersion(),
                    mapFacts(product.getFacts())));
        }
        List<AiRecommendationUnresolvedCriteriaVO> unresolved = mapUnresolved(sourceUnresolved);
        return new AiProductComparisonResponseVO(
                comparison.getRequestId(), comparison.getStatus(), comparison.getCategoryId(),
                comparison.getCatalogVersion(), comparison.getRuleVersion(), products,
                unresolved, comparison.getComparedAt());
    }

    private static void validateEnvelope(AgnoChatResponse response) {
        if (response.getOwnPolicySummary() != null || response.getOwnPolicyDetail() != null) {
            if (response.getRecommendationStatus() != null
                    || !isEmpty(response.getRecommendations())
                    || response.getProductComparison() != null
                    || response.getOwnPolicySummary() != null && response.getOwnPolicyDetail() != null) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            return;
        }
        String status = response.getRecommendationStatus();
        if (status == null) {
            if (response.getEvaluationCompleteness() != null
                    || response.getRuleVersion() != null
                    || StringUtils.hasText(response.getCatalogVersion())
                    || !isEmpty(response.getMissingCriteria())
                    || !isEmpty(response.getUnsupportedCriteria())
                    || !isEmpty(response.getUnresolvedCriteria())
                    || (response.getExcludedProductCount() != null && response.getExcludedProductCount() != 0)) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            return;
        }
        Set<String> statuses = new HashSet<String>(Arrays.asList(
                "MATCHED", "NEEDS_CLARIFICATION", "UNSUPPORTED_CRITERIA", "CATALOG_INCOMPLETE",
                "CATALOG_NOT_READY", "NO_MATCH"));
        if (!statuses.contains(status)) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        validateCodes(response.getMissingCriteria());
        validateCodes(response.getUnsupportedCriteria());
        List<AgnoProductCandidateDTO> candidates = response.getRecommendations();
        boolean hasCandidates = candidates != null && !candidates.isEmpty();
        if ("MATCHED".equals(status)
                && (!hasCandidates || !"COMPLETE".equals(response.getEvaluationCompleteness()))) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if ("CATALOG_INCOMPLETE".equals(status)
                && (!"PARTIAL".equals(response.getEvaluationCompleteness())
                || isEmpty(response.getUnresolvedCriteria()))) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (!"CATALOG_INCOMPLETE".equals(status)
                && !"COMPLETE".equals(response.getEvaluationCompleteness())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (!"MATCHED".equals(status) && !"CATALOG_INCOMPLETE".equals(status) && hasCandidates) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if ("NEEDS_CLARIFICATION".equals(status) && isEmpty(response.getMissingCriteria())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if ("UNSUPPORTED_CRITERIA".equals(status) && isEmpty(response.getUnsupportedCriteria())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (!"NEEDS_CLARIFICATION".equals(status) && !isEmpty(response.getMissingCriteria())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (!"UNSUPPORTED_CRITERIA".equals(status) && !isEmpty(response.getUnsupportedCriteria())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (!"CATALOG_INCOMPLETE".equals(status) && !isEmpty(response.getUnresolvedCriteria())) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (("MATCHED".equals(status) || "CATALOG_INCOMPLETE".equals(status))
                && (!StringUtils.hasText(response.getCatalogVersion())
                || response.getCatalogVersion().length() > 64
                || response.getRuleVersion() == null || response.getRuleVersion() < 1)) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (response.getExcludedProductCount() != null && response.getExcludedProductCount() < 0) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
    }

    private static void validateCandidate(AgnoChatResponse response, AgnoProductCandidateDTO candidate) {
        if (response.getRecommendationStatus() == null) {
            if (!StringUtils.hasText(candidate.getCategoryCode())
                    || !StringUtils.hasText(candidate.getCategoryName())) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            return;
        }
        if (candidate.getProductVersion() == null || candidate.getProductVersion() < 1
                || !"VERIFIED_MATCH".equals(candidate.getVerificationStatus())
                || candidate.getFacts() == null || candidate.getFacts().size() > 100
                || (candidate.getMatchReasons() != null && candidate.getMatchReasons().size() > 10)) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (candidate.getMatchReasons() != null) {
            for (String reason : candidate.getMatchReasons()) {
                if (!StringUtils.hasText(reason) || reason.length() > 255) {
                    throw new ProjectException(BaseEnum.REMOTE_FAIL);
                }
            }
        }
        Set<String> codes = new HashSet<String>();
        for (AgnoRecommendationFactDTO fact : candidate.getFacts()) {
            validateFact(fact);
            if (!codes.add(fact.getCode())) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
        }
    }

    private static void validateFact(AgnoRecommendationFactDTO fact) {
        if (fact == null || !StringUtils.hasText(fact.getCode()) || !StringUtils.hasText(fact.getName())
                || fact.getDefinitionVersion() == null || fact.getDefinitionVersion() < 1
                || !Arrays.asList("ENUM", "MULTI_ENUM", "NUMBER", "NUMBER_RANGE")
                .contains(fact.getValueType())
                || !Arrays.asList("KNOWN", "NOT_PROVIDED", "NOT_APPLICABLE")
                .contains(fact.getValueStatus())
                || fact.getCode().length() > 80 || fact.getName().length() > 120
                || fact.getValueCode() != null && fact.getValueCode().length() > 80
                || fact.getValueDisplay() != null && fact.getValueDisplay().length() > 1000
                || fact.getValueType() != null && fact.getValueType().length() > 24
                || fact.getUnit() != null && fact.getUnit().length() > 24) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        boolean hasValue = fact.getValueCode() != null || !isEmpty(fact.getValueCodes())
                || fact.getValueDisplay() != null || fact.getValueNumber() != null
                || fact.getMinValue() != null || fact.getMaxValue() != null;
        if (!"KNOWN".equals(fact.getValueStatus())) {
            if (hasValue) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            return;
        }
        String type = fact.getValueType();
        boolean valid = "ENUM".equals(type)
                ? StringUtils.hasText(fact.getValueCode()) && isEmpty(fact.getValueCodes())
                    && fact.getValueNumber() == null && fact.getMinValue() == null && fact.getMaxValue() == null
                : "MULTI_ENUM".equals(type)
                    ? !isEmpty(fact.getValueCodes()) && fact.getValueCode() == null
                        && fact.getValueNumber() == null && fact.getMinValue() == null && fact.getMaxValue() == null
                    : "NUMBER".equals(type)
                        ? fact.getValueNumber() != null && fact.getValueCode() == null
                            && isEmpty(fact.getValueCodes()) && fact.getMinValue() == null && fact.getMaxValue() == null
                        : fact.getMinValue() != null && fact.getMaxValue() != null
                            && fact.getMinValue().compareTo(fact.getMaxValue()) <= 0
                            && fact.getValueCode() == null && isEmpty(fact.getValueCodes())
                            && fact.getValueNumber() == null;
        if (!valid) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        if (fact.getValueCodes() != null) {
            Set<String> uniqueValues = new HashSet<String>();
            for (String value : fact.getValueCodes()) {
                if (!StringUtils.hasText(value) || value.length() > 80 || !uniqueValues.add(value)) {
                    throw new ProjectException(BaseEnum.REMOTE_FAIL);
                }
            }
        }
    }

    private static List<AiRecommendationFactVO> mapFacts(List<AgnoRecommendationFactDTO> facts) {
        if (facts == null || facts.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiRecommendationFactVO> mapped = new ArrayList<AiRecommendationFactVO>(facts.size());
        for (AgnoRecommendationFactDTO fact : facts) {
            if (fact == null) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            mapped.add(new AiRecommendationFactVO(
                    fact.getCode(),
                    fact.getDefinitionVersion(),
                    fact.getName(),
                    fact.getValueType(),
                    fact.getUnit(),
                    fact.getValueCode(),
                    fact.getValueCodes(),
                    fact.getValueDisplay(),
                    fact.getValueNumber(),
                    fact.getMinValue(),
                    fact.getMaxValue(),
                    fact.getValueStatus()));
        }
        return mapped;
    }

    private static List<AiRecommendationUnresolvedCriteriaVO> mapUnresolved(
            List<AgnoRecommendationUnresolvedCriterionDTO> unresolved) {
        if (unresolved == null || unresolved.isEmpty()) {
            return Collections.emptyList();
        }
        List<AiRecommendationUnresolvedCriteriaVO> mapped =
                new ArrayList<AiRecommendationUnresolvedCriteriaVO>(unresolved.size());
        for (AgnoRecommendationUnresolvedCriterionDTO item : unresolved) {
            if (item == null || !StringUtils.hasText(item.getCode())
                    || !StringUtils.hasText(item.getReason()) || item.getAffectedProductCount() == null
                    || item.getAffectedProductCount() < 0) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
            mapped.add(new AiRecommendationUnresolvedCriteriaVO(
                    item.getCode(), item.getAffectedProductCount(), item.getReason()));
        }
        return mapped;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? Collections.<String>emptyList() : values;
    }

    private static void validateCodes(List<String> codes) {
        if (codes == null) {
            return;
        }
        if (codes.size() > 100) {
            throw new ProjectException(BaseEnum.REMOTE_FAIL);
        }
        for (String code : codes) {
            if (!StringUtils.hasText(code) || code.length() > 80) {
                throw new ProjectException(BaseEnum.REMOTE_FAIL);
            }
        }
    }

    private static boolean isUuid(String value) {
        try {
            return java.util.UUID.fromString(value).toString().equalsIgnoreCase(value);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isEmpty(List<?> values) {
        return values == null || values.isEmpty();
    }
}
