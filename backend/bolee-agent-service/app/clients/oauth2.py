"""OAuth2 client-credentials token acquisition and bounded in-memory caching."""

import threading
import time

import httpx


class OAuth2TokenError(RuntimeError):
    """Sanitized OAuth2 token endpoint failure."""


class OAuth2ClientCredentials:
    def __init__(
        self,
        token_url: str,
        client_id: str,
        client_secret: str,
        scope: str,
        timeout_seconds: float = 5.0,
    ):
        if timeout_seconds <= 0:
            raise ValueError("OAuth2 timeout must be greater than zero")
        self._token_url = token_url
        self._client_id = client_id
        self._client_secret = client_secret
        self._scope = scope
        self._client = httpx.Client(timeout=timeout_seconds, auth=(client_id, client_secret))
        self._lock = threading.Lock()
        self._access_token: str | None = None
        self._expires_at = 0.0

    def get_access_token(self) -> str:
        if not self._token_url or not self._client_id or not self._client_secret or not self._scope:
            raise OAuth2TokenError("OAuth2 client credentials are not configured")
        with self._lock:
            if self._access_token and time.monotonic() < self._expires_at - 30:
                return self._access_token
            try:
                response = self._client.post(
                    self._token_url,
                    data={"grant_type": "client_credentials", "scope": self._scope},
                    headers={"Accept": "application/json"},
                )
                response.raise_for_status()
                payload = response.json()
                if not isinstance(payload, dict):
                    raise OAuth2TokenError("OAuth2 endpoint returned an invalid response")
                token = payload.get("access_token")
                expires_in = int(payload.get("expires_in", 0))
                token_type = payload.get("token_type", "Bearer")
                if (
                    not isinstance(token_type, str)
                    or token_type.lower() != "bearer"
                    or not token
                    or expires_in <= 0
                ):
                    raise OAuth2TokenError("OAuth2 endpoint returned an invalid access token")
            except OAuth2TokenError:
                raise
            except (httpx.HTTPError, ValueError, TypeError) as exc:
                raise OAuth2TokenError(
                    "OAuth2 token endpoint is unavailable or rejected the client"
                ) from exc

            self._access_token = token
            self._expires_at = time.monotonic() + expires_in
            return token


__all__ = ["OAuth2ClientCredentials", "OAuth2TokenError"]
