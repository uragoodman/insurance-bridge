"""Per-run business-tool context and trusted result collector."""

import re
from dataclasses import dataclass

from app.schemas.own_policy import OwnPolicyDetailResponse, OwnPolicySummaryResponse
from app.schemas.product_catalog import ProductCatalogSearchResponse
from app.schemas.product_comparison import ProductComparisonResponse
from app.schemas.product_recommendation import ProductRecommendationResponse
from app.services.ports import JavaInternalApiPort


@dataclass(frozen=True, slots=True)
class AgentToolContext:
    java_internal_api: JavaInternalApiPort
    authorization_context: str
    result_collector: "AgentToolResultCollector"
    selected_policy_id: str | None = None


@dataclass(slots=True)
class AgentToolResultCollector:
    """Keep only the most recently completed trusted business-tool result per run."""

    context_result_token_limit: int = 8192
    context_result_tokens_used: int = 0
    product_catalog_search: ProductCatalogSearchResponse | None = None
    product_recommendation: ProductRecommendationResponse | None = None
    product_comparison: ProductComparisonResponse | None = None
    own_policy_summary: OwnPolicySummaryResponse | None = None
    own_policy_detail: OwnPolicyDetailResponse | None = None

    @property
    def sensitive_tool_used(self) -> bool:
        """Whether this run accessed owner-specific policy information."""
        return self.own_policy_summary is not None or self.own_policy_detail is not None

    @property
    def context_references(self) -> tuple[str, ...]:
        """Expose only Java-validated product IDs and versions for later re-querying."""
        references: list[str] = []
        results = (
            (self.product_catalog_search, "candidates"),
            (self.product_recommendation, "candidates"),
            (self.product_comparison, "products"),
        )
        for result, collection_name in results:
            if result is None:
                continue
            catalog_version = getattr(result, "catalog_version", None)
            for product in getattr(result, collection_name, []):
                product_id = getattr(product, "product_id", None)
                product_version = getattr(product, "product_version", None)
                if (
                    not isinstance(product_id, str)
                    or re.fullmatch(r"[A-Za-z0-9_-]{1,64}", product_id) is None
                ):
                    continue
                reference = f"product_id={product_id}"
                if isinstance(product_version, int) and product_version > 0:
                    reference += f";product_version={product_version}"
                if (
                    isinstance(catalog_version, str)
                    and re.fullmatch(r"[A-Za-z0-9._-]{1,64}", catalog_version)
                ):
                    reference += f";catalog_version={catalog_version}"
                if reference not in references:
                    references.append(reference)
        return tuple(references[-10:])

    def clear(self) -> None:
        self.product_catalog_search = None
        self.product_recommendation = None
        self.product_comparison = None
        self.own_policy_summary = None
        self.own_policy_detail = None

    def reserve_context_result_tokens(self, token_count: int) -> bool:
        """Bound cumulative Java tool payloads for this Agent run."""
        if token_count < 0:
            return False
        if self.context_result_tokens_used + token_count > self.context_result_token_limit:
            return False
        self.context_result_tokens_used += token_count
        return True

    def record_product_catalog_search(self, result: ProductCatalogSearchResponse) -> None:
        self.product_catalog_search = result
        self.product_recommendation = None
        self.product_comparison = None
        self.own_policy_summary = None
        self.own_policy_detail = None

    def record_product_recommendation(self, result: ProductRecommendationResponse) -> None:
        self.product_recommendation = result
        self.product_catalog_search = None
        self.product_comparison = None
        self.own_policy_summary = None
        self.own_policy_detail = None

    def record_product_comparison(self, result: ProductComparisonResponse) -> None:
        self.product_comparison = result
        self.product_catalog_search = None
        self.product_recommendation = None
        self.own_policy_summary = None
        self.own_policy_detail = None

    def record_own_policy_summary(self, result: OwnPolicySummaryResponse) -> None:
        self.product_catalog_search = None
        self.product_recommendation = None
        self.product_comparison = None
        self.own_policy_summary = result
        self.own_policy_detail = None

    def record_own_policy_detail(self, result: OwnPolicyDetailResponse) -> None:
        self.product_catalog_search = None
        self.product_recommendation = None
        self.product_comparison = None
        self.own_policy_summary = None
        self.own_policy_detail = result
