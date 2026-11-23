"""Agno tool for version-scoped, evidence-grounded insurance terms lookup."""

from typing import Any

from agno.run import RunContext
from agno.tools import Toolkit

from app.services.terms.retrieval import InsuranceTermsService
from app.tools.payload import bounded_tool_payload
from app.tools.runtime import AgentToolContextError, resolve_tool_context


class InsuranceTermsTools(Toolkit):
    def __init__(self, service: InsuranceTermsService | None = None, **kwargs: Any) -> None:
        self._service = service or InsuranceTermsService()
        super().__init__(
            name="insurance_product_terms",
            tools=[self.search_product_terms],
            instructions=(
                "Use this tool for questions about official policy coverage, exclusions, waiting "
                "periods, claims, or duties. Pass the product name and the customer's question. "
                "Do not add policy facts beyond the returned answer and citations. Ask for product "
                "or version clarification when the tool requests it."
            ),
            add_instructions=True,
            **kwargs,
        )

    def search_product_terms(
        self, run_context: RunContext, product_name: str, question: str
    ) -> dict[str, Any]:
        """Search the official terms for one product and answer with source citations."""
        try:
            context = resolve_tool_context(run_context)
            result = self._service.search(product_name, question, context)
        except AgentToolContextError as exc:
            raise RuntimeError(str(exc)) from exc
        return bounded_tool_payload(result.model_dump(mode="json"), context.result_collector)


__all__ = ["InsuranceTermsTools"]
