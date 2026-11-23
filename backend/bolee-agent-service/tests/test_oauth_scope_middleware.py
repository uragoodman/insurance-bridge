"""Focused authorization tests for AgentOS role and OAuth scope boundaries."""

from __future__ import annotations

import asyncio
import unittest
from typing import Any

from app.clients.oauth2_jwt import OAuth2JWTRejected
from app.security.oauth_scope_middleware import (
    AgentOSOAuthScopeMiddleware,
    AgentOSRouteScopePolicy,
)


class FakeTokenValidator:
    def __init__(self) -> None:
        self.claims = {
            "admin-token": {"sub": "admin-1", "roles": ["AGENT_ADMIN"]},
            "observer-token": {"sub": "operator-1", "roles": ["AGENT_OBSERVER"]},
            "auditor-token": {"sub": "auditor-1", "roles": ["CONVERSATION_AUDITOR"]},
        }

    async def decode_claims_async(
        self, token: str, *, issuer: str | None = None, audience: str | None = None
    ) -> dict[str, Any]:
        if issuer != "test-issuer" or audience != "test-audience":
            raise OAuth2JWTRejected("invalid management token configuration")
        if token not in self.claims:
            raise OAuth2JWTRejected("invalid or expired bearer access token")
        return self.claims[token]

    async def require_async(self, token: str, required_scope: str) -> None:
        if token == "chat-client-token" and required_scope == "agent.chat":
            return
        raise OAuth2JWTRejected("OAuth client is not permitted", status_code=403)


class AgentOSRouteScopePolicyTests(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = AgentOSRouteScopePolicy(
            chat_scope="agent.chat",
            operator_scope="agent.operator",
            tag_generation_scope="product.tags.generate",
        )

    def test_agent_definitions_are_limited_to_observers_and_admins(self) -> None:
        access = self.policy.resolve("GET", "/agents")

        self.assertEqual(
            frozenset({"AGENT_OBSERVER", "AGENT_ADMIN"}), access.required_roles
        )

    def test_session_audit_is_limited_to_auditors_and_admins(self) -> None:
        access = self.policy.resolve("GET", "/sessions")

        self.assertEqual(
            frozenset({"CONVERSATION_AUDITOR", "AGENT_ADMIN"}), access.required_roles
        )

    def test_admin_mutations_and_customer_chat_use_separate_authorization(self) -> None:
        mutation = self.policy.resolve("POST", "/agents/agent-1/runs/run-1/cancel")
        chat = self.policy.resolve("POST", "/internal/chat")

        self.assertEqual(frozenset({"AGENT_ADMIN"}), mutation.required_roles)
        self.assertEqual("agent.chat", chat.required_scope)

    def test_unknown_route_is_not_mapped_to_an_authorization_grant(self) -> None:
        access = self.policy.resolve("GET", "/unreviewed-route")

        self.assertIsNone(access.required_scope)
        self.assertFalse(access.required_roles)
        self.assertFalse(access.public)

    def test_openapi_validation_accepts_explicitly_classified_paths(self) -> None:
        self.policy.validate_openapi(
            {"paths": {"/agents/{agent_id}": {"get": {}, "post": {}}}}
        )


class AgentOSOAuthScopeMiddlewareTests(unittest.TestCase):
    def setUp(self) -> None:
        self.downstream_calls = 0

        async def downstream(scope: dict[str, Any], receive: Any, send: Any) -> None:
            self.downstream_calls += 1
            await send({"type": "http.response.start", "status": 200, "headers": []})
            await send({"type": "http.response.body", "body": b"ok"})

        self.middleware = AgentOSOAuthScopeMiddleware(
            downstream,
            token_validator=FakeTokenValidator(),
            route_policy=AgentOSRouteScopePolicy(
                chat_scope="agent.chat",
                operator_scope="agent.operator",
                tag_generation_scope="product.tags.generate",
            ),
            admin_issuer="test-issuer",
            admin_audience="test-audience",
        )

    def run_request(
        self, method: str, path: str, token: str | None = None
    ) -> tuple[int, bytes]:
        messages: list[dict[str, Any]] = []
        headers = [] if token is None else [(b"authorization", f"Bearer {token}".encode())]

        async def receive() -> dict[str, Any]:
            return {"type": "http.request", "body": b"", "more_body": False}

        async def send(message: dict[str, Any]) -> None:
            messages.append(message)

        scope = {"type": "http", "method": method, "path": path, "headers": headers}
        asyncio.run(self.middleware(scope, receive, send))
        response_start = next(
            message for message in messages if message["type"] == "http.response.start"
        )
        response_body = next(
            message for message in messages if message["type"] == "http.response.body"
        )
        return response_start["status"], response_body.get("body", b"")

    def test_admin_can_read_agent_definitions(self) -> None:
        status, body = self.run_request("GET", "/agents", "admin-token")

        self.assertEqual(200, status)
        self.assertEqual(b"ok", body)
        self.assertEqual(1, self.downstream_calls)

    def test_observer_cannot_read_customer_sessions(self) -> None:
        status, body = self.run_request("GET", "/sessions", "observer-token")

        self.assertEqual(403, status)
        self.assertIn(b"AgentOS role denied", body)
        self.assertEqual(0, self.downstream_calls)

    def test_customer_chat_scope_does_not_accept_management_role_token(self) -> None:
        status, body = self.run_request("POST", "/internal/chat", "admin-token")

        self.assertEqual(403, status)
        self.assertIn(b"OAuth client is not permitted", body)
        self.assertEqual(0, self.downstream_calls)

    def test_missing_bearer_is_rejected(self) -> None:
        status, body = self.run_request("GET", "/agents")

        self.assertEqual(401, status)
        self.assertIn(b"bearer access token required", body)
        self.assertEqual(0, self.downstream_calls)

    def test_unknown_agentos_route_fails_closed(self) -> None:
        status, body = self.run_request("GET", "/unreviewed-route", "admin-token")

        self.assertEqual(404, status)
        self.assertIn(b"Not found", body)
        self.assertEqual(0, self.downstream_calls)


if __name__ == "__main__":
    unittest.main()
