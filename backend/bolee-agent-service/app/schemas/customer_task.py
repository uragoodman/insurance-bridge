"""Single LLM-facing contract for selecting a supported customer-service task."""

from __future__ import annotations

from enum import StrEnum
from typing import Any, ClassVar

from pydantic import Field, model_validator

from app.domain.customer_task import CustomerTaskIntent
from app.schemas.base import ContractModel
from app.schemas.product_recommendation import ProductRecommendationCriteria


class CustomerTaskAction(StrEnum):
    LIST_CATEGORIES = "LIST_CATEGORIES"
    SEARCH_PRODUCTS = "SEARCH_PRODUCTS"
    GET_RECOMMENDATION_CRITERIA = "GET_RECOMMENDATION_CRITERIA"
    RECOMMEND_PRODUCTS = "RECOMMEND_PRODUCTS"
    COMPARE_PRODUCTS = "COMPARE_PRODUCTS"
    SEARCH_PRODUCT_TERMS = "SEARCH_PRODUCT_TERMS"
    GET_MY_POLICIES = "GET_MY_POLICIES"
    GET_MY_POLICY = "GET_MY_POLICY"
    GET_SELECTED_POLICY = "GET_SELECTED_POLICY"


class CustomerTaskRequest(ContractModel):
    """Flat, bounded schema supported by OpenAI-compatible tool calling."""

    intent: CustomerTaskIntent
    action: CustomerTaskAction
    category_id: str | None = Field(default=None, max_length=19, pattern=r"^[0-9]+$")
    keyword: str | None = Field(default=None, min_length=1, max_length=100)
    keywords: list[str] | None = Field(default=None, max_length=8)
    # Catalog and recommendation services both cap results at three. LLM-facing wrappers
    # inject this value server-side instead of asking the model to choose it.
    limit: int | None = Field(default=None, ge=1, le=3, strict=True)
    criteria: ProductRecommendationCriteria | None = None
    product_ids: list[str] | None = Field(default=None, max_length=10)
    product_name: str | None = Field(default=None, max_length=200)
    question: str | None = Field(default=None, min_length=1, max_length=1000)
    policy_id: str | None = Field(default=None, max_length=19, pattern=r"^[0-9]+$")

    _ACTION_INTENTS: ClassVar[dict[CustomerTaskAction, frozenset[CustomerTaskIntent]]] = {
        CustomerTaskAction.LIST_CATEGORIES: frozenset(
            {
                CustomerTaskIntent.PRODUCT_CATALOG,
                CustomerTaskIntent.PRODUCT_RECOMMENDATION,
                CustomerTaskIntent.PRODUCT_COMPARISON,
            }
        ),
        CustomerTaskAction.SEARCH_PRODUCTS: frozenset(
            {CustomerTaskIntent.PRODUCT_CATALOG, CustomerTaskIntent.PRODUCT_COMPARISON}
        ),
        CustomerTaskAction.GET_RECOMMENDATION_CRITERIA: frozenset(
            {CustomerTaskIntent.PRODUCT_RECOMMENDATION}
        ),
        CustomerTaskAction.RECOMMEND_PRODUCTS: frozenset(
            {CustomerTaskIntent.PRODUCT_RECOMMENDATION}
        ),
        CustomerTaskAction.COMPARE_PRODUCTS: frozenset({CustomerTaskIntent.PRODUCT_COMPARISON}),
        CustomerTaskAction.SEARCH_PRODUCT_TERMS: frozenset({CustomerTaskIntent.TERMS_QA}),
        CustomerTaskAction.GET_MY_POLICIES: frozenset({CustomerTaskIntent.OWN_POLICY}),
        CustomerTaskAction.GET_MY_POLICY: frozenset({CustomerTaskIntent.OWN_POLICY}),
        CustomerTaskAction.GET_SELECTED_POLICY: frozenset({CustomerTaskIntent.OWN_POLICY}),
    }
    _ACTION_FIELDS: ClassVar[dict[CustomerTaskAction, set[str]]] = {
        CustomerTaskAction.LIST_CATEGORIES: set(),
        CustomerTaskAction.SEARCH_PRODUCTS: {"category_id", "keyword", "keywords", "limit"},
        CustomerTaskAction.GET_RECOMMENDATION_CRITERIA: {"category_id"},
        CustomerTaskAction.RECOMMEND_PRODUCTS: {"criteria", "limit"},
        CustomerTaskAction.COMPARE_PRODUCTS: {"category_id", "product_ids"},
        CustomerTaskAction.SEARCH_PRODUCT_TERMS: {"product_name", "question"},
        CustomerTaskAction.GET_MY_POLICIES: set(),
        CustomerTaskAction.GET_MY_POLICY: {"policy_id"},
        CustomerTaskAction.GET_SELECTED_POLICY: set(),
    }

    @model_validator(mode="after")
    def validate_action_payload(self) -> CustomerTaskRequest:
        allowed_intents = self._ACTION_INTENTS[self.action]
        if self.intent not in allowed_intents:
            raise ValueError("intent does not match the requested action")

        required_by_action = {
            CustomerTaskAction.GET_RECOMMENDATION_CRITERIA: ("category_id",),
            CustomerTaskAction.RECOMMEND_PRODUCTS: ("criteria",),
            CustomerTaskAction.COMPARE_PRODUCTS: ("category_id", "product_ids"),
            CustomerTaskAction.SEARCH_PRODUCT_TERMS: ("product_name", "question"),
            CustomerTaskAction.GET_MY_POLICY: ("policy_id",),
        }
        missing = [
            name
            for name in required_by_action.get(self.action, ())
            if getattr(self, name) is None or getattr(self, name) == []
        ]
        if missing:
            raise ValueError("missing required task fields: " + ", ".join(missing))

        if self.action == CustomerTaskAction.SEARCH_PRODUCTS and not (
            self.category_id or self.keyword or self.keywords
        ):
            raise ValueError("product search requires a category or keyword")
        if self.action == CustomerTaskAction.COMPARE_PRODUCTS:
            assert self.product_ids is not None
            if len(self.product_ids) < 2 or len(set(self.product_ids)) != len(self.product_ids):
                raise ValueError("comparison requires 2 to 10 distinct product IDs")

        provided = self.model_fields_set - {"intent", "action"}
        unexpected = provided - self._ACTION_FIELDS[self.action]
        if unexpected:
            fields = ", ".join(sorted(unexpected))
            raise ValueError("fields do not match the requested action: " + fields)
        return self

    def handler_arguments(self) -> dict[str, Any]:
        allowed = self._ACTION_FIELDS[self.action]
        return {name: getattr(self, name) for name in allowed if getattr(self, name) is not None}


class CustomerTaskOutcome(ContractModel):
    """Bounded status returned by the dispatcher; business payload remains in the collector."""

    status: str = Field(max_length=40)
    intent: CustomerTaskIntent
    action: CustomerTaskAction
    task_id: str = Field(min_length=1, max_length=64)
    stage: str = Field(min_length=1, max_length=64)
    missing_slots: list[str] = Field(default_factory=list, max_length=8)
    detail: str = Field(default="", max_length=500)
