package com.itheima.bolee.insurance.dto.ai.contract;

import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyChatSummaryResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiOwnPolicyChatDetailResponseVO;
import com.itheima.bolee.insurance.dto.ai.AiProductCandidateVO;
import com.itheima.bolee.insurance.dto.ai.AiTermCitationVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiRecommendationUnresolvedCriteriaVO;
import com.itheima.bolee.insurance.dto.ai.recommendation.AiProductComparisonResponseVO;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Customer-visible structured cards attached to a completed assistant message. */
@Data
@NoArgsConstructor
public class AiMessageStructuredDataDTO {
    private List<AiProductCandidateVO> recommendations = new ArrayList<AiProductCandidateVO>();
    private AiProductComparisonResponseVO productComparison;
    private AiOwnPolicyChatSummaryResponseVO ownPolicySummary;
    private AiOwnPolicyChatDetailResponseVO ownPolicyDetail;
    private String recommendationStatus;
    private String evaluationCompleteness;
    private List<String> missingCriteria = new ArrayList<String>();
    private List<String> unsupportedCriteria = new ArrayList<String>();
    private List<AiRecommendationUnresolvedCriteriaVO> unresolvedCriteria =
            new ArrayList<AiRecommendationUnresolvedCriteriaVO>();
    private List<AiTermCitationVO> termCitations = new ArrayList<AiTermCitationVO>();
    private Integer excludedProductCount;
    private String catalogVersion;
    private Integer ruleVersion;
}
