"""Tests for Agno tool registration and the Java-facing chat response boundary."""

import importlib.util
import unittest
from types import SimpleNamespace
from unittest.mock import patch


@unittest.skipUnless(importlib.util.find_spec("agno"), "Agno is installed in the Docker runtime")
class ChatAgentBoundaryTests(unittest.TestCase):
    def test_agent_registers_only_the_supported_business_toolkits(self) -> None:
        from app.agent import insurance_agent

        self.assertFalse(insurance_agent.add_history_to_context)
        self.assertTrue(insurance_agent.add_session_state_to_context)
        self.assertEqual(
            {toolkit.name for toolkit in insurance_agent.tools},
            {
                "insurance_product_catalog",
                "insurance_product_recommendation",
                "insurance_product_comparison",
                "insurance_own_policy",
            },
        )
        catalog_toolkit = next(
            toolkit
            for toolkit in insurance_agent.tools
            if toolkit.name == "insurance_product_catalog"
        )
        self.assertEqual(
            {tool.__name__ for tool in catalog_toolkit.tools},
            {"list_product_categories", "search_products_by_category"},
        )
        recommendation_toolkit = next(
            toolkit
            for toolkit in insurance_agent.tools
            if toolkit.name == "insurance_product_recommendation"
        )
        self.assertEqual(
            {tool.__name__ for tool in recommendation_toolkit.tools},
            {"get_product_recommendation_criteria", "recommend_products"},
        )
        comparison_toolkit = next(
            toolkit
            for toolkit in insurance_agent.tools
            if toolkit.name == "insurance_product_comparison"
        )
        self.assertEqual(
            {tool.__name__ for tool in comparison_toolkit.tools},
            {"compare_products"},
        )
        own_policy_toolkit = next(
            toolkit for toolkit in insurance_agent.tools if toolkit.name == "insurance_own_policy"
        )
        self.assertEqual(
            {tool.__name__ for tool in own_policy_toolkit.tools},
            {"get_my_policies", "get_my_policy"},
        )

    def test_chat_returns_only_the_collected_java_candidates(self) -> None:
        from fastapi.testclient import TestClient

        import app.main as main
        from app.schemas import (
            ProductCatalogSearchResponse,
            ProductRecommendationResponse,
        )

        run_args: dict[str, object] = {}

        def fake_run(_message: str, **kwargs: object) -> SimpleNamespace:
            run_args.update(kwargs)
            dependencies = kwargs["dependencies"]
            collector = dependencies["tool_result_collector"]
            collector.record_product_catalog_search(
                ProductCatalogSearchResponse.model_validate(
                    {
                        "status": "MATCHED",
                        "candidates": [
                            {
                                "productId": "product-1",
                                "name": "示例意外险",
                                "categoryId": "42",
                                "categoryCode": "001002003",
                                "categoryName": "意外险",
                                "matchReasons": ["来自 Java 目录"],
                            }
                        ],
                    }
                )
            )
            collector.record_product_recommendation(
                ProductRecommendationResponse.model_validate(
                    {
                        "requestId": "11111111-1111-4111-8111-111111111111",
                        "status": "MATCHED",
                        "evaluationCompleteness": "COMPLETE",
                        "missingCriteria": [],
                        "unsupportedCriteria": [],
                        "unresolvedCriteria": [],
                        "excludedProductCount": 1,
                        "candidates": [
                            {
                                "productId": "product-recommended",
                                "name": "经过审核的医疗险",
                                "categoryId": "42",
                                "productVersion": 2,
                                "verificationStatus": "VERIFIED_MATCH",
                                "matchReasons": ["等待期符合偏好"],
                                "facts": [
                                    {
                                        "code": "waiting_period_days",
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
                        "evaluatedAt": "2026-11-15T03:00:00Z",
                        "catalogVersion": "catalog-test-v1",
                        "ruleVersion": 1,
                        "engineVersion": "recommendation-engine-v1",
                    }
                )
            )
            return SimpleNamespace(content="Java 目录中有一项候选产品", run_id="run-1")

        test_settings = SimpleNamespace(
            model_api_key="model-key",
            oauth_chat_scope="agent.chat",
            context_tool_result_reserve_tokens=8192,
        )
        with patch.object(main, "settings", test_settings), patch.object(
            main, "chat_application", SimpleNamespace(run=fake_run)
        ), patch.object(main, "oauth_jwt_validator") as token_validator:
            token_validator.require.return_value = None
            response = TestClient(main.base_app).post(
                "/internal/chat",
                headers={"Authorization": "Bearer test-token"},
                json={
                    "user_id": "customer-1",
                    "session_id": "session-1",
                    "authorization_context": "a" * 43,
                    "message": "推荐意外险目录",
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(
            response.json()["recommendations"][0]["product_id"],
            "product-recommended",
        )
        self.assertEqual(response.json()["recommendation_status"], "MATCHED")
        self.assertEqual(
            response.json()["recommendations"][0]["facts"][0]["value_number"], "0"
        )
        self.assertEqual(response.json()["session_id"], "session-1")
        self.assertEqual(
            run_args["dependencies"]["authorization_context"],
            "a" * 43,
        )

    def test_chat_stream_emits_only_content_deltas_then_the_complete_contract(self) -> None:
        from fastapi.testclient import TestClient

        import app.main as main

        class FakeRunOutput:
            content = "流式完成答复"
            run_id = "run-stream-1"

        run_args: dict[str, object] = {}

        def fake_run(_message: str, **kwargs: object):
            run_args.update(kwargs)
            return iter(
                [
                    SimpleNamespace(event="RunContent", content="流式"),
                    SimpleNamespace(event="RunContent", content="完成答复"),
                    FakeRunOutput(),
                ]
            )

        test_settings = SimpleNamespace(
            model_api_key="model-key",
            oauth_chat_scope="agent.chat",
            context_tool_result_reserve_tokens=8192,
        )
        with patch.object(main, "settings", test_settings), patch.object(
            main,
            "chat_application",
            SimpleNamespace(
                stream=fake_run,
                is_final_output=lambda item: isinstance(item, FakeRunOutput),
            ),
        ), patch.object(
            main, "oauth_jwt_validator"
        ) as token_validator:
            token_validator.require.return_value = None
            response = TestClient(main.base_app).post(
                "/internal/chat/stream",
                headers={"Authorization": "Bearer test-token"},
                json={
                    "user_id": "customer-1",
                    "session_id": "session-1",
                    "authorization_context": "a" * 43,
                    "message": "你好",
                },
            )

        self.assertEqual(response.status_code, 200)
        self.assertIn('event: delta\ndata: {"text":"流式"}', response.text)
        self.assertIn('event: complete\ndata:', response.text)
        self.assertIn("流式完成答复", response.text)
        self.assertEqual(run_args["tool_result_collector"].sensitive_tool_used, False)
        token_validator.require.assert_called_once_with("test-token", "agent.chat")

    def test_session_delete_requires_operator_scope_and_deletes_persisted_data(self) -> None:
        from fastapi.testclient import TestClient

        import app.main as main

        with patch.object(main, "oauth_jwt_validator") as token_validator, patch.object(
            main, "delete_session_and_traces"
        ) as delete_data:
            token_validator.require.return_value = None
            response = TestClient(main.base_app).delete(
                "/internal/sessions/session-1",
                headers={"Authorization": "Bearer operator-token"},
            )

        self.assertEqual(response.status_code, 204)
        token_validator.require.assert_called_once_with("operator-token", "agent.operator")
        delete_data.assert_called_once_with("session-1")

    def test_trace_retention_route_requires_operator_scope_and_positive_days(self) -> None:
        from fastapi.testclient import TestClient

        import app.main as main

        with patch.object(main, "oauth_jwt_validator") as token_validator, patch.object(
            main, "delete_expired_traces", return_value=12
        ) as cleanup:
            token_validator.require.return_value = None
            response = TestClient(main.base_app).post(
                "/internal/retention/traces?retention_days=30",
                headers={"Authorization": "Bearer operator-token"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"deleted_rows": 12})
        token_validator.require.assert_called_once_with("operator-token", "agent.operator")
        cleanup.assert_called_once_with(30)

    def test_session_retention_route_removes_expired_sessions_under_operator_scope(self) -> None:
        from fastapi.testclient import TestClient

        import app.main as main

        with patch.object(main, "oauth_jwt_validator") as token_validator, patch.object(
            main, "delete_expired_sessions", return_value=3
        ) as cleanup:
            token_validator.require.return_value = None
            response = TestClient(main.base_app).post(
                "/internal/retention/sessions?retention_days=90",
                headers={"Authorization": "Bearer operator-token"},
            )

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json(), {"deleted_sessions": 3})
        token_validator.require.assert_called_once_with("operator-token", "agent.operator")
        cleanup.assert_called_once_with(90)


if __name__ == "__main__":
    unittest.main()
