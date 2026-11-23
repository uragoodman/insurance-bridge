package com.itheima.bolee.insurance.dto.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Response returned by the private Agno endpoint. */
@Data
@NoArgsConstructor
public class AgnoChatResponse {

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("run_id")
    private String runId;

    private String answer;

    @JsonProperty("recommendations")
    private List<AgnoProductCandidateDTO> recommendations = new ArrayList<AgnoProductCandidateDTO>();

    @JsonProperty("recommendation_status")
    private String recommendationStatus;

    @JsonProperty("evaluation_completeness")
    private String evaluationCompleteness;

    @JsonProperty("missing_criteria")
    private List<String> missingCriteria = new ArrayList<String>();

    @JsonProperty("unsupported_criteria")
    private List<String> unsupportedCriteria = new ArrayList<String>();

    @JsonProperty("unresolved_criteria")
    private List<AgnoRecommendationUnresolvedCriterionDTO> unresolvedCriteria =
            new ArrayList<AgnoRecommendationUnresolvedCriterionDTO>();

    @JsonProperty("excluded_product_count")
    private Integer excludedProductCount = 0;

    @JsonProperty("catalog_version")
    private String catalogVersion;

    @JsonProperty("rule_version")
    private Integer ruleVersion;

    @JsonProperty("product_comparison")
    private AgnoProductComparisonDTO productComparison;

    @JsonProperty("own_policy_summary")
    private AgnoOwnPolicySummaryDTO ownPolicySummary;

    @JsonProperty("own_policy_detail")
    private AgnoOwnPolicyDetailDTO ownPolicyDetail;

    @JsonProperty("term_citations")
    private List<AgnoTermCitationDTO> termCitations = new ArrayList<AgnoTermCitationDTO>();
}
