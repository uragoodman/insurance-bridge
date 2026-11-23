"""Customer-to-agent request and Java-facing response contracts."""

from pydantic import Field, model_validator

from app.schemas.base import ContractModel
from app.schemas.insurance_terms import TermCitation
from app.schemas.own_policy import OwnPolicyDetailResponse, OwnPolicySummaryResponse
from app.schemas.product_catalog import ProductCandidate
from app.schemas.product_comparison import ProductComparisonResponse
from app.schemas.product_recommendation import (
    EvaluationCompleteness,
    ProductRecommendationCandidate,
    ProductRecommendationStatus,
    RecommendationUnresolvedCriterion,
)


class ChatRequest(ContractModel):
    user_id: str = Field(min_length=1, max_length=64)
    session_id: str = Field(min_length=1, max_length=64, pattern=r"^[A-Za-z0-9_-]+$")
    authorization_context: str = Field(
        min_length=43,
        max_length=43,
        pattern=r"^[A-Za-z0-9_-]{43}$",
        repr=False,
    )
    message: str = Field(min_length=1, max_length=4000)
    selected_policy_id: str | None = Field(
        default=None, min_length=1, max_length=19, pattern=r"^[0-9]+$"
    )


class ChatResponse(ContractModel):
    session_id: str
    run_id: str = Field(min_length=1, max_length=64)
    answer: str = Field(min_length=1, max_length=12000)
    recommendations: list[ProductCandidate | ProductRecommendationCandidate] = Field(
        default_factory=list, max_length=3
    )
    product_comparison: ProductComparisonResponse | None = None
    own_policy_summary: OwnPolicySummaryResponse | None = None
    own_policy_detail: OwnPolicyDetailResponse | None = None
    recommendation_status: ProductRecommendationStatus | None = None
    evaluation_completeness: EvaluationCompleteness | None = None
    missing_criteria: list[str] = Field(default_factory=list, max_length=100)
    unsupported_criteria: list[str] = Field(default_factory=list, max_length=100)
    unresolved_criteria: list[RecommendationUnresolvedCriterion] = Field(
        default_factory=list, max_length=100
    )
    excluded_product_count: int = Field(default=0, ge=0)
    catalog_version: str | None = Field(default=None, max_length=64)
    rule_version: int | None = Field(default=None, ge=1)
    term_citations: list[TermCitation] = Field(default_factory=list, max_length=6)

    @model_validator(mode="after")
    def validate_recommendation_payload(self) -> "ChatResponse":
        if self.term_citations and (
            self.recommendations
            or self.product_comparison is not None
            or self.own_policy_summary is not None
            or self.own_policy_detail is not None
            or self.recommendation_status is not None
        ):
            raise ValueError("Terms citations cannot be mixed with another structured tool result")
        if self.own_policy_summary is not None and (
            self.recommendations
            or self.product_comparison is not None
            or self.own_policy_detail is not None
            or self.recommendation_status is not None
        ):
            raise ValueError("Own-policy and product tool results must be returned separately")
        if self.own_policy_detail is not None and (
            self.recommendations
            or self.product_comparison is not None
            or self.own_policy_summary is not None
            or self.recommendation_status is not None
        ):
            raise ValueError("Own-policy and product tool results must be returned separately")
        if self.product_comparison is not None and (
            self.recommendations or self.recommendation_status is not None
        ):
            raise ValueError("Comparison and recommendation results must be returned separately")
        if self.recommendation_status is None:
            has_extra_metadata = (
                self.evaluation_completeness is not None
                or self.rule_version is not None
                or bool(self.missing_criteria)
                or bool(self.unsupported_criteria)
                or bool(self.unresolved_criteria)
                or self.excluded_product_count > 0
                or self.catalog_version is not None
            )
            if has_extra_metadata:
                raise ValueError("Recommendation metadata requires a recommendation status")
            return self

        if self.recommendation_status == ProductRecommendationStatus.MATCHED:
            has_incomplete_metadata = (
                bool(self.missing_criteria)
                or bool(self.unsupported_criteria)
                or bool(self.unresolved_criteria)
            )
            if (
                not self.recommendations
                or self.evaluation_completeness != EvaluationCompleteness.COMPLETE
                or has_incomplete_metadata
            ):
                raise ValueError("MATCHED chat responses require complete candidates")
        elif self.recommendation_status == ProductRecommendationStatus.CATALOG_INCOMPLETE:
            if (
                self.evaluation_completeness != EvaluationCompleteness.PARTIAL
                or not self.unresolved_criteria
            ):
                raise ValueError(
                    "CATALOG_INCOMPLETE requires unresolved criteria and partial status"
                )
        elif self.recommendations:
            raise ValueError("This recommendation status cannot include candidates")
        elif self.evaluation_completeness != EvaluationCompleteness.COMPLETE:
            raise ValueError("Non-partial recommendation responses must be complete")
        if (
            self.recommendation_status == ProductRecommendationStatus.NEEDS_CLARIFICATION
            and not self.missing_criteria
        ):
            raise ValueError("NEEDS_CLARIFICATION requires missing criteria")
        if (
            self.recommendation_status == ProductRecommendationStatus.UNSUPPORTED_CRITERIA
            and not self.unsupported_criteria
        ):
            raise ValueError("UNSUPPORTED_CRITERIA requires unsupported criteria")
        if (
            self.recommendation_status != ProductRecommendationStatus.NEEDS_CLARIFICATION
            and self.missing_criteria
        ):
            raise ValueError("Only NEEDS_CLARIFICATION may include missing criteria")
        if (
            self.recommendation_status != ProductRecommendationStatus.UNSUPPORTED_CRITERIA
            and self.unsupported_criteria
        ):
            raise ValueError("Only UNSUPPORTED_CRITERIA may include unsupported criteria")
        if (
            self.recommendation_status != ProductRecommendationStatus.CATALOG_INCOMPLETE
            and self.unresolved_criteria
        ):
            raise ValueError("Only CATALOG_INCOMPLETE may include unresolved criteria")
        if self.recommendation_status in {
            ProductRecommendationStatus.MATCHED,
            ProductRecommendationStatus.CATALOG_INCOMPLETE,
        } and (not self.catalog_version or self.rule_version is None):
            raise ValueError("Candidate results require catalog and rule versions")
        return self
