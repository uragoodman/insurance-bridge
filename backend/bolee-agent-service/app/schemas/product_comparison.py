"""Typed contracts for Java's published multi-product comparison API."""

import re
from datetime import datetime
from enum import StrEnum

from pydantic import AliasChoices, Field, model_validator

from app.schemas.base import ContractModel
from app.schemas.product_recommendation import RecommendationFact, RecommendationUnresolvedCriterion


class ProductComparisonStatus(StrEnum):
    COMPLETE = "COMPLETE"
    PARTIAL = "PARTIAL"
    CATALOG_NOT_READY = "CATALOG_NOT_READY"


class ProductComparisonRequest(ContractModel):
    request_id: str = Field(
        alias="requestId",
        validation_alias=AliasChoices("request_id", "requestId"),
        min_length=36,
        max_length=36,
        pattern=r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
    )
    category_id: str = Field(
        alias="categoryId",
        validation_alias=AliasChoices("category_id", "categoryId"),
        min_length=1,
        max_length=19,
        pattern=r"^[0-9]{1,19}$",
    )
    product_ids: list[str] = Field(
        alias="productIds",
        validation_alias=AliasChoices("product_ids", "productIds"),
        min_length=2,
        max_length=10,
    )

    @model_validator(mode="after")
    def validate_product_ids(self) -> "ProductComparisonRequest":
        if any(
            re.fullmatch(r"[0-9]{1,19}", product_id) is None or int(product_id) <= 0
            for product_id in self.product_ids
        ):
            raise ValueError("Product IDs must be positive numeric IDs")
        if len({int(product_id) for product_id in self.product_ids}) != len(self.product_ids):
            raise ValueError("Product IDs must be unique")
        return self


class ProductComparisonProduct(ContractModel):
    product_id: str = Field(
        validation_alias=AliasChoices("product_id", "productId"), min_length=1, max_length=64
    )
    name: str = Field(min_length=1, max_length=255)
    product_version: int = Field(
        validation_alias=AliasChoices("product_version", "productVersion"), ge=1
    )
    facts: list[RecommendationFact] = Field(max_length=100)

    @model_validator(mode="after")
    def validate_unique_facts(self) -> "ProductComparisonProduct":
        codes = [fact.code for fact in self.facts]
        if len(codes) != len(set(codes)):
            raise ValueError("Comparison product contains duplicate fact codes")
        return self


class ProductComparisonResponse(ContractModel):
    request_id: str = Field(
        validation_alias=AliasChoices("request_id", "requestId"), min_length=36, max_length=36
    )
    status: ProductComparisonStatus
    category_id: str = Field(
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
    products: list[ProductComparisonProduct] = Field(max_length=10)
    unresolved_criteria: list[RecommendationUnresolvedCriterion] = Field(
        validation_alias=AliasChoices("unresolved_criteria", "unresolvedCriteria"), max_length=100
    )
    compared_at: datetime | None = Field(
        default=None, validation_alias=AliasChoices("compared_at", "comparedAt")
    )

    @model_validator(mode="after")
    def validate_comparison_result(self) -> "ProductComparisonResponse":
        product_ids = [product.product_id for product in self.products]
        if len(product_ids) != len(set(product_ids)):
            raise ValueError("Comparison response contains duplicate products")

        if self.status == ProductComparisonStatus.CATALOG_NOT_READY:
            if self.products or self.unresolved_criteria:
                raise ValueError("Unavailable comparison catalog cannot contain product data")
            return self

        if (
            not self.catalog_version
            or self.rule_version is None
            or not 2 <= len(self.products) <= 10
        ):
            raise ValueError("Comparison results require versions and 2 to 10 products")
        fact_codes = [fact.code for fact in self.products[0].facts]
        if any(
            [fact.code for fact in product.facts] != fact_codes
            for product in self.products[1:]
        ):
            raise ValueError("All compared products must expose the same fact columns")
        if self.status == ProductComparisonStatus.COMPLETE and self.unresolved_criteria:
            raise ValueError("Complete comparison cannot contain unresolved criteria")
        if self.status == ProductComparisonStatus.PARTIAL and not self.unresolved_criteria:
            raise ValueError("Partial comparison requires unresolved criteria")
        return self
