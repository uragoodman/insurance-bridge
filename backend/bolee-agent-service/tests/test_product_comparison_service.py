"""Focused tests for the HTTP comparison client, contract validation, and result capture."""

import unittest
from typing import Any

from pydantic import ValidationError

from app.contexts.tool import AgentToolContext, AgentToolResultCollector
from app.schemas.product_comparison import (
    ProductComparisonRequest,
    ProductComparisonResponse,
    ProductComparisonStatus,
)
from app.services.product_comparison import (
    ProductComparisonService,
    ProductComparisonServiceError,
)

COMPARISON_PATH = "/internal/agent-tools/v1/products/compare"
REQUEST_ID = "11111111-1111-4111-8111-111111111111"


def response(request_id: str = REQUEST_ID) -> dict[str, Any]:
    return {
        "code": 200,
        "data": {
            "requestId": request_id,
            "status": "COMPLETE",
            "categoryId": "42",
            "catalogVersion": "catalog-v1",
            "ruleVersion": 3,
            "products": [
                {
                    "productId": "9001",
                    "name": "团体意外险A",
                    "productVersion": 1,
                    "facts": [
                        {
                            "code": "coverage_amount",
                            "definitionVersion": 1,
                            "name": "保障额度",
                            "valueType": "NUMBER",
                            "unit": "CNY",
                            "valueNumber": 100000,
                            "valueDisplay": "100000 元",
                            "valueStatus": "KNOWN",
                        }
                    ],
                },
                {
                    "productId": "9002",
                    "name": "团体意外险B",
                    "productVersion": 2,
                    "facts": [
                        {
                            "code": "coverage_amount",
                            "definitionVersion": 1,
                            "name": "保障额度",
                            "valueType": "NUMBER",
                            "unit": "CNY",
                            "valueNumber": 250000,
                            "valueStatus": "KNOWN",
                        }
                    ],
                },
            ],
            "unresolvedCriteria": [],
            "comparedAt": "2026-11-15T03:00:00Z",
        },
    }


class FakeJavaClient:
    def __init__(self) -> None:
        self.post_response = response()
        self.post_calls: list[dict[str, Any]] = []

    def post_json(
        self,
        path: str,
        authorization_context: str,
        json_body: dict[str, Any],
    ) -> Any:
        self.post_calls.append(
            {"path": path, "authorization_context": authorization_context, "json_body": json_body}
        )
        return self.post_response


class ProductComparisonServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = FakeJavaClient()
        self.collector = AgentToolResultCollector()
        self.context = AgentToolContext(self.client, "grant-token", self.collector)
        self.service = ProductComparisonService()

    def test_posts_multiple_ids_and_records_java_facts(self) -> None:
        request = ProductComparisonRequest(
            request_id=REQUEST_ID,
            category_id="42",
            product_ids=["9001", "9002"],
        )

        result = self.service.compare(request, self.context)

        self.assertEqual(self.client.post_calls[0]["path"], COMPARISON_PATH)
        self.assertEqual(self.client.post_calls[0]["authorization_context"], "grant-token")
        self.assertEqual(self.client.post_calls[0]["json_body"]["productIds"], ["9001", "9002"])
        self.assertEqual(result.products[1].facts[0].value_number, 250000)
        self.assertEqual(result.products[0].facts[0].value_display, "100000 元")
        self.assertIs(self.collector.product_comparison, result)

    def test_rejects_java_response_with_a_different_product_set(self) -> None:
        self.client.post_response["data"]["products"].reverse()
        request = ProductComparisonRequest(
            request_id=REQUEST_ID,
            category_id="42",
            product_ids=["9001", "9002"],
        )

        with self.assertRaises(ProductComparisonServiceError):
            self.service.compare(request, self.context)

    def test_request_requires_two_to_ten_distinct_products(self) -> None:
        with self.assertRaises(ValidationError):
            ProductComparisonRequest(
                request_id=REQUEST_ID,
                category_id="42",
                product_ids=["9001"],
            )
        with self.assertRaises(ValidationError):
            ProductComparisonRequest(
                request_id=REQUEST_ID,
                category_id="42",
                product_ids=["9001", "09001"],
            )
        with self.assertRaises(ValidationError):
            ProductComparisonRequest(
                request_id=REQUEST_ID,
                category_id="42",
                product_ids=["9001", "١"],
            )

    def test_comparison_contract_rejects_inconsistent_fact_columns(self) -> None:
        raw = response()["data"]
        raw["products"][1]["facts"] = []
        with self.assertRaises(ValidationError):
            ProductComparisonResponse.model_validate(raw)

    def test_not_ready_response_has_no_products(self) -> None:
        raw = response()["data"]
        raw.update(
            {
                "status": "CATALOG_NOT_READY",
                "products": [],
                "unresolvedCriteria": [],
                "catalogVersion": None,
                "ruleVersion": None,
            }
        )
        result = ProductComparisonResponse.model_validate(raw)
        self.assertEqual(result.status, ProductComparisonStatus.CATALOG_NOT_READY)


if __name__ == "__main__":
    unittest.main()
