"""Typed contracts for Java's versioned product recommendation API."""

from datetime import datetime
from decimal import Decimal
from enum import StrEnum
from typing import Literal

from pydantic import AliasChoices, Field, model_validator

from app.schemas.base import ContractModel


class CriteriaCatalogStatus(StrEnum):
    READY = "READY"
    NOT_PUBLISHED = "NOT_PUBLISHED"
    NOT_READY = "NOT_READY"


class RecommendationRuleMode(StrEnum):
    HARD_FILTER = "HARD_FILTER"
    PREFERENCE_SCORE = "PREFERENCE_SCORE"
    COMPARE_ONLY = "COMPARE_ONLY"


class CustomerIntent(StrEnum):
    MUST_HAVE = "MUST_HAVE"
    PREFERENCE = "PREFERENCE"


class RecommendationValueType(StrEnum):
    ENUM = "ENUM"
    MULTI_ENUM = "MULTI_ENUM"
    NUMBER = "NUMBER"
    NUMBER_RANGE = "NUMBER_RANGE"


class ProductRecommendationStatus(StrEnum):
    MATCHED = "MATCHED"
    NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION"
    UNSUPPORTED_CRITERIA = "UNSUPPORTED_CRITERIA"
    CATALOG_INCOMPLETE = "CATALOG_INCOMPLETE"
    CATALOG_NOT_READY = "CATALOG_NOT_READY"
    NO_MATCH = "NO_MATCH"


class EvaluationCompleteness(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"


class ProductFactStatus(StrEnum):
    KNOWN = "KNOWN"
    NOT_PROVIDED = "NOT_PROVIDED"
    NOT_APPLICABLE = "NOT_APPLICABLE"


class RecommendationOption(ContractModel):
    value_code: str = Field(
        validation_alias=AliasChoices("value_code", "valueCode"), min_length=1, max_length=80
    )
    display_name: str = Field(
        validation_alias=AliasChoices("display_name", "displayName"), min_length=1, max_length=120
    )


class RecommendationCriterion(ContractModel):
    code: str = Field(min_length=1, max_length=80)
    definition_version: int = Field(
        validation_alias=AliasChoices("definition_version", "definitionVersion"), ge=1
    )
    name: str = Field(min_length=1, max_length=120)
    value_type: RecommendationValueType = Field(
        validation_alias=AliasChoices("value_type", "valueType")
    )
    unit: str | None = Field(default=None, max_length=24)
    allowed_operators: list[str] = Field(
        validation_alias=AliasChoices("allowed_operators", "allowedOperators"), max_length=8
    )
    allowed_uses: list[RecommendationRuleMode] = Field(
        validation_alias=AliasChoices("allowed_uses", "allowedUses"), min_length=1, max_length=3
    )
    active_rule_mode: RecommendationRuleMode = Field(
        validation_alias=AliasChoices("active_rule_mode", "activeRuleMode")
    )
    required_for_clarification: bool = Field(
        validation_alias=AliasChoices("required_for_clarification", "requiredForClarification")
    )
    options: list[RecommendationOption] = Field(default_factory=list, max_length=500)


class ProductRecommendationCriteriaCatalog(ContractModel):
    status: CriteriaCatalogStatus
    category_id: str = Field(
        alias="categoryId",
        validation_alias=AliasChoices("category_id", "categoryId"),
        min_length=1,
        max_length=19,
        pattern=r"^[0-9]{1,19}$",
    )
    catalog_version: str | None = Field(
        default=None,
        validation_alias=AliasChoices("catalog_version", "catalogVersion"),
        max_length=64,
    )
    rule_version: int | None = Field(
        default=None, validation_alias=AliasChoices("rule_version", "ruleVersion"), ge=1
    )
    criteria: list[RecommendationCriterion] = Field(default_factory=list, max_length=100)

    @model_validator(mode="after")
    def validate_catalog_state(self) -> "ProductRecommendationCriteriaCatalog":
        codes = [criterion.code for criterion in self.criteria]
        if len(codes) != len(set(codes)):
            raise ValueError("Recommendation catalog contains duplicate criterion codes")
        if self.status == CriteriaCatalogStatus.READY:
            if not self.catalog_version or self.rule_version is None or not self.criteria:
                raise ValueError("READY recommendation catalog requires versions and criteria")
        elif self.criteria:
            raise ValueError("Unavailable recommendation catalogs cannot expose criteria")
        return self


class RecommendationCriterionInput(ContractModel):
    code: str = Field(
        min_length=1,
        max_length=80,
        description="Criterion code returned by the published recommendation criteria catalog.",
    )
    operator: str = Field(
        min_length=1,
        max_length=16,
        description=(
            "Allowed operator returned by the catalog, such as EQ, IN, LTE, GTE, or RANGE."
        ),
    )
    customer_intent: CustomerIntent = Field(
        alias="customerIntent",
        validation_alias=AliasChoices("customer_intent", "customerIntent"),
        description="MUST_HAVE for a required condition; PREFERENCE for a desired feature.",
    )
    value_code: str | None = Field(
        default=None,
        alias="valueCode",
        validation_alias=AliasChoices("value_code", "valueCode"),
        max_length=80,
        description="One published option code for an EQ enum criterion.",
    )
    value_codes: list[str] | None = Field(
        default=None,
        alias="valueCodes",
        validation_alias=AliasChoices("value_codes", "valueCodes"),
        min_length=1,
        max_length=20,
        description="Published option codes for an IN criterion.",
    )
    number_value: Decimal | None = Field(
        default=None,
        alias="numberValue",
        validation_alias=AliasChoices("number_value", "numberValue"),
        max_digits=20,
        decimal_places=6,
        description="Numeric value for EQ, LTE, or GTE. Use the criterion's published unit.",
    )
    min_value: Decimal | None = Field(
        default=None,
        alias="minValue",
        validation_alias=AliasChoices("min_value", "minValue"),
        max_digits=20,
        decimal_places=6,
        description="Inclusive lower bound for a RANGE criterion.",
    )
    max_value: Decimal | None = Field(
        default=None,
        alias="maxValue",
        validation_alias=AliasChoices("max_value", "maxValue"),
        max_digits=20,
        decimal_places=6,
        description="Inclusive upper bound for a RANGE criterion.",
    )
    unit: str | None = Field(
        default=None,
        max_length=24,
        description="Exact unit returned by the published catalog for a numeric criterion.",
    )

    @model_validator(mode="after")
    def validate_value_codes(self) -> "RecommendationCriterionInput":
        if self.value_codes is not None:
            if any(not code or len(code) > 80 for code in self.value_codes):
                raise ValueError("valueCodes must contain non-empty codes of at most 80 characters")
            if len(self.value_codes) != len(set(self.value_codes)):
                raise ValueError("valueCodes must not contain duplicates")
        return self


class ProductRecommendationCriteria(ContractModel):
    category_id: str = Field(
        alias="categoryId",
        validation_alias=AliasChoices("category_id", "categoryId"),
        min_length=1,
        max_length=19,
        pattern=r"^[0-9]{1,19}$",
        description="Product category ID returned by the Java product category tool.",
    )
    items: list[RecommendationCriterionInput] = Field(
        max_length=10,
        description="Customer needs mapped to valid criteria from the current published catalog.",
    )


class ProductRecommendationRequest(ContractModel):
    request_id: str = Field(
        alias="requestId",
        validation_alias=AliasChoices("request_id", "requestId"),
        min_length=36,
        max_length=36,
        pattern=r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )
    turn_id: str | None = Field(
        default=None,
        alias="turnId",
        validation_alias=AliasChoices("turn_id", "turnId"),
        min_length=36,
        max_length=36,
        pattern=r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )
    criteria: ProductRecommendationCriteria
    limit: int = Field(default=3, ge=1, le=3, strict=True)

    @model_validator(mode="after")
    def validate_unique_criteria(self) -> "ProductRecommendationRequest":
        codes = [item.code for item in self.criteria.items]
        if len(codes) != len(set(codes)):
            raise ValueError("A criterion code may only be supplied once")
        return self


class RecommendationUnresolvedCriterion(ContractModel):
    code: str = Field(min_length=1, max_length=80)
    affected_product_count: int = Field(
        validation_alias=AliasChoices("affected_product_count", "affectedProductCount"), ge=0
    )
    reason: str = Field(min_length=1, max_length=64)


class RecommendationFact(ContractModel):
    code: str = Field(min_length=1, max_length=80)
    definition_version: int = Field(
        validation_alias=AliasChoices("definition_version", "definitionVersion"), ge=1
    )
    name: str = Field(min_length=1, max_length=120)
    value_type: RecommendationValueType = Field(
        validation_alias=AliasChoices("value_type", "valueType")
    )
    unit: str | None = Field(default=None, max_length=24)
    value_code: str | None = Field(
        default=None, validation_alias=AliasChoices("value_code", "valueCode"), max_length=80
    )
    value_codes: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("value_codes", "valueCodes"),
        max_length=500,
    )
    value_display: str | None = Field(
        default=None,
        validation_alias=AliasChoices("value_display", "valueDisplay"),
        max_length=1000,
    )
    value_number: Decimal | None = Field(
        default=None,
        validation_alias=AliasChoices("value_number", "valueNumber"),
        max_digits=20,
        decimal_places=6,
    )
    min_value: Decimal | None = Field(
        default=None,
        validation_alias=AliasChoices("min_value", "minValue"),
        max_digits=20,
        decimal_places=6,
    )
    max_value: Decimal | None = Field(
        default=None,
        validation_alias=AliasChoices("max_value", "maxValue"),
        max_digits=20,
        decimal_places=6,
    )
    value_status: ProductFactStatus = Field(
        validation_alias=AliasChoices("value_status", "valueStatus")
    )

    @model_validator(mode="after")
    def validate_typed_value(self) -> "RecommendationFact":
        decimals = (self.value_number, self.min_value, self.max_value)
        if any(value is not None and not value.is_finite() for value in decimals):
            raise ValueError("Numeric product facts must be finite")
        if any(not value.strip() for value in self.value_codes):
            raise ValueError("Product fact codes must be non-empty")
        if len(self.value_codes) != len(set(self.value_codes)):
            raise ValueError("Product fact codes must not contain duplicates")
        has_value = any(
            value is not None
            for value in (
                self.value_code,
                self.value_display,
                self.value_number,
                self.min_value,
                self.max_value,
            )
        ) or bool(self.value_codes)
        if self.value_status != ProductFactStatus.KNOWN:
            if has_value:
                raise ValueError("Unknown product facts cannot expose typed values")
            return self

        if self.value_type == RecommendationValueType.ENUM:
            valid = (
                bool(self.value_code)
                and not self.value_codes
                and self.value_number is None
                and self.min_value is None
                and self.max_value is None
            )
        elif self.value_type == RecommendationValueType.MULTI_ENUM:
            valid = (
                bool(self.value_codes)
                and self.value_code is None
                and self.value_number is None
                and self.min_value is None
                and self.max_value is None
            )
        elif self.value_type == RecommendationValueType.NUMBER:
            valid = (
                self.value_number is not None
                and self.value_code is None
                and not self.value_codes
                and self.min_value is None
                and self.max_value is None
            )
        else:
            valid = (
                self.min_value is not None
                and self.max_value is not None
                and self.min_value <= self.max_value
                and self.value_code is None
                and not self.value_codes
                and self.value_number is None
            )
        if not valid:
            raise ValueError("Known product fact does not match its declared value type")
        return self


class ProductRecommendationCandidate(ContractModel):
    product_id: str = Field(
        validation_alias=AliasChoices("product_id", "productId"), min_length=1, max_length=64
    )
    name: str = Field(min_length=1, max_length=255)
    category_id: str = Field(
        validation_alias=AliasChoices("category_id", "categoryId"),
        min_length=1,
        max_length=19,
        pattern=r"^[0-9]{1,19}$",
    )
    product_version: int = Field(
        validation_alias=AliasChoices("product_version", "productVersion"), ge=1
    )
    verification_status: Literal["VERIFIED_MATCH"] = Field(
        validation_alias=AliasChoices("verification_status", "verificationStatus"),
        min_length=1,
        max_length=32,
    )
    facts: list[RecommendationFact] = Field(default_factory=list, max_length=100)
    match_reasons: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("match_reasons", "matchReasons"),
        max_length=10,
    )

    @model_validator(mode="after")
    def validate_unique_fact_codes(self) -> "ProductRecommendationCandidate":
        codes = [fact.code for fact in self.facts]
        if len(codes) != len(set(codes)):
            raise ValueError("Recommendation candidate contains duplicate fact codes")
        if any(not reason.strip() for reason in self.match_reasons):
            raise ValueError("Recommendation match reasons must not be empty")
        return self


class ProductRecommendationResponse(ContractModel):
    request_id: str = Field(
        validation_alias=AliasChoices("request_id", "requestId"), min_length=36, max_length=36
    )
    status: ProductRecommendationStatus
    evaluation_completeness: EvaluationCompleteness = Field(
        validation_alias=AliasChoices("evaluation_completeness", "evaluationCompleteness")
    )
    missing_criteria: list[str] = Field(
        validation_alias=AliasChoices("missing_criteria", "missingCriteria"), max_length=100
    )
    unsupported_criteria: list[str] = Field(
        validation_alias=AliasChoices("unsupported_criteria", "unsupportedCriteria"), max_length=100
    )
    unresolved_criteria: list[RecommendationUnresolvedCriterion] = Field(
        validation_alias=AliasChoices("unresolved_criteria", "unresolvedCriteria"), max_length=100
    )
    excluded_product_count: int = Field(
        validation_alias=AliasChoices("excluded_product_count", "excludedProductCount"), ge=0
    )
    candidates: list[ProductRecommendationCandidate] = Field(max_length=3)
    evaluated_at: datetime | None = Field(
        default=None, validation_alias=AliasChoices("evaluated_at", "evaluatedAt")
    )
    catalog_version: str | None = Field(
        default=None,
        validation_alias=AliasChoices("catalog_version", "catalogVersion"),
        max_length=64,
    )
    rule_version: int | None = Field(
        default=None, validation_alias=AliasChoices("rule_version", "ruleVersion"), ge=1
    )
    engine_version: str | None = Field(
        default=None,
        validation_alias=AliasChoices("engine_version", "engineVersion"),
        max_length=64,
    )

    @model_validator(mode="after")
    def validate_result_state(self) -> "ProductRecommendationResponse":
        candidate_ids = [candidate.product_id for candidate in self.candidates]
        if len(candidate_ids) != len(set(candidate_ids)):
            raise ValueError("Recommendation response contains duplicate product IDs")
        if self.status == ProductRecommendationStatus.MATCHED and not self.candidates:
            raise ValueError("MATCHED recommendation response requires candidates")
        if self.status == ProductRecommendationStatus.MATCHED and (
            self.evaluation_completeness != EvaluationCompleteness.COMPLETE
        ):
            raise ValueError("MATCHED recommendation response must be complete")
        if self.status in {
            ProductRecommendationStatus.MATCHED,
            ProductRecommendationStatus.CATALOG_INCOMPLETE,
        } and (not self.catalog_version or self.rule_version is None):
            raise ValueError("Candidate results require catalog and rule versions")
        no_candidate_statuses = {
            ProductRecommendationStatus.NEEDS_CLARIFICATION,
            ProductRecommendationStatus.UNSUPPORTED_CRITERIA,
            ProductRecommendationStatus.NO_MATCH,
            ProductRecommendationStatus.CATALOG_NOT_READY,
        }
        if self.status in no_candidate_statuses:
            if self.candidates:
                raise ValueError("This recommendation status cannot include candidates")
        if (
            self.status == ProductRecommendationStatus.NEEDS_CLARIFICATION
            and not self.missing_criteria
        ):
            raise ValueError("NEEDS_CLARIFICATION requires missing criteria")
        if self.status != ProductRecommendationStatus.NEEDS_CLARIFICATION and self.missing_criteria:
            raise ValueError("Only NEEDS_CLARIFICATION may include missing criteria")
        if (
            self.status == ProductRecommendationStatus.UNSUPPORTED_CRITERIA
            and not self.unsupported_criteria
        ):
            raise ValueError("UNSUPPORTED_CRITERIA requires unsupported criteria")
        if (
            self.status != ProductRecommendationStatus.UNSUPPORTED_CRITERIA
            and self.unsupported_criteria
        ):
            raise ValueError("Only UNSUPPORTED_CRITERIA may include unsupported criteria")
        if self.status == ProductRecommendationStatus.CATALOG_INCOMPLETE:
            if (
                self.evaluation_completeness != EvaluationCompleteness.PARTIAL
                or not self.unresolved_criteria
            ):
                raise ValueError(
                    "CATALOG_INCOMPLETE requires unresolved criteria and PARTIAL completeness"
                )
        elif self.evaluation_completeness != EvaluationCompleteness.COMPLETE:
            raise ValueError("Only CATALOG_INCOMPLETE may be PARTIAL")
        if (
            self.status != ProductRecommendationStatus.CATALOG_INCOMPLETE
            and self.unresolved_criteria
        ):
            raise ValueError("Only CATALOG_INCOMPLETE may include unresolved criteria")
        return self
