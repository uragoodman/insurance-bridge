"""Typed schemas for the live Java product catalog API."""

from enum import StrEnum

from pydantic import AliasChoices, Field, model_validator

from app.schemas.base import ContractModel
from app.schemas.product_recommendation import RecommendationFact


class RecommendationStatus(StrEnum):
    MATCHED = "MATCHED"
    NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION"
    NO_MATCH = "NO_MATCH"


class ProductMatchType(StrEnum):
    EXACT_NAME = "EXACT_NAME"
    APPROVED_ALIAS = "APPROVED_ALIAS"
    RELATED = "RELATED"


class MissingCriterion(StrEnum):
    PRODUCT_CATEGORY = "PRODUCT_CATEGORY"


class ProductCategory(ContractModel):
    category_id: str = Field(
        validation_alias=AliasChoices("category_id", "categoryId"),
        min_length=1,
        max_length=64,
        pattern=r"^[0-9]{1,19}$",
    )
    category_code: str = Field(
        validation_alias=AliasChoices("category_code", "categoryCode"),
        min_length=1,
        max_length=64,
    )
    category_name: str = Field(
        validation_alias=AliasChoices("category_name", "categoryName"),
        min_length=1,
        max_length=200,
    )


class ProductCandidate(ContractModel):
    product_id: str = Field(
        validation_alias=AliasChoices("product_id", "productId"),
        min_length=1,
        max_length=64,
    )
    name: str = Field(min_length=1, max_length=200)
    category_id: str = Field(
        validation_alias=AliasChoices("category_id", "categoryId"),
        min_length=1,
        max_length=64,
    )
    category_code: str = Field(
        validation_alias=AliasChoices("category_code", "categoryCode"),
        min_length=1,
        max_length=64,
    )
    category_name: str = Field(
        validation_alias=AliasChoices("category_name", "categoryName"),
        min_length=1,
        max_length=200,
    )
    match_reasons: list[str] = Field(
        default_factory=list,
        validation_alias=AliasChoices("match_reasons", "matchReasons"),
        max_length=10,
    )
    match_type: ProductMatchType = Field(
        default=ProductMatchType.RELATED,
        validation_alias=AliasChoices("match_type", "matchType"),
    )
    product_version: int | None = Field(
        default=None,
        validation_alias=AliasChoices("product_version", "productVersion"),
        ge=1,
    )
    verification_status: str | None = Field(
        default=None,
        validation_alias=AliasChoices("verification_status", "verificationStatus"),
        max_length=40,
    )
    facts: list[RecommendationFact] = Field(default_factory=list, max_length=100)
    summary: str | None = Field(default=None, max_length=4000)


class ProductCatalogSearchQuery(ContractModel):
    category_id: str | None = Field(
        default=None,
        min_length=1,
        max_length=19,
        pattern=r"^[0-9]{1,19}$",
    )
    keyword: str | None = Field(default=None, min_length=1, max_length=100)
    keywords: list[str] = Field(default_factory=list, min_length=0, max_length=10)
    limit: int = Field(default=3, ge=1, le=3, strict=True)

    @model_validator(mode="after")
    def normalize_search_terms(self) -> "ProductCatalogSearchQuery":
        terms: list[str] = []
        for term in [*self.keywords, self.keyword]:
            if term is None:
                continue
            normalized = term.strip()
            if not normalized:
                raise ValueError("Search keywords cannot be blank")
            if len(normalized) > 100:
                raise ValueError("Search keywords cannot exceed 100 characters")
            if normalized.casefold() not in {value.casefold() for value in terms}:
                terms.append(normalized)
        if len(terms) > 10:
            raise ValueError("At most 10 unique search keywords are allowed")
        self.keywords = terms
        self.keyword = terms[0] if terms else None
        return self


class ProductCatalogSearchResponse(ContractModel):
    status: RecommendationStatus
    missing_criteria: list[MissingCriterion] = Field(
        default_factory=list,
        validation_alias=AliasChoices("missing_criteria", "missingCriteria"),
    )
    candidates: list[ProductCandidate] = Field(default_factory=list, max_length=3)

    @model_validator(mode="after")
    def validate_result_state(self) -> "ProductCatalogSearchResponse":
        if self.status == RecommendationStatus.MATCHED:
            if not self.candidates or self.missing_criteria:
                raise ValueError("MATCHED requires candidates and no missing criteria")
        elif self.candidates:
            raise ValueError("Non-matched catalog results cannot include candidates")
        if self.status == RecommendationStatus.NEEDS_CLARIFICATION and not self.missing_criteria:
            raise ValueError("NEEDS_CLARIFICATION requires missing criteria")
        if self.status == RecommendationStatus.NO_MATCH and self.missing_criteria:
            raise ValueError("NO_MATCH cannot include missing criteria")
        ids = [candidate.product_id for candidate in self.candidates]
        if len(ids) != len(set(ids)):
            raise ValueError("Catalog response contains duplicate product IDs")
        return self
