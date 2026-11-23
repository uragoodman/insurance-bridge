"""Tests for serialized dispatch through a single model-facing task tool."""

import unittest
from types import SimpleNamespace

from agno.tools.function import Function

from app.contexts.customer_task import CustomerTaskRuntime
from app.contexts.tool import AgentToolResultCollector
from app.domain.customer_task import ActiveTaskState
from app.schemas.customer_task import CustomerTaskRequest
from app.services.customer_task_dispatcher import CustomerTaskDispatcher
from app.tools.customer_task import CustomerTaskTools
from app.tools.registry import build_agent_tools


class FakeAdapter:
    def __init__(self, results=None) -> None:
        self.results = results or {}
        self.calls: list[tuple[str, dict[str, object]]] = []

    def __getattr__(self, name):
        def call(_run_context, **kwargs):
            self.calls.append((name, kwargs))
            return self.results.get(name, {"status": "OK"})

        return call


class CustomerTaskDispatcherTests(unittest.TestCase):
    def setUp(self) -> None:
        self.catalog = FakeAdapter()
        self.recommendation = FakeAdapter(
            {
                "get_product_recommendation_criteria": {"status": "READY"},
                "recommend_products": {"status": "MATCHED", "candidates": []},
            }
        )
        self.comparison = FakeAdapter()
        self.terms = FakeAdapter({"search_product_terms": {"status": "ANSWERED"}})
        self.own_policy = FakeAdapter()
        self.dispatcher = CustomerTaskDispatcher(
            catalog=self.catalog,
            recommendation=self.recommendation,
            comparison=self.comparison,
            terms=self.terms,
            own_policy=self.own_policy,
        )
        self.runtime = CustomerTaskRuntime()
        self.runtime.begin_turn(ActiveTaskState())
        self.collector = AgentToolResultCollector()
        self.run_context = SimpleNamespace(
            dependencies={
                "java_internal_api": SimpleNamespace(get_json=lambda *args, **kwargs: {}),
                "authorization_context": "a" * 43,
                "tool_result_collector": self.collector,
                "selected_policy_id": None,
                "customer_task_runtime": self.runtime,
            }
        )

    def test_recommendation_is_blocked_until_java_criteria_are_read(self) -> None:
        request = CustomerTaskRequest.model_validate(
            {
                "intent": "PRODUCT_RECOMMENDATION",
                "action": "RECOMMEND_PRODUCTS",
                "criteria": {"category_id": "42", "items": []},
            }
        )

        result = self.dispatcher.dispatch(request, self.run_context)

        self.assertEqual(result["status"], "BLOCKED_BY_WORKFLOW")
        self.assertEqual(self.recommendation.calls, [])

    def test_recommendation_steps_run_in_order_and_complete_one_task(self) -> None:
        criteria_request = CustomerTaskRequest.model_validate(
            {
                "intent": "PRODUCT_RECOMMENDATION",
                "action": "GET_RECOMMENDATION_CRITERIA",
                "category_id": "42",
            }
        )
        recommendation_request = CustomerTaskRequest.model_validate(
            {
                "intent": "PRODUCT_RECOMMENDATION",
                "action": "RECOMMEND_PRODUCTS",
                "criteria": {"category_id": "42", "items": []},
            }
        )

        first = self.dispatcher.dispatch(criteria_request, self.run_context)
        second = self.dispatcher.dispatch(recommendation_request, self.run_context)

        self.assertEqual(first["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(first["task_status"], "IN_PROGRESS")
        self.assertEqual(second["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(second["task_status"], "COMPLETED")
        self.assertEqual(len(self.recommendation.calls), 2)

    def test_a_second_independent_intent_is_rejected_in_the_same_run(self) -> None:
        terms_request = CustomerTaskRequest.model_validate(
            {
                "intent": "TERMS_QA",
                "action": "SEARCH_PRODUCT_TERMS",
                "product_name": "境内旅游险",
                "question": "等待期多久",
            }
        )
        catalog_request = CustomerTaskRequest.model_validate(
            {
                "intent": "PRODUCT_CATALOG",
                "action": "SEARCH_PRODUCTS",
                "keyword": "旅游险",
            }
        )

        self.dispatcher.dispatch(terms_request, self.run_context)
        result = self.dispatcher.dispatch(catalog_request, self.run_context)

        self.assertEqual(result["status"], "TASK_CONFLICT")
        self.assertEqual(self.catalog.calls, [])

    def test_agent_registry_exposes_narrow_action_tool_schemas(self) -> None:
        toolkits = build_agent_tools()
        self.assertEqual(len(toolkits), 1)
        functions = toolkits[0].functions
        self.assertEqual(
            set(functions),
            {
                "list_catalog_categories",
                "list_recommendation_categories",
                "search_catalog_products",
                "search_comparison_products",
                "get_recommendation_criteria",
                "recommend_products",
                "compare_products",
                "search_product_terms",
                "get_my_policies",
                "get_my_policy",
                "get_selected_policy",
            },
        )

        catalog_search = Function.from_callable(
            functions["search_catalog_products"].entrypoint
        ).parameters["properties"]
        self.assertEqual(set(catalog_search), {"keyword", "category_id"})
        self.assertNotIn("minLength", catalog_search["category_id"])

        terms_search = Function.from_callable(
            functions["search_product_terms"].entrypoint
        ).parameters["properties"]
        self.assertEqual(set(terms_search), {"product_name", "question"})
        self.assertNotIn("limit", catalog_search)
        self.assertNotIn("intent", catalog_search)
        self.assertNotIn("action", catalog_search)

    def test_catalog_search_wrapper_binds_catalog_intent_and_server_limit(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.search_catalog_products(self.run_context, "境内旅游险")

        self.assertEqual(result["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(result["intent"], "PRODUCT_CATALOG")
        self.assertEqual(result["action"], "SEARCH_PRODUCTS")
        self.assertEqual(
            self.catalog.calls,
            [("search_products_by_category", {"keyword": "境内旅游险", "limit": 3})],
        )

    def test_comparison_search_wrapper_binds_comparison_intent(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.search_comparison_products(self.run_context, "安心旅行")

        self.assertEqual(result["intent"], "PRODUCT_COMPARISON")
        self.assertEqual(result["action"], "SEARCH_PRODUCTS")
        self.assertEqual(self.catalog.calls[0][1]["limit"], 3)

    def test_comparison_search_ignores_empty_optional_category_id(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.search_comparison_products(
            self.run_context,
            "出境旅游保险",
            category_id="",
        )

        self.assertEqual(result["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(
            self.catalog.calls,
            [
                (
                    "search_products_by_category",
                    {"keyword": "出境旅游保险", "limit": 3},
                )
            ],
        )

    def test_comparison_search_ignores_null_string_optional_category_id(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.search_comparison_products(
            self.run_context,
            "境内旅游险",
            category_id="\nnull",
        )

        self.assertEqual(result["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(
            self.catalog.calls,
            [("search_products_by_category", {"keyword": "境内旅游险", "limit": 3})],
        )

    def test_catalog_search_ignores_whitespace_optional_category_id(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.search_catalog_products(
            self.run_context,
            "境内旅游险",
            category_id="   ",
        )

        self.assertEqual(result["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(
            self.catalog.calls,
            [
                (
                    "search_products_by_category",
                    {"keyword": "境内旅游险", "limit": 3},
                )
            ],
        )

    def test_own_policy_list_ignores_provider_null_argument_sentinel(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.get_my_policies(self.run_context, raw_arguments="null")

        self.assertEqual(result["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(self.own_policy.calls, [("get_my_policies", {})])

    def test_own_policy_list_ignores_json_null_argument_sentinel(self) -> None:
        toolkit = CustomerTaskTools(dispatcher=self.dispatcher)

        result = toolkit.get_my_policies(self.run_context, raw_arguments=None)

        self.assertEqual(result["status"], "TASK_ACTION_COMPLETED")
        self.assertEqual(self.own_policy.calls, [("get_my_policies", {})])


if __name__ == "__main__":
    unittest.main()
