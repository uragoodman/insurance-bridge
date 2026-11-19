"""HTTP adapter for allowlisted Java insurance internal APIs."""

import json
from decimal import Decimal
from typing import Any
from urllib.parse import urlsplit

import httpx

from app.clients.oauth2 import OAuth2ClientCredentials, OAuth2TokenError


class JavaApiError(RuntimeError):
    """Sanitized Java API failure that does not expose upstream response contents."""


class JavaInternalApiClient:
    _ALLOWED_PREFIX = "/internal/agent-tools/"
    _ALLOWED_POST_PATHS = frozenset(
        {
            "/internal/agent-tools/v1/products/recommendations",
            "/internal/agent-tools/v1/products/compare",
        }
    )

    def __init__(
        self,
        base_url: str,
        oauth_client: OAuth2ClientCredentials,
        timeout_seconds: float = 10.0,
    ):
        parsed = urlsplit(base_url)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.netloc
            or parsed.path not in {"", "/"}
        ):
            raise ValueError("AGNO_JAVA_SERVICE_URL must be an HTTP(S) origin without a path")
        if timeout_seconds <= 0:
            raise ValueError("AGNO_JAVA_HTTP_TIMEOUT_SECONDS must be greater than zero")
        self._base_url = base_url.rstrip("/")
        self._oauth_client = oauth_client
        self._client = httpx.Client(timeout=timeout_seconds)

    def get_json(
        self,
        path: str,
        authorization_context: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        if not authorization_context:
            raise JavaApiError("Agent authorization context is missing")
        if (
            not path.startswith(self._ALLOWED_PREFIX)
            or ".." in path.split("/")
            or "?" in path
            or "#" in path
        ):
            raise ValueError("Java tool path is outside the allowed internal API namespace")

        try:
            response = self._client.get(
                self._base_url + path,
                params=params,
                headers={
                    "Authorization": f"Bearer {self._oauth_client.get_access_token()}",
                    "X-Agent-Authorization-Context": authorization_context,
                    "Accept": "application/json",
                },
            )
        except httpx.HTTPError as exc:
            raise JavaApiError("Java insurance service is unavailable") from exc
        except OAuth2TokenError as exc:
            raise JavaApiError("OAuth2 service authentication failed") from exc

        if response.is_error:
            raise JavaApiError(f"Java tool API returned HTTP {response.status_code}")
        try:
            return response.json()
        except ValueError as exc:
            raise JavaApiError("Java tool API returned invalid JSON") from exc

    def post_json(
        self,
        path: str,
        authorization_context: str,
        json_body: dict[str, Any],
    ) -> Any:
        if not authorization_context:
            raise JavaApiError("Agent authorization context is missing")
        if path not in self._ALLOWED_POST_PATHS:
            raise ValueError("Java POST path is not allowlisted")

        try:
            response = self._client.post(
                self._base_url + path,
                content=_encode_json(json_body),
                headers={
                    "Authorization": f"Bearer {self._oauth_client.get_access_token()}",
                    "X-Agent-Authorization-Context": authorization_context,
                    "Accept": "application/json",
                    "Content-Type": "application/json",
                },
            )
        except httpx.HTTPError as exc:
            raise JavaApiError("Java insurance service is unavailable") from exc
        except OAuth2TokenError as exc:
            raise JavaApiError("OAuth2 service authentication failed") from exc

        if response.is_error:
            raise JavaApiError(f"Java tool API returned HTTP {response.status_code}")
        try:
            return response.json()
        except ValueError as exc:
            raise JavaApiError("Java tool API returned invalid JSON") from exc


def _encode_json(value: Any) -> bytes:
    """Encode JSON while preserving Decimal values as exact JSON numbers."""

    def encode(item: Any) -> str:
        if isinstance(item, Decimal):
            if not item.is_finite():
                raise ValueError("Non-finite decimals cannot be sent to Java")
            return format(item, "f")
        if isinstance(item, dict):
            if any(not isinstance(key, str) for key in item):
                raise TypeError("JSON object keys must be strings")
            members = (
                f"{json.dumps(key, ensure_ascii=False)}:{encode(member)}"
                for key, member in item.items()
            )
            return "{" + ",".join(members) + "}"
        if isinstance(item, (list, tuple)):
            return "[" + ",".join(encode(member) for member in item) + "]"
        return json.dumps(item, ensure_ascii=False, allow_nan=False, separators=(",", ":"))

    return encode(value).encode("utf-8")


__all__ = ["JavaApiError", "JavaInternalApiClient"]
