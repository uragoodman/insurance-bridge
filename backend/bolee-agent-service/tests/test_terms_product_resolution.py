"""Terms lookup must stop when Java finds only a similar product candidate."""

import unittest
from unittest.mock import Mock

from app.contexts.tool import AgentToolContext, AgentToolResultCollector
from app.schemas.product_catalog import (
    ProductCandidate,
    ProductCatalogSearchResponse,
    ProductMatchType,
    RecommendationStatus,
)
from app.services.terms.retrieval import InsuranceTermsService


class FakeCatalogService:
    def __init__(self, result: ProductCatalogSearchResponse) -> None:
        self.result = result

    def search_by_category(self, **_kwargs: object) -> ProductCatalogSearchResponse:
        return self.result


class TermsProductResolutionTests(unittest.TestCase):
    def test_related_product_requires_confirmation_before_terms_vector_search(self) -> None:
        catalog = FakeCatalogService(
            ProductCatalogSearchResponse(
                status=RecommendationStatus.MATCHED,
                candidates=[
                    ProductCandidate(
                        product_id="7001",
                        name="平安境内旅游险",
                        category_id="42",
                        category_code="001002003",
                        category_name="旅游险",
                        match_type=ProductMatchType.RELATED,
                    )
                ],
            )
        )
        java_client = Mock()
        repository = Mock()
        embeddings = Mock()
        answer_generator = Mock()
        context = AgentToolContext(
            java_internal_api=java_client,
            authorization_context="opaque-grant",
            result_collector=AgentToolResultCollector(),
        )
        service = InsuranceTermsService(
            catalog_service=catalog,  # type: ignore[arg-type]
            repository=repository,
            embedding_provider=embeddings,
            answer_generator=answer_generator,
        )

        result = service.search("境内旅游保险", "航班延误责任是什么？", context)

        self.assertEqual(result.status, "NEEDS_PRODUCT_CLARIFICATION")
        self.assertIn("平安境内旅游险", result.answer)
        java_client.get_json.assert_not_called()
        repository.search.assert_not_called()
        embeddings.embed.assert_not_called()
        answer_generator.generate.assert_not_called()


if __name__ == "__main__":
    unittest.main()
