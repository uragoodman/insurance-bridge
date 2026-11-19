"""Agno tool for authoritative Java-backed multi-product comparisons."""

from typing import Any
from uuid import uuid4

from agno.run import RunContext
from agno.tools import Toolkit
from pydantic import ValidationError

from app.schemas.product_comparison import ProductComparisonRequest
from app.services.product_comparison import (
    ProductComparisonService,
    ProductComparisonServiceError,
)
from app.tools.payload import bounded_tool_payload
from app.tools.runtime import AgentToolContextError, resolve_tool_context


class ProductComparisonTools(Toolkit):
    def __init__(self, service: ProductComparisonService | None = None, **kwargs: Any) -> None:
        self._service = service or ProductComparisonService()
        super().__init__(
            name="insurance_product_comparison",
            tools=[self.compare_products],
            instructions=(
                "Compare only 2 to 10 distinct product IDs returned by Java tools in this "
                "conversation, and use their Java-returned category ID. Java returns the "
                "authoritative facts and missing-data markers; do not infer or fill missing values."
            ),
            add_instructions=True,
            **kwargs,
        )

    def compare_products(
        self,
        run_context: RunContext,
        category_id: str,
        product_ids: list[str],
    ) -> dict[str, Any]:
        """Compare 2 to 10 products previously returned by Java's product tools.

        Each product must be in the current published snapshot for category_id. The result contains
        the same Java-approved comparison fields for each product and marks missing
        facts explicitly.
        """
        try:
            tool_context = resolve_tool_context(run_context)
            tool_context.result_collector.clear()
            request = ProductComparisonRequest(
                request_id=str(uuid4()),
                category_id=category_id,
                product_ids=product_ids,
            )
            result = self._service.compare(request, tool_context)
        except ValidationError as exc:
            raise RuntimeError("Product comparison request is invalid.") from exc
        except (AgentToolContextError, ProductComparisonServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            result.model_dump(mode="json"), tool_context.result_collector
        )
