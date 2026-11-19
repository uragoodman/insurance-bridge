"""Allowlist of Agno tools backed by implemented Java APIs."""

from agno.tools import Toolkit

from app.tools.own_policy import OwnPolicyTools
from app.tools.product_catalog import ProductCatalogTools
from app.tools.product_comparison import ProductComparisonTools
from app.tools.product_recommendation import ProductRecommendationTools


def build_agent_tools() -> list[Toolkit]:
    return [
        ProductCatalogTools(),
        ProductRecommendationTools(),
        ProductComparisonTools(),
        OwnPolicyTools(),
    ]
