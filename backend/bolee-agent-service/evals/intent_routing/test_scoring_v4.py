"""Regression tests for the v4 intent-evaluation expectations and normalization."""

from __future__ import annotations

import json
import unittest
from types import SimpleNamespace

from runner import CASES, _select_cases, _validate_observation


class IntentRoutingScoringV4Tests(unittest.TestCase):
    def setUp(self) -> None:
        self.cases = {case["case_id"]: case for case in CASES}

    def test_case_subset_preserves_request_order(self) -> None:
        selected = _select_cases(CASES, "compare-07,policy-02")

        self.assertEqual([case["case_id"] for case in selected], ["compare-07", "policy-02"])

    def test_case_subset_rejects_unknown_case_ids(self) -> None:
        with self.assertRaises(ValueError):
            _select_cases(CASES, "case-does-not-exist")

    @staticmethod
    def _call(name: str, arguments: dict[str, object]) -> dict[str, object]:
        return {
            "type": "function",
            "function": {
                "name": name,
                "arguments": json.dumps(arguments, ensure_ascii=False),
            },
        }

    def test_product_clarification_is_not_scored_as_a_routing_failure(self) -> None:
        observation = _validate_observation(
            self.cases["terms-07"],
            SimpleNamespace(content="请补充具体产品名称或版本。"),
            [],
        )

        self.assertTrue(observation["pass"])
        self.assertTrue(observation["manual_review"])

    def test_empty_optional_category_is_scored_as_omitted(self) -> None:
        observation = _validate_observation(
            self.cases["catalog-01"],
            SimpleNamespace(content=""),
            [self._call("search_catalog_products", {"keyword": "境内旅游险", "category_id": ""})],
        )

        self.assertTrue(observation["pass"])
        self.assertTrue(observation["schema_valid"])
        self.assertNotIn("category_id", observation["normalized_call"]["arguments"])

    def test_null_string_category_is_scored_as_omitted(self) -> None:
        observation = _validate_observation(
            self.cases["compare-02"],
            SimpleNamespace(content=""),
            [
                self._call(
                    "search_comparison_products",
                    {"keyword": "境内旅游险", "category_id": "\nnull"},
                )
            ],
        )

        self.assertTrue(observation["pass"])
        self.assertTrue(observation["schema_valid"])

    def test_browsing_categories_accepts_catalog_category_tool(self) -> None:
        observation = _validate_observation(
            self.cases["recommend-05"],
            SimpleNamespace(content=""),
            [self._call("list_catalog_categories", {})],
        )

        self.assertTrue(observation["pass"])

    def test_comparison_must_not_route_to_recommendation_categories(self) -> None:
        observation = _validate_observation(
            self.cases["compare-07"],
            SimpleNamespace(content=""),
            [self._call("list_recommendation_categories", {})],
        )

        self.assertFalse(observation["pass"])
        self.assertIn("wrong_intent", observation["failure_reasons"])

    def test_provider_json_null_sentinel_is_ignored_for_own_policy_list(self) -> None:
        observation = _validate_observation(
            self.cases["policy-02"],
            SimpleNamespace(content=""),
            [self._call("get_my_policies", {"raw_arguments": None})],
        )

        self.assertTrue(observation["pass"])
        self.assertTrue(observation["schema_valid"])

    def test_mixed_goal_must_be_clarified_before_any_business_tool(self) -> None:
        observation = _validate_observation(
            self.cases["ambiguous-03"],
            SimpleNamespace(content=""),
            [self._call("list_recommendation_categories", {})],
        )

        self.assertFalse(observation["pass"])
        self.assertIn("unexpected_business_tool_call", observation["failure_reasons"])


if __name__ == "__main__":
    unittest.main()
