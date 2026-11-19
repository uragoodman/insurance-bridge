"""Pydantic wire schemas used by the Python service."""

from app.schemas.chat import ChatRequest, ChatResponse
from app.schemas.java import JavaResponseEnvelope
from app.schemas.product_catalog import (
    MissingCriterion,
    ProductCandidate,
    ProductCatalogSearchQuery,
    ProductCatalogSearchResponse,
    ProductCategory,
    RecommendationStatus,
)
from app.schemas.product_comparison import (
    ProductComparisonProduct,
    ProductComparisonRequest,
    ProductComparisonResponse,
    ProductComparisonStatus,
)
from app.schemas.product_recommendation import (
    CriteriaCatalogStatus,
    CustomerIntent,
    EvaluationCompleteness,
    ProductFactStatus,
    ProductRecommendationCandidate,
    ProductRecommendationCriteria,
    ProductRecommendationCriteriaCatalog,
    ProductRecommendationRequest,
    ProductRecommendationResponse,
    ProductRecommendationStatus,
    RecommendationCriterion,
    RecommendationCriterionInput,
    RecommendationFact,
    RecommendationOption,
    RecommendationRuleMode,
    RecommendationUnresolvedCriterion,
    RecommendationValueType,
)
from app.schemas.product_search_tags import (
    GeneratedProductSearchTag,
    ProductSearchTagGenerationOutput,
    ProductSearchTagInput,
)

__all__ = [
    "ChatRequest",
    "ChatResponse",
    "JavaResponseEnvelope",
    "MissingCriterion",
    "ProductCandidate",
    "ProductCatalogSearchQuery",
    "ProductCatalogSearchResponse",
    "ProductCategory",
    "RecommendationStatus",
    "CriteriaCatalogStatus",
    "CustomerIntent",
    "EvaluationCompleteness",
    "ProductFactStatus",
    "ProductRecommendationCandidate",
    "ProductRecommendationCriteria",
    "ProductRecommendationCriteriaCatalog",
    "ProductRecommendationRequest",
    "ProductRecommendationResponse",
    "ProductRecommendationStatus",
    "RecommendationCriterion",
    "RecommendationCriterionInput",
    "RecommendationFact",
    "RecommendationOption",
    "RecommendationRuleMode",
    "RecommendationUnresolvedCriterion",
    "RecommendationValueType",
    "ProductComparisonProduct",
    "ProductComparisonRequest",
    "ProductComparisonResponse",
    "ProductComparisonStatus",
    "GeneratedProductSearchTag",
    "ProductSearchTagGenerationOutput",
    "ProductSearchTagInput",
]
