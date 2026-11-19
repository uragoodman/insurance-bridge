"""Agno tools for safe, read-only Java product catalog access."""

from typing import Any

from agno.run import RunContext
from agno.tools import Toolkit

from app.services.product_catalog import ProductCatalogService, ProductCatalogServiceError
from app.tools.payload import bounded_tool_payload
from app.tools.runtime import AgentToolContextError, resolve_tool_context


class ProductCatalogTools(Toolkit):
    def __init__(self, service: ProductCatalogService | None = None, **kwargs: Any) -> None:
        self._service = service or ProductCatalogService()
        super().__init__(
            name="insurance_product_catalog",
            tools=[self.list_product_categories, self.search_products_by_category],
            instructions=(
                "Discover categories when the customer has not provided a product keyword "
                "or category. Extract a few concrete product, audience, and usage terms from "
                "the customer's request and pass them as keywords. A product keyword can be "
                "searched directly, with or "
                "without a category. Java returns only products explicitly enabled for AI "
                "recommendation. Recommend only returned candidates and ground product claims "
                "in their summary. If a coverage detail is absent from the summary, say it is "
                "not available; do not infer policy terms. "
                "Catalog results are not a quote or an eligibility decision."
            ),
            add_instructions=True,
            **kwargs,
        )

    def list_product_categories(
        self, run_context: RunContext
    ) -> list[dict[str, str]] | dict[str, str]:
        """List the current Java product categories. Use returned category IDs only."""
        try:
            tool_context = resolve_tool_context(run_context)
            tool_context.result_collector.clear()
            categories = self._service.list_categories(tool_context)
        except (AgentToolContextError, ProductCatalogServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            [category.model_dump(mode="json") for category in categories],
            tool_context.result_collector,
        )

    def search_products_by_category(
        self,
        run_context: RunContext,
        category_id: str | None = None,
        keyword: str | None = None,
        limit: int = 3,
        keywords: list[str] | None = None,
    ) -> dict[str, Any]:
        """Search up to three enabled products by category and/or multiple product keywords.

        Use a category ID from list_product_categories when available. Pass concise extracted
        requirements as keywords, such as audience, product type, and scenario. A keyword can search
        product names, registration numbers, labels, and descriptions, optionally within a
        category. If both are omitted, Java asks for a category. This tool does not quote premiums
        or determine eligibility.
        """
        try:
            tool_context = resolve_tool_context(run_context)
            tool_context.result_collector.clear()
            result = self._service.search_by_category(
                category_id,
                limit,
                tool_context,
                keyword,
                keywords,
            )
        except (AgentToolContextError, ProductCatalogServiceError) as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(
            result.model_dump(mode="json"), tool_context.result_collector
        )
