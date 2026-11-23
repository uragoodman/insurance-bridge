"""Fail-closed OAuth protection for custom and AgentOS-generated routes."""

import logging
from dataclasses import dataclass
from re import fullmatch, sub
from typing import Any

from app.clients.oauth2_jwt import (
    OAuth2JWTRejected,
    OAuth2JWTUnavailable,
    OAuth2JWTValidator,
)

logger = logging.getLogger("bolee-agent.authorization")


@dataclass(frozen=True, slots=True)
class RouteAccess:
    required_scope: str | None = None
    required_roles: frozenset[str] = frozenset()
    public: bool = False

    def __post_init__(self) -> None:
        if self.public and (self.required_scope is not None or self.required_roles):
            raise ValueError("Public routes cannot also require an authorization grant")
        if self.required_scope is not None and self.required_roles:
            raise ValueError("A route must use either OAuth scope or an AgentOS role")


class AgentOSRouteScopePolicy:
    """Map supported HTTP routes to machine scopes or internal operator roles."""

    AGENT_OBSERVER = "AGENT_OBSERVER"
    CONVERSATION_AUDITOR = "CONVERSATION_AUDITOR"
    AGENT_ADMIN = "AGENT_ADMIN"

    _AGENTOS_STATUS_PATHS = frozenset(
        {"/", "/health", "/info", "/openapi.json", "/docs", "/docs/oauth2-redirect", "/redoc"}
    )
    _AGENTOS_DEFINITION_PATTERNS = (
        r"/agents",
        r"/agents/[^/]+",
        r"/teams",
        r"/teams/[^/]+",
        r"/workflows",
        r"/workflows/[^/]+",
    )
    _AGENTOS_EXECUTION_READ_PATTERNS = (
        r"/agents/[^/]+/runs(?:/[^/]+(?:/checkpoints(?:/[^/]+)?)?)?",
        r"/teams/[^/]+/runs(?:/[^/]+(?:/checkpoints(?:/[^/]+)?)?)?",
        r"/workflows/[^/]+/runs(?:/[^/]+)?",
    )
    _AGENTOS_AUDIT_READ_PATTERNS = (
        r"/sessions",
        r"/sessions/filter-schema",
        r"/sessions/search",
        r"/sessions/[^/]+(?:/runs(?:/[^/]+)?|/run-previews|/media/[^/]+)?",
        r"/sessions/[^/]+/runs/[^/]+",
        r"/traces",
        r"/traces/filter-schema",
        r"/traces/search",
        r"/traces/[^/]+",
        r"/trace_session_stats",
        r"/os/metrics/users",
    )
    _AGENTOS_METRIC_READ_PATTERNS = (
        r"/metrics",
        r"/metrics/refresh/status",
        r"/os/metrics/(?:sessions|tokens|models|runs|latency)",
        r"/os/metrics/refresh/status",
    )
    # This allowlist mirrors AgentOS 3.2.0's OpenAPI paths. New generated routes
    # stay closed until their access class is reviewed and added here.
    _AGENTOS_KNOWN_ROUTE_PATTERNS = (
        r"/",
        r"/health",
        r"/info",
        r"/config",
        r"/openapi.json",
        r"/docs",
        r"/docs/oauth2-redirect",
        r"/redoc",
        *_AGENTOS_DEFINITION_PATTERNS,
        *_AGENTOS_EXECUTION_READ_PATTERNS,
        r"/agents/[^/]+/runs/[^/]+/(?:cancel|continue|resume)",
        r"/agents/[^/]+/sessions/[^/]+/(?:fork|compact)",
        r"/teams/[^/]+/runs/[^/]+/(?:cancel|continue|resume)",
        r"/teams/[^/]+/sessions/[^/]+/fork",
        r"/workflows/[^/]+/runs/[^/]+/(?:continue|cancel|resume)",
        r"/queue",
        *_AGENTOS_AUDIT_READ_PATTERNS,
        r"/sessions/[^/]+/(?:rename)",
        r"/sessions/[^/]+/runs/[^/]+/rename",
        r"/memories",
        r"/memories/[^/]+",
        r"/memory_topics",
        r"/user_memory_stats",
        r"/optimize-memories",
        r"/learnings",
        r"/learnings/users",
        r"/learnings/users/[^/]+",
        r"/learnings/[^/]+",
        r"/eval-runs",
        r"/eval-runs/[^/]+",
        *_AGENTOS_METRIC_READ_PATTERNS,
        r"/metrics/refresh",
        r"/os/metrics/refresh",
        r"/knowledge/content",
        r"/knowledge/remote-content",
        r"/knowledge/content/[^/]+",
        r"/knowledge/content/[^/]+/(?:refresh|status)",
        r"/knowledge/search",
        r"/knowledge/config",
        r"/knowledge/[^/]+/sources",
        r"/knowledge/[^/]+/sources/[^/]+/files",
        r"/databases/all/migrate",
        r"/databases/[^/]+/migrate",
        r"/filesystem/(?:files|entries|content|search)",
        r"/components",
        r"/components/[^/]+",
        r"/components/[^/]+/(?:restore|configs|configs/current)",
        r"/components/[^/]+/configs/[^/]+",
        r"/components/[^/]+/configs/[^/]+/set-current",
        r"/schedules",
        r"/schedules/[^/]+",
        r"/schedules/[^/]+/(?:enable|disable|trigger|runs)",
        r"/schedules/[^/]+/runs/[^/]+",
        r"/approvals",
        r"/approvals/count",
        r"/approvals/[^/]+",
        r"/approvals/[^/]+/status",
        r"/approvals/[^/]+/resolve",
        r"/service-accounts",
        r"/service-accounts/[^/]+",
        r"/registry",
    )

    def __init__(
        self,
        *,
        chat_scope: str,
        operator_scope: str,
        tag_generation_scope: str,
    ) -> None:
        self._chat_scope = chat_scope
        self._operator_scope = operator_scope
        self._tag_generation_scope = tag_generation_scope

    def resolve(self, method: str, path: str) -> RouteAccess:
        normalized_method = method.upper()
        normalized_path = path.rstrip("/") or "/"

        if normalized_path == "/health" and normalized_method in {"GET", "HEAD"}:
            return RouteAccess(public=True)

        if (
            normalized_path in {"/internal/chat", "/internal/chat/stream"}
            and normalized_method == "POST"
        ):
            return RouteAccess(required_scope=self._chat_scope)
        if normalized_path == "/internal/sessions/previews" and normalized_method == "POST":
            return RouteAccess(required_scope=self._chat_scope)
        if normalized_method == "GET" and fullmatch(
            r"/internal/sessions/[A-Za-z0-9_-]{1,64}/messages", normalized_path
        ):
            return RouteAccess(required_scope=self._chat_scope)
        if (
            normalized_path == "/internal/product-tags/generate"
            and normalized_method == "POST"
        ):
            return RouteAccess(required_scope=self._tag_generation_scope)
        if normalized_method == "DELETE" and fullmatch(
            r"/internal/sessions/[A-Za-z0-9_-]{1,64}", normalized_path
        ):
            return RouteAccess(required_scope=self._operator_scope)
        if normalized_method == "POST" and normalized_path in {
            "/internal/retention/traces",
            "/internal/retention/sessions",
        }:
            return RouteAccess(required_scope=self._operator_scope)

        if self._matches(self._AGENTOS_KNOWN_ROUTE_PATTERNS, normalized_path):
            if normalized_path in self._AGENTOS_STATUS_PATHS and normalized_method in {
                "GET",
                "HEAD",
            }:
                return RouteAccess(
                    required_roles=frozenset({self.AGENT_OBSERVER, self.AGENT_ADMIN})
                )
            if normalized_path == "/config":
                return RouteAccess(required_roles=frozenset({self.AGENT_ADMIN}))
            if normalized_method in {"GET", "HEAD"} and self._matches(
                self._AGENTOS_DEFINITION_PATTERNS, normalized_path
            ):
                return RouteAccess(
                    required_roles=frozenset({self.AGENT_OBSERVER, self.AGENT_ADMIN})
                )
            if normalized_method in {"GET", "HEAD"} and self._matches(
                self._AGENTOS_EXECUTION_READ_PATTERNS, normalized_path
            ):
                return RouteAccess(
                    required_roles=frozenset({self.CONVERSATION_AUDITOR, self.AGENT_ADMIN})
                )
            is_audit_read = (
                normalized_method in {"GET", "HEAD"}
                and self._matches(self._AGENTOS_AUDIT_READ_PATTERNS, normalized_path)
            ) or (
                normalized_method == "POST"
                and normalized_path in {"/sessions/search", "/traces/search"}
            )
            if is_audit_read:
                return RouteAccess(
                    required_roles=frozenset({self.CONVERSATION_AUDITOR, self.AGENT_ADMIN})
                )
            if normalized_method in {"GET", "HEAD"} and self._matches(
                self._AGENTOS_METRIC_READ_PATTERNS, normalized_path
            ):
                return RouteAccess(
                    required_roles=frozenset({self.AGENT_OBSERVER, self.AGENT_ADMIN})
                )
            return RouteAccess(required_roles=frozenset({self.AGENT_ADMIN}))

        # Unknown route families remain unavailable. Known AgentOS families require
        # an explicit operator role; mutations default to the administrator role.
        return RouteAccess()

    @staticmethod
    def _matches(patterns: tuple[str, ...], path: str) -> bool:
        return any(fullmatch(pattern, path) for pattern in patterns)

    def validate_openapi(self, schema: dict[str, Any]) -> None:
        """Fail startup if a generated HTTP operation has no explicit policy."""
        uncovered: list[str] = []
        for path, operations in schema.get("paths", {}).items():
            concrete_path = sub(r"\{[^/]+\}", "route-param", path)
            for method in operations:
                if method.lower() not in {
                    "get", "head", "post", "put", "patch", "delete", "options", "trace"
                }:
                    continue
                access = self.resolve(method, concrete_path)
                if (
                    not access.public
                    and access.required_scope is None
                    and not access.required_roles
                ):
                    uncovered.append(f"{method.upper()} {path}")
        if uncovered:
            raise RuntimeError(
                "AgentOS route authorization policy is incomplete: " + ", ".join(sorted(uncovered))
            )


class AgentOSOAuthScopeMiddleware:
    """ASGI middleware that protects HTTP and closes unsupported WebSocket routes."""

    def __init__(
        self,
        app: Any,
        *,
        token_validator: OAuth2JWTValidator,
        route_policy: AgentOSRouteScopePolicy,
        admin_issuer: str,
        admin_audience: str,
    ) -> None:
        self.app = app
        self._token_validator = token_validator
        self._route_policy = route_policy
        self._admin_issuer = admin_issuer
        self._admin_audience = admin_audience

    async def __call__(self, scope: dict[str, Any], receive: Any, send: Any) -> None:
        scope_type = scope.get("type")
        if scope_type == "websocket":
            # Customer chat uses HTTP SSE. No WebSocket interface is configured;
            # keep future AgentOS WebSocket surfaces closed until explicitly secured.
            await send({"type": "websocket.close", "code": 1008, "reason": "WebSocket disabled"})
            return
        if scope_type != "http":
            await self.app(scope, receive, send)
            return

        method = str(scope.get("method", "GET"))
        path = str(scope.get("path", "/"))
        access = self._route_policy.resolve(method, path)
        if access.public:
            await self.app(scope, receive, send)
            return
        if access.required_scope is None and not access.required_roles:
            await self._respond(send, 404, {"detail": "Not found"})
            return

        token = self._bearer_token(scope)
        if token is None:
            await self._respond(send, 401, {"detail": "bearer access token required"})
            return
        try:
            if access.required_scope is not None:
                await self._token_validator.require_async(token, access.required_scope)
            else:
                claims = await self._token_validator.decode_claims_async(
                    token,
                    issuer=self._admin_issuer,
                    audience=self._admin_audience,
                )
                claim_roles = claims.get("roles", [])
                roles = (
                    set(claim_roles)
                    if isinstance(claim_roles, list)
                    and all(isinstance(role, str) for role in claim_roles)
                    else set()
                )
                if access.required_roles.isdisjoint(roles):
                    logger.warning(
                        "AgentOS authorization denied subject=%s method=%s route=%s resource=%s",
                        claims.get("sub", "unknown"),
                        method,
                        self._route_group(path),
                        self._audit_resource(path),
                    )
                    await self._respond(send, 403, {"detail": "AgentOS role denied"})
                    return
                logger.info(
                    "AgentOS authorization allowed subject=%s roles=%s method=%s route=%s "
                    "resource=%s",
                    claims.get("sub", "unknown"),
                    sorted(roles.intersection(access.required_roles)),
                    method,
                    self._route_group(path),
                    self._audit_resource(path),
                )
        except OAuth2JWTUnavailable:
            await self._respond(send, 503, {"detail": "OAuth verification is unavailable"})
            return
        except OAuth2JWTRejected as exc:
            await self._respond(send, exc.status_code, {"detail": exc.detail})
            return

        await self.app(scope, receive, send)

    @staticmethod
    def _bearer_token(scope: dict[str, Any]) -> str | None:
        for name, value in scope.get("headers", []):
            if name.lower() == b"authorization":
                authorization = value.decode("latin-1").strip()
                if authorization[:7].lower() == "bearer ":
                    token = authorization[7:].strip()
                    return token or None
                return None
        return None

    @staticmethod
    def _route_group(path: str) -> str:
        segments = [segment for segment in path.split("/") if segment]
        return segments[0] if segments else "root"

    @staticmethod
    def _audit_resource(path: str) -> str:
        # Record session/trace identifiers for audit correlation without logging
        # query strings, request bodies, or arbitrary control characters.
        return sub(r"[^A-Za-z0-9._/{}/-]", "_", path)[:256]

    @staticmethod
    async def _respond(send: Any, status_code: int, body: dict[str, str]) -> None:
        import json

        payload = json.dumps(body, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        await send(
            {
                "type": "http.response.start",
                "status": status_code,
                "headers": [
                    (b"content-type", b"application/json; charset=utf-8"),
                    (b"cache-control", b"no-store"),
                    (b"content-length", str(len(payload)).encode("ascii")),
                ],
            }
        )
        await send({"type": "http.response.body", "body": payload})


__all__ = ["AgentOSOAuthScopeMiddleware", "AgentOSRouteScopePolicy", "RouteAccess"]
