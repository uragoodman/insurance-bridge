package com.itheima.bolee.insurance.dto.ai;

import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationUnresolvedCriteriaVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonResponseVO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Customer-facing response containing the conversation ID and assistant answer. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiAssistantChatResponseVO {

    public AiAssistantChatResponseVO(String sessionId, String answer, List<AiProductCandidateVO> recommendations,
                                     String recommendationStatus, String evaluationCompleteness,
                                     List<String> missingCriteria, List<String> unsupportedCriteria,
                                     List<AiRecommendationUnresolvedCriteriaVO> unresolvedCriteria,
                                     Integer excludedProductCount, String catalogVersion, Integer ruleVersion,
                                     AiProductComparisonResponseVO productComparison) {
        this.sessionId = sessionId;
        this.answer = answer;
        this.recommendations = recommendations;
        this.recommendationStatus = recommendationStatus;
        this.evaluationCompleteness = evaluationCompleteness;
        this.missingCriteria = missingCriteria;
        this.unsupportedCriteria = unsupportedCriteria;
        this.unresolvedCriteria = unresolvedCriteria;
        this.excludedProductCount = excludedProductCount;
        this.catalogVersion = catalogVersion;
        this.ruleVersion = ruleVersion;
        this.productComparison = productComparison;
    }

    private String sessionId;

    private String answer;

    private List<AiProductCandidateVO> recommendations;

    private String recommendationStatus;

    private String evaluationCompleteness;

    private List<String> missingCriteria;

    private List<String> unsupportedCriteria;

    private List<AiRecommendationUnresolvedCriteriaVO> unresolvedCriteria;

    private Integer excludedProductCount;

    private String catalogVersion;

    private Integer ruleVersion;

    private AiProductComparisonResponseVO productComparison;

    private AiOwnPolicyChatSummaryResponseVO ownPolicySummary;

    private AiOwnPolicyChatDetailResponseVO ownPolicyDetail;

    /** COMPLETED or PROCESSING; clients retry the same request ID while processing. */
    private String requestStatus;

    private String clientMessageId;

    private String turnId;
}
