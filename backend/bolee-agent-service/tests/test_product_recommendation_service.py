"""Focused tests for the Java recommendation client and use case."""

import unittest
from decimal import Decimal
from typing import Any
from unittest.mock import Mock

import httpx

from app.clients.java_internal_api import JavaInternalApiClient
from app.contexts.tool import AgentToolContext, AgentToolResultCollector
from app.schemas.product_catalog import ProductCatalogSearchResponse
from app.schemas.product_recommendation import (
    ProductRecommendationResponse,
    ProductRecommendationStatus,
)
from app.services.product_recommendation import (
    ProductRecommendationService,
    ProductRecommendationServiceError,
)

CRITERIA_PATH = "/internal/agent-tools/v1/products/recommendation-criteria"
RECOMMENDATIONS_PATH = "/internal/agent-tools/v1/products/recommendations"


def ready_catalog() -> dict[str, Any]:
    return {
        "code": 200,
        "data": {
            "status": "READY",
            "categoryId": "42",
            "catalogVersion": "catalog-2026-11-15-1",
            "ruleVersion": 3,
            "criteria": [
                {
                    "code": "insured_group_type",
                    "definitionVersion": 1,
                    "name": "承保对象",
                    "valueType": "ENUM",
                    "unit": None,
                    "allowedOperators": ["EQ"],
                    "allowedUses": ["HARD_FILTER"],
                    "activeRuleMode": "HARD_FILTER",
                    "requiredForClarification": True,
                    "options": [{"valueCode": "GROUP", "displayName": "团体"}],
                },
                {
                    "code": "waiting_period_days",
                    "definitionVersion": 2,
                    "name": "等待期",
                    "valueType": "NUMBER",
                    "unit": "DAY",
                    "allowedOperators": ["LTE", "RANGE"],
                    "allowedUses": ["PREFERENCE_SCORE", "COMPARE_ONLY"],
                    "activeRuleMode": "PREFERENCE_SCORE",
                    "requiredForClarification": False,
                    "options": [],
                },
                {
                    "code": "coverage_limit",
                    "definitionVersion": 1,
                    "name": "保障额度",
                    "valueType": "NUMBER",
                    "unit": "CNY",
                    "allowedOperators": [],
                    "allowedUses": ["COMPARE_ONLY"],
                    "activeRuleMode": "COMPARE_ONLY",
                    "requiredForClarification": False,
                    "options": [],
                },
            ],
        },
    }


def recommendation_response(request_id: str) -> dict[str, Any]:
    return {
        "code": 200,
        "data": {
            "requestId": request_id,
            "status": "MATCHED",
            "evaluationCompleteness": "COMPLETE",
            "missingCriteria": [],
            "unsupportedCriteria": [],
            "unresolvedCriteria": [],
            "excludedProductCount": 0,
            "candidates": [
                {
                    "productId": "9001",
                    "name": "团体意外险",
                    "categoryId": "42",
                    "productVersion": 1,
                    "verificationStatus": "VERIFIED_MATCH",
                    "matchReasons": ["符合团体承保条件"],
                    "facts": [
                        {
                            "code": "waiting_period_days",
                            "definitionVersion": 2,
                            "name": "等待期",
                            "valueType": "NUMBER",
                            "unit": "DAY",
                            "valueCode": None,
                            "valueCodes": [],
                            "valueNumber": 0,
                            "minValue": None,
                            "maxValue": None,
                            "valueStatus": "KNOWN",
                        },
                        {
                            "code": "coverage_limit",
                            "definitionVersion": 1,
                            "name": "保障额度",
                            "valueType": "NUMBER",
                            "unit": "CNY",
                            "valueCode": None,
                            "valueCodes": [],
                            "valueNumber": 100000,
                            "minValue": None,
                            "maxValue": None,
                            "valueStatus": "KNOWN",
                        },
                    ],
                }
            ],
            "evaluatedAt": "2026-11-15T03:00:00Z",
            "catalogVersion": "catalog-2026-11-15-1",
            "ruleVersion": 3,
            "engineVersion": "recommendation-engine-v1",
        },
    }


class FakeJavaClient:
    def __init__(self) -> None:
        self.get_response: dict[str, Any] = ready_catalog()
        self.post_response: dict[str, Any] | None = None
        self.get_calls: list[dict[str, Any]] = []
        self.post_calls: list[dict[str, Any]] = []

    def get_json(
        self,
        path: str,
        authorization_context: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        self.get_calls.append(
            {"path": path, "authorization_context": authorization_context, "params": params}
        )
        return self.get_response

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


class ProductRecommendationServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = FakeJavaClient()
        self.context = AgentToolContext(
            java_internal_api=self.client,
            authorization_context="grant-token",
            result_collector=AgentToolResultCollector(),
        )
        self.service = ProductRecommendationService()
        self.request = {
            "requestId": "11111111-1111-4111-8111-111111111111",
            "turnId": "22222222-2222-4222-8222-222222222222",
            "criteria": {
                "categoryId": "42",
                "items": [
                    {
                        "code": "insured_group_type",
                        "operator": "EQ",
                        "valueCode": "GROUP",
                        "customerIntent": "MUST_HAVE",
                    },
                    {
                        "code": "waiting_period_days",
                        "operator": "LTE",
                        "numberValue": 0,
                        "unit": "DAY",
                        "customerIntent": "PREFERENCE",
                    },
                ],
            },
            "limit": 2,
        }
        self.client.post_response = recommendation_response(self.request["requestId"])

    def test_criteria_catalog_accepts_nineteen_digit_snowflake_id(self) -> None:
        category_id = "1234567890123456789"
        self.client.get_response["data"]["categoryId"] = category_id

        result = self.service.get_criteria_catalog(category_id, self.context)

        self.assertEqual(result.category_id, category_id)

    def test_latest_tool_result_replaces_older_business_result(self) -> None:
        catalog_result = ProductCatalogSearchResponse.model_validate(
            {
                "status": "MATCHED",
                "candidates": [
                    {
                        "productId": "catalog-product",
                        "name": "目录产品",
                        "categoryId": "42",
                        "categoryCode": "001002003",
                        "categoryName": "医疗险",
                    }
                ],
            }
        )
        self.context.result_collector.record_product_catalog_search(catalog_result)
        self.context.result_collector.record_product_recommendation(
            ProductRecommendationResponse.model_validate(
                recommendation_response(self.request["requestId"])["data"]
            )
        )

        self.assertIsNone(self.context.result_collector.product_catalog_search)
        self.assertEqual(
            self.context.result_collector.product_recommendation.candidates[0].product_id,
            "9001",
        )

        self.context.result_collector.record_product_catalog_search(catalog_result)
        self.assertIsNone(self.context.result_collector.product_recommendation)

    def test_recommend_posts_camel_case_request_and_validates_java_result(self) -> None:
        result = self.service.recommend(self.request, self.context)

        self.assertEqual(result.status, ProductRecommendationStatus.MATCHED)
        self.assertEqual(result.candidates[0].product_id, "9001")
        self.assertEqual(self.client.get_calls[0]["path"], CRITERIA_PATH)
        self.assertEqual(self.client.get_calls[0]["params"], {"categoryId": "42"})
        self.assertEqual(self.client.get_calls[0]["authorization_context"], "grant-token")
        post_call = self.client.post_calls[0]
        self.assertEqual(post_call["path"], RECOMMENDATIONS_PATH)
        self.assertEqual(post_call["authorization_context"], "grant-token")
        body = post_call["json_body"]
        self.assertEqual(body["requestId"], self.request["requestId"])
        self.assertEqual(body["criteria"]["categoryId"], "42")
        self.assertEqual(body["criteria"]["items"][0]["customerIntent"], "MUST_HAVE")
        self.assertNotIn("customer_intent", body["criteria"]["items"][0])
        self.assertEqual(body["criteria"]["items"][1]["numberValue"], Decimal("0"))
        self.assertEqual(
            self.context.result_collector.product_recommendation.candidates[0].product_id,
            "9001",
        )

    def test_unpublished_catalog_returns_not_ready_without_posting(self) -> None:
        self.client.get_response = {
            "code": 200,
            "data": {
                "status": "NOT_PUBLISHED",
                "categoryId": "42",
                "catalogVersion": None,
                "ruleVersion": None,
                "criteria": [],
            },
        }

        result = self.service.recommend(self.request, self.context)

        self.assertEqual(result.status, ProductRecommendationStatus.CATALOG_NOT_READY)
        self.assertEqual(self.client.post_calls, [])
        self.assertEqual(
            self.context.result_collector.product_recommendation.status,
            ProductRecommendationStatus.CATALOG_NOT_READY,
        )

    def test_failed_recommendation_does_not_leave_stale_catalog_candidates(self) -> None:
        self.context.result_collector.record_product_catalog_search(
            ProductCatalogSearchResponse.model_validate(
                {
                    "status": "MATCHED",
                    "candidates": [
                        {
                            "productId": "stale-product",
                            "name": "旧目录结果",
                            "categoryId": "42",
                            "categoryCode": "001002003",
                            "categoryName": "医疗险",
                        }
                    ],
                }
            )
        )
        self.client.get_response = {"code": 503, "data": None}

        with self.assertRaises(ProductRecommendationServiceError):
            self.service.recommend(self.request, self.context)

        self.assertIsNone(self.context.result_collector.product_catalog_search)
        self.assertIsNone(self.context.result_collector.product_recommendation)

    def test_unsupported_rule_mode_is_returned_without_posting(self) -> None:
        catalog = ready_catalog()
        catalog["data"]["criteria"][1]["activeRuleMode"] = "COMPARE_ONLY"
        catalog["data"]["criteria"][1]["allowedUses"] = ["COMPARE_ONLY"]
        self.client.get_response = catalog

        result = self.service.recommend(self.request, self.context)

        self.assertEqual(result.status, ProductRecommendationStatus.UNSUPPORTED_CRITERIA)
        self.assertEqual(result.unsupported_criteria, ["waiting_period_days"])
        self.assertEqual(self.client.post_calls, [])
        self.assertEqual(
            self.context.result_collector.product_recommendation.status,
            ProductRecommendationStatus.UNSUPPORTED_CRITERIA,
        )

    def test_unpublished_enum_option_is_rejected_before_post(self) -> None:
        request = self.request.copy()
        request["criteria"] = {**self.request["criteria"], "items": [
            {**self.request["criteria"]["items"][0], "valueCode": "INDIVIDUAL"}
        ]}

        with self.assertRaises(ProductRecommendationServiceError):
            self.service.recommend(request, self.context)
        self.assertEqual(self.client.post_calls, [])

    def test_catalog_version_change_between_read_and_recommendation_is_rejected(self) -> None:
        changed = recommendation_response(self.request["requestId"])
        changed["data"]["catalogVersion"] = "catalog-newer"
        self.client.post_response = changed

        with self.assertRaisesRegex(ProductRecommendationServiceError, "catalog changed"):
            self.service.recommend(self.request, self.context)


class JavaInternalApiClientPostTests(unittest.TestCase):
    def test_post_uses_auth_headers_and_exact_recommendation_allowlist(self) -> None:
        captured: list[httpx.Request] = []

        def handler(request: httpx.Request) -> httpx.Response:
            captured.append(request)
            return httpx.Response(200, json={"code": 200, "data": {"ok": True}})

        oauth_client = Mock()
        oauth_client.get_access_token.return_value = "access-token"
        client = JavaInternalApiClient("http://java:17065", oauth_client)
        client._client.close()
        client._client = httpx.Client(transport=httpx.MockTransport(handler))
        try:
            result = client.post_json(
                RECOMMENDATIONS_PATH,
                authorization_context="grant-token",
                json_body={"requestId": "request-1", "numberValue": Decimal("0.1")},
            )
            self.assertEqual(result["data"], {"ok": True})
            self.assertEqual(captured[0].method, "POST")
            self.assertEqual(captured[0].headers["Authorization"], "Bearer access-token")
            self.assertEqual(captured[0].headers["X-Agent-Authorization-Context"], "grant-token")
            self.assertEqual(
                captured[0].read(),
                b'{"requestId":"request-1","numberValue":0.1}',
            )
            with self.assertRaises(ValueError):
                client.post_json(
                    "/internal/agent-tools/v1/other/write",
                    authorization_context="grant-token",
                    json_body={},
                )
        finally:
            client._client.close()


if __name__ == "__main__":
    unittest.main()
