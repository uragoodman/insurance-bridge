"""Local verification of OAuth2 JWTs issued by the Java authorization server."""

import threading

import httpx
import jwt


class OAuth2JWTRejected(RuntimeError):
    def __init__(self, detail: str, status_code: int = 401):
        super().__init__(detail)
        self.detail = detail
        self.status_code = status_code


class OAuth2JWTUnavailable(RuntimeError):
    """The trusted OAuth verification key could not be loaded."""


class OAuth2JWTValidator:
    def __init__(
        self,
        token_key_url: str,
        expected_client_ids: dict[str, str],
        timeout_seconds: float = 5.0,
    ):
        self._token_key_url = token_key_url
        self._expected_client_ids = expected_client_ids
        self._timeout_seconds = timeout_seconds
        self._public_key: str | None = None
        self._lock = threading.Lock()

    def require(self, token: str, required_scope: str) -> None:
        if not token:
            raise OAuth2JWTRejected("bearer access token required")
        expected_client_id = self._expected_client_ids.get(required_scope)
        if not expected_client_id:
            raise OAuth2JWTUnavailable("OAuth client identity or required scope is not configured")
        try:
            claims = jwt.decode(
                token,
                self._key(),
                algorithms=["RS256"],
                options={"require": ["exp"]},
            )
        except OAuth2JWTUnavailable:
            raise
        except jwt.PyJWTError as exc:
            raise OAuth2JWTRejected("invalid or expired bearer access token") from exc

        if claims.get("client_id") != expected_client_id:
            raise OAuth2JWTRejected("OAuth client is not permitted", status_code=403)
        scope_claim = claims.get("scope", [])
        if isinstance(scope_claim, str):
            scopes = set(scope_claim.split())
        elif isinstance(scope_claim, (list, tuple, set)) and all(
            isinstance(scope, str) for scope in scope_claim
        ):
            scopes = set(scope_claim)
        else:
            scopes = set()
        if required_scope not in scopes:
            raise OAuth2JWTRejected("OAuth scope denied", status_code=403)

    def _key(self) -> str:
        if self._public_key:
            return self._public_key
        with self._lock:
            if self._public_key:
                return self._public_key
            if not self._token_key_url:
                raise OAuth2JWTUnavailable("OAuth token key URL is not configured")
            try:
                response = httpx.get(self._token_key_url, timeout=self._timeout_seconds)
                response.raise_for_status()
                payload = response.json()
                public_key = payload.get("value") if isinstance(payload, dict) else None
                if not isinstance(public_key, str) or "BEGIN PUBLIC KEY" not in public_key:
                    raise OAuth2JWTUnavailable("OAuth token key endpoint returned no public key")
                self._public_key = public_key
                return public_key
            except OAuth2JWTUnavailable:
                raise
            except (httpx.HTTPError, ValueError, TypeError) as exc:
                raise OAuth2JWTUnavailable("Could not load OAuth token verification key") from exc


__all__ = ["OAuth2JWTRejected", "OAuth2JWTUnavailable", "OAuth2JWTValidator"]
