"""Validated, read-only product catalog use cases backed by Java."""

import logging
from typing import Any

from pydantic import ValidationError

from app.clients.java_internal_api import JavaApiError
from app.contexts.tool import AgentToolContext
from app.schemas import (
    JavaResponseEnvelope,
    MissingCriterion,
    ProductCatalogSearchQuery,
    ProductCatalogSearchResponse,
    ProductCategory,
    RecommendationStatus,
)

logger = logging.getLogger("bolee-agent.product-catalog")


class ProductCatalogServiceError(RuntimeError):
    """Sanitized catalog failure safe to expose to the agent."""


class ProductCatalogService:
    _CATEGORIES_PATH = "/internal/agent-tools/v1/products/categories"
    _SEARCH_PATH = "/internal/agent-tools/v1/products/search"
    _JAVA_SUCCESS_CODE = 200
    _MAX_CATEGORY_COUNT = 500

    def list_categories(self, context: AgentToolContext) -> list[ProductCategory]:
        try:
            raw = context.java_internal_api.get_json(
                self._CATEGORIES_PATH,
                authorization_context=context.authorization_context,
            )
            envelope = JavaResponseEnvelope[list[ProductCategory]].model_validate(raw)
        except JavaApiError as exc:
            logger.warning("Product category query failed")
            raise ProductCatalogServiceError(
                "Product categories are temporarily unavailable."
            ) from exc
        except ValidationError as exc:
            logger.warning("Invalid Java category response")
            raise ProductCatalogServiceError(
                "Product catalog returned an invalid response."
            ) from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java category envelope rejected; code=%s", envelope.code)
            raise ProductCatalogServiceError("Product categories are temporarily unavailable.")
        if len(envelope.data) > self._MAX_CATEGORY_COUNT:
            raise ProductCatalogServiceError("Product catalog returned too many categories.")
        category_ids = [category.category_id for category in envelope.data]
        if len(category_ids) != len(set(category_ids)):
            raise ProductCatalogServiceError("Product catalog returned duplicate category IDs.")
        return envelope.data

    def search_by_category(
        self,
        category_id: str | None,
        limit: int,
        context: AgentToolContext,
        keyword: str | None = None,
        keywords: list[str] | None = None,
    ) -> ProductCatalogSearchResponse:
        context.result_collector.clear()
        try:
            query = ProductCatalogSearchQuery.model_validate(
                {
                    "category_id": category_id,
                    "keyword": keyword,
                    "keywords": keywords or [],
                    "limit": limit,
                }
            )
            params: dict[str, Any] = {"limit": query.limit}
            if query.category_id is not None:
                # Java's current endpoint names this query parameter categoryId.
                params["categoryId"] = query.category_id
            if len(query.keywords) > 1:
                params["keywords"] = query.keywords
            elif query.keyword is not None:
                params["keyword"] = query.keyword
            raw = context.java_internal_api.get_json(
                self._SEARCH_PATH,
                authorization_context=context.authorization_context,
                params=params,
            )
            envelope = JavaResponseEnvelope[ProductCatalogSearchResponse].model_validate(raw)
        except JavaApiError as exc:
            logger.warning("Product search failed")
            raise ProductCatalogServiceError("Product search is temporarily unavailable.") from exc
        except ValidationError as exc:
            logger.warning("Invalid product search contract")
            raise ProductCatalogServiceError(
                "Product search request or response is invalid."
            ) from exc

        if envelope.code != self._JAVA_SUCCESS_CODE or envelope.data is None:
            logger.warning("Java product search envelope rejected; code=%s", envelope.code)
            raise ProductCatalogServiceError("Product search is temporarily unavailable.")

        result = envelope.data
        if len(result.candidates) > query.limit:
            raise ProductCatalogServiceError("Product catalog returned too many products.")
        if query.category_id is None and not query.keywords:
            if (
                result.status != RecommendationStatus.NEEDS_CLARIFICATION
                or result.missing_criteria != [MissingCriterion.PRODUCT_CATEGORY]
            ):
                raise ProductCatalogServiceError("Product catalog returned an invalid response.")
        else:
            if result.status == RecommendationStatus.NEEDS_CLARIFICATION:
                raise ProductCatalogServiceError("Product catalog returned an invalid response.")
            if query.category_id is not None and any(
                candidate.category_id != query.category_id for candidate in result.candidates
            ):
                raise ProductCatalogServiceError("Product catalog returned a category mismatch.")

        context.result_collector.record_product_catalog_search(result)
        return result
