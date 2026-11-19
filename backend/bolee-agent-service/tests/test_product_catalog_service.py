"""Unit tests for the Java-backed catalog use case."""

import unittest
from typing import Any

from app.contexts.tool import AgentToolContext, AgentToolResultCollector
from app.services.product_catalog import ProductCatalogService, ProductCatalogServiceError


class FakeJavaClient:
    def __init__(self, responses: dict[str, Any]) -> None:
        self.responses = responses
        self.calls: list[tuple[str, dict[str, Any]]] = []

    def get_json(
        self,
        path: str,
        authorization_context: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        self.calls.append(
            (
                path,
                {
                    "authorization_context": authorization_context,
                    "params": params,
                },
            )
        )
        return self.responses[path]


class ProductCatalogServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = FakeJavaClient({})
        self.collector = AgentToolResultCollector()
        self.context = AgentToolContext(
            java_internal_api=self.client,
            authorization_context="a" * 43,
            result_collector=self.collector,
        )
        self.service = ProductCatalogService()

    def test_list_categories_accepts_java_camel_case_envelope(self) -> None:
        self.client.responses[self.service._CATEGORIES_PATH] = {
            "code": 200,
            "msg": "success",
            "operatorId": 123,
            "data": [
                {
                    "categoryId": "42",
                    "categoryCode": "001002003",
                    "categoryName": "意外险",
                }
            ],
        }

        categories = self.service.list_categories(self.context)

        self.assertEqual(categories[0].category_id, "42")
        self.assertEqual(categories[0].category_code, "001002003")
        self.assertEqual(self.client.calls[0][1]["authorization_context"], "a" * 43)

    def test_list_categories_accepts_java_snowflake_id(self) -> None:
        category_id = "1974472135232000001"
        self.client.responses[self.service._CATEGORIES_PATH] = {
            "code": 200,
            "data": [
                {
                    "categoryId": category_id,
                    "categoryCode": "001002003",
                    "categoryName": "意外险",
                }
            ],
        }

        categories = self.service.list_categories(self.context)

        self.assertEqual(categories[0].category_id, category_id)

    def test_search_uses_java_parameter_name_and_records_validated_candidates(self) -> None:
        self.client.responses[self.service._SEARCH_PATH] = {
            "code": 200,
            "msg": "success",
            "data": {
                "status": "MATCHED",
                "missingCriteria": [],
                "candidates": [
                    {
                        "productId": "product-1",
                        "name": "示例意外险",
                        "categoryId": "42",
                        "categoryCode": "001002003",
                        "categoryName": "意外险",
                        "matchReasons": ["属于客户选择的产品分类"],
                    }
                ],
            },
        }

        result = self.service.search_by_category("42", 2, self.context)

        self.assertEqual(result.candidates[0].product_id, "product-1")
        self.assertEqual(self.client.calls[0][1]["params"], {"limit": 2, "categoryId": "42"})
        self.assertEqual(self.collector.product_catalog_search, result)

    def test_search_rejects_candidate_from_another_category(self) -> None:
        self.client.responses[self.service._SEARCH_PATH] = {
            "code": 200,
            "data": {
                "status": "MATCHED",
                "missingCriteria": [],
                "candidates": [
                    {
                        "productId": "product-1",
                        "name": "示例意外险",
                        "categoryId": "99",
                        "categoryCode": "001002099",
                        "categoryName": "其他分类",
                    }
                ],
            },
        }

        with self.assertRaises(ProductCatalogServiceError):
            self.service.search_by_category("42", 3, self.context)
        self.assertIsNone(self.collector.product_catalog_search)

    def test_keyword_search_finds_only_java_returned_recommended_candidates(self) -> None:
        self.client.responses[self.service._SEARCH_PATH] = {
            "code": 200,
            "data": {
                "status": "MATCHED",
                "missingCriteria": [],
                "candidates": [
                    {
                        "productId": "product-1",
                        "name": "示例百万医疗险",
                        "categoryId": "42",
                        "categoryCode": "001002003",
                        "categoryName": "医疗险",
                        "matchReasons": ["关键词匹配管理员已开启推荐的产品信息"],
                        "productVersion": None,
                        "verificationStatus": "CATALOG_MATCH",
                        "facts": [],
                        "summary": "产品标签：百万医疗；产品描述：一般疾病医疗保障",
                    }
                ],
            },
        }

        result = self.service.search_by_category(None, 3, self.context, "百万医疗")

        self.assertEqual(result.candidates[0].product_id, "product-1")
        self.assertIn("百万医疗", result.candidates[0].summary or "")
        self.assertEqual(
            self.client.calls[0][1]["params"], {"limit": 3, "keyword": "百万医疗"}
        )
        self.assertEqual(self.collector.product_catalog_search, result)

    def test_multi_keyword_search_sends_all_requirements_to_java(self) -> None:
        self.client.responses[self.service._SEARCH_PATH] = {
            "code": 200,
            "data": {
                "status": "MATCHED",
                "missingCriteria": [],
                "candidates": [
                    {
                        "productId": "product-group-sports",
                        "name": "团体运动意外险",
                        "categoryId": "42",
                        "categoryCode": "001002003",
                        "categoryName": "意外险",
                        "matchReasons": ["产品标签匹配：团体运动"],
                        "summary": "面向团体运动活动的保险产品",
                    }
                ],
            },
        }

        result = self.service.search_by_category(
            None, 3, self.context, keywords=["团体", "运动", "意外险"]
        )

        self.assertEqual(result.candidates[0].product_id, "product-group-sports")
        self.assertEqual(
            self.client.calls[0][1]["params"],
            {"limit": 3, "keywords": ["团体", "运动", "意外险"]},
        )

    def test_search_rejects_limit_above_java_bound_before_calling_java(self) -> None:
        with self.assertRaises(ProductCatalogServiceError):
            self.service.search_by_category("42", 4, self.context)
        self.assertEqual(self.client.calls, [])


if __name__ == "__main__":
    unittest.main()
