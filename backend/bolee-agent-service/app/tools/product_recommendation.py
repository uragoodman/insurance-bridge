"""Agno tools for Java-backed, rule-validated product recommendations."""

from typing import Any
from uuid import uuid4

from agno.run import RunContext
from agno.tools import Toolkit
from pydantic import ValidationError

from app.schemas.product_recommendation import (
    ProductRecommendationCriteria,
    ProductRecommendationRequest,
)
from app.services.product_recommendation import (
    ProductRecommendationService,
    ProductRecommendationServiceError,
)
from app.tools.payload import bounded_tool_payload
from app.tools.runtime import AgentToolContextError, resolve_tool_context


class ProductRecommendationTools(Toolkit):
    def __init__(self, service: ProductRecommendationService | None = None, **kwargs: Any) -> None:
        self._service = service or ProductRecommendationService()
        super().__init__(
            name="insurance_product_recommendation",
            tools=[self.get_product_recommendation_criteria, self.recommend_products],
            instructions=(
                "For ordinary product recommendation, first use insurance_product_catalog to fetch "
                "products the administrator enabled. The separate structured rule engine is only "
                "for requests that require explicit criteria matching. Use "
                "get_product_recommendation_criteria after the customer selects a category. "
                "Only use criterion codes, allowed operators, options, and units returned by that "
                "tool. Ask the customer for required missing information before calling "
                "recommend_products. Java controls candidate selection and product facts."
            ),
            add_instructions=True,
            **kwargs,
        )

    def get_product_recommendation_criteria(
        self,
        run_context: RunContext,
        category_id: str,
    ) -> dict[str, Any]:
        """Get Java's currently published criteria and allowed values for one product category.

        Call list_product_categories first and use a returned category ID. A non-READY result means
        recommendation criteria are not published; do not invent criteria or claim products match.
        """
        try:
            tool_context = resolve_tool_context(run_context)
            tool_context.result_collector.clear()
            catalog = self._service.get_criteria_catalog(
                category_id,
                tool_context,
            )
        except (AgentToolContextError, ProductRecommendationServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            catalog.model_dump(mode="json"), tool_context.result_collector
        )

    def recommend_products(
        self,
        run_context: RunContext,
        criteria: ProductRecommendationCriteria,
        limit: int = 3,
    ) -> dict[str, Any]:
        """Evaluate structured customer needs against Java's published product rules.

        Build criteria from a READY catalog returned by get_product_recommendation_criteria. Each
        item must use a returned criterion code, allowed operator and option code; set customer
        intent to MUST_HAVE for a requirement or PREFERENCE for a desired feature. Do not include
        personal, medical, payment, or policyholder data. NEEDS_CLARIFICATION or other non-match
        statuses are outcomes to explain to the customer, not permission to guess alternatives.
        """
        try:
            tool_context = resolve_tool_context(run_context)
            tool_context.result_collector.clear()
            request = ProductRecommendationRequest(
                request_id=str(uuid4()),
                criteria=criteria,
                limit=limit,
            )
            result = self._service.recommend(request, tool_context)
        except ValidationError as exc:
            raise RuntimeError("Product recommendation request is invalid.") from exc
        except (AgentToolContextError, ProductRecommendationServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            result.model_dump(mode="json"), tool_context.result_collector
        )
