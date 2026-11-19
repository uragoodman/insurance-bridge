"""Tests for the customer-facing structured recommendation contract."""

import unittest

from pydantic import ValidationError

from app.schemas.chat import ChatResponse


class ChatResponseContractTests(unittest.TestCase):
    def test_chat_response_can_carry_single_own_policy_detail(self) -> None:
        response = ChatResponse.model_validate(
            {
                "session_id": "session-1",
                "run_id": "run-1",
                "answer": "该保单目前处于保障中。",
                "own_policy_detail": {
                    "status": "FOUND",
                    "as_of": "2026-11-17T10:00:00Z",
                    "policy": {
                        "policy_id": "9001",
                        "product_name": "个人意外险",
                        "policy_status": "IN_FORCE",
                        "policy_status_label": "保障中",
                    },
                },
            }
        )
        self.assertEqual(response.own_policy_detail.policy.policy_id, "9001")

    def test_rejects_policy_detail_mixed_with_product_recommendations(self) -> None:
        with self.assertRaises(ValidationError):
            ChatResponse.model_validate(
                {
                    "session_id": "session-1",
                    "run_id": "run-1",
                    "answer": "回答",
                    "own_policy_detail": {
                        "status": "NOT_FOUND",
                        "as_of": "2026-11-17T10:00:00Z",
                    },
                    "recommendations": [{"product_id": "p1", "name": "产品"}],
                }
            )

    def test_chat_response_can_carry_structured_product_comparison(self) -> None:
        response = ChatResponse.model_validate(
            {
                "session_id": "session-1",
                "run_id": "run-1",
                "answer": "两款产品的保障额度分别如下。",
                "product_comparison": {
                    "request_id": "11111111-1111-4111-8111-111111111111",
                    "status": "COMPLETE",
                    "category_id": "42",
                    "catalog_version": "catalog-v1",
                    "rule_version": 3,
                    "products": [
                        {"product_id": "9001", "name": "产品 A", "product_version": 1, "facts": []},
                        {"product_id": "9002", "name": "产品 B", "product_version": 1, "facts": []},
                    ],
                    "unresolved_criteria": [],
                },
            }
        )
        self.assertEqual(len(response.product_comparison.products), 2)

    def test_matched_response_requires_versioned_verified_candidates(self) -> None:
        response = ChatResponse.model_validate(
            {
                "session_id": "session-1",
                "run_id": "run-1",
                "answer": "候选如下",
                "recommendation_status": "MATCHED",
                "evaluation_completeness": "COMPLETE",
                "catalog_version": "catalog-v1",
                "rule_version": 1,
                "recommendations": [
                    {
                        "productId": "p-1",
                        "name": "医疗险",
                        "categoryId": "42",
                        "productVersion": 2,
                        "verificationStatus": "VERIFIED_MATCH",
                        "facts": [
                            {
                                "code": "waiting_days",
                                "definitionVersion": 1,
                                "name": "等待期",
                                "valueType": "NUMBER",
                                "unit": "DAY",
                                "valueNumber": 0,
                                "valueStatus": "KNOWN",
                            }
                        ],
                    }
                ],
            }
        )

        self.assertEqual(response.recommendations[0].product_id, "p-1")

    def test_rejects_matched_status_without_candidates(self) -> None:
        with self.assertRaises(ValidationError):
            ChatResponse.model_validate(
                {
                    "session_id": "session-1",
                    "run_id": "run-1",
                    "answer": "已找到候选",
                    "recommendation_status": "MATCHED",
                    "evaluation_completeness": "COMPLETE",
                    "catalog_version": "catalog-v1",
                    "rule_version": 1,
                    "recommendations": [],
                }
            )

    def test_rejects_partial_result_without_unresolved_criteria(self) -> None:
        with self.assertRaises(ValidationError):
            ChatResponse.model_validate(
                {
                    "session_id": "session-1",
                    "run_id": "run-1",
                    "answer": "部分结果",
                    "recommendation_status": "CATALOG_INCOMPLETE",
                    "evaluation_completeness": "PARTIAL",
                    "catalog_version": "catalog-v1",
                    "rule_version": 1,
                    "recommendations": [],
                    "unresolved_criteria": [],
                }
            )

    def test_rejects_non_finite_comparison_numbers(self) -> None:
        with self.assertRaises(ValidationError):
            ChatResponse.model_validate(
                {
                    "session_id": "session-1",
                    "run_id": "run-1",
                    "answer": "比较结果",
                    "recommendation_status": "MATCHED",
                    "evaluation_completeness": "COMPLETE",
                    "catalog_version": "catalog-v1",
                    "rule_version": 1,
                    "recommendations": [
                        {
                            "productId": "p-1",
                            "name": "医疗险",
                            "categoryId": "42",
                            "productVersion": 1,
                            "verificationStatus": "VERIFIED_MATCH",
                            "facts": [
                                {
                                    "code": "limit",
                                    "definitionVersion": 1,
                                    "name": "保额",
                                    "valueType": "NUMBER",
                                    "valueNumber": "Infinity",
                                    "valueStatus": "KNOWN",
                                }
                            ],
                        }
                    ],
                }
            )


if __name__ == "__main__":
    unittest.main()
