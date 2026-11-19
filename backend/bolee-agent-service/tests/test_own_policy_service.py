"""Tests for authenticated own-policy Java tool contracts."""

import unittest
from typing import Any

from app.contexts.tool import AgentToolContext, AgentToolResultCollector
from app.schemas.own_policy import OwnPolicyDetailStatus, OwnPolicyStatus
from app.services.own_policy import OwnPolicyService, OwnPolicyServiceError


class FakeJavaClient:
    def __init__(self) -> None:
        self.responses: dict[str, Any] = {}
        self.calls: list[tuple[str, str]] = []

    def get_json(
        self,
        path: str,
        authorization_context: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        self.calls.append((path, authorization_context))
        return self.responses[path]


class OwnPolicyServiceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.client = FakeJavaClient()
        self.collector = AgentToolResultCollector()
        self.context = AgentToolContext(self.client, "g" * 43, self.collector)
        self.service = OwnPolicyService()

    def test_summary_exposes_policy_reference_and_validates_java_response(self) -> None:
        path = "/internal/agent-tools/v1/policies/summary"
        self.client.responses[path] = {
            "code": 200,
            "data": {
                "status": "FOUND",
                "asOf": "2026-11-17T10:00:00Z",
                "policies": [
                    {
                        "policyId": "9001",
                        "productName": "个人意外险",
                        "policyStatus": "IN_FORCE",
                        "policyStatusLabel": "保障中",
                        "coverageStartTime": "2026-01-01T00:00:00",
                        "coverageEndTime": "2026-12-31T23:59:59",
                        "autoExtensionEnabled": False,
                    }
                ],
                "returnedCount": 1,
                "truncated": False,
            },
        }

        result = self.service.get_my_policies(self.context)

        self.assertEqual(result.policies[0].policy_id, "9001")
        self.assertEqual(result.policies[0].policy_status, OwnPolicyStatus.IN_FORCE)
        self.assertEqual(self.client.calls, [(path, "g" * 43)])
        self.assertEqual(self.collector.own_policy_summary, result)

    def test_detail_uses_reference_and_records_not_found_without_owner_data(self) -> None:
        self.client.responses["/internal/agent-tools/v1/policies/9001"] = {
            "code": 200,
            "data": {"status": "NOT_FOUND", "asOf": "2026-11-17T10:00:00Z", "policy": None},
        }

        result = self.service.get_my_policy("9001", self.context)

        self.assertEqual(result.status, OwnPolicyDetailStatus.NOT_FOUND)
        self.assertIsNone(result.policy)
        self.assertEqual(
            self.client.calls,
            [("/internal/agent-tools/v1/policies/9001", "g" * 43)],
        )
        self.assertEqual(self.collector.own_policy_detail, result)

    def test_detail_rejects_arbitrary_policy_number_before_calling_java(self) -> None:
        with self.assertRaises(OwnPolicyServiceError):
            self.service.get_my_policy("POLICY-123", self.context)
        self.assertEqual(self.client.calls, [])

    def test_detail_requires_policy_reference_to_match_requested_id(self) -> None:
        self.client.responses["/internal/agent-tools/v1/policies/9001"] = {
            "code": 200,
            "data": {
                "status": "FOUND",
                "asOf": "2026-11-17T10:00:00Z",
                "policy": {
                    "policyId": "9002",
                    "policyStatus": "IN_FORCE",
                    "policyStatusLabel": "保障中",
                },
            },
        }
        with self.assertRaises(OwnPolicyServiceError):
            self.service.get_my_policy("9001", self.context)

