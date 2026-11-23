"""Service-authenticated client for Java's terms indexing task API."""

from typing import Any
from urllib.parse import urlsplit

import httpx

from app.clients.oauth2 import OAuth2ClientCredentials, OAuth2TokenError
from app.schemas.insurance_terms import TermIndexTask


class JavaTermIndexApiError(RuntimeError):
    """Sanitized task API failure."""


class JavaTermIndexApiClient:
    _TASK_ROOT = "/internal/agent-index/v1/tasks"

    def __init__(
        self,
        base_url: str,
        oauth_client: OAuth2ClientCredentials,
        timeout_seconds: float = 20.0,
    ) -> None:
        parsed = urlsplit(base_url)
        if (
            parsed.scheme not in {"http", "https"}
            or not parsed.netloc
            or parsed.path not in {"", "/"}
        ):
            raise ValueError("Java service URL must be an HTTP(S) origin")
        if timeout_seconds <= 0:
            raise ValueError("Java HTTP timeout must be positive")
        self._base_url = base_url.rstrip("/")
        self._oauth = oauth_client
        self._client = httpx.Client(timeout=timeout_seconds)

    def claim(self) -> TermIndexTask | None:
        response = self._request("POST", f"{self._TASK_ROOT}/claim")
        if response.status_code == 204:
            return None
        if response.status_code != 200:
            raise JavaTermIndexApiError(f"Java task claim returned HTTP {response.status_code}")
        try:
            return TermIndexTask.model_validate(response.json())
        except Exception as exc:
            raise JavaTermIndexApiError("Java returned an invalid indexing task") from exc

    def download(self, task: TermIndexTask, target_path: str) -> int:
        if task.action != "INDEX" or not task.content_path:
            raise JavaTermIndexApiError("INDEX task has no content path")
        expected_prefix = f"{self._TASK_ROOT}/{task.task_id}/content"
        if task.content_path != expected_prefix:
            raise JavaTermIndexApiError("Java task returned an unexpected content path")
        try:
            headers = {"X-Task-Lease-Token": task.lease_token}
            for attempt in range(2):
                token = self._oauth.get_access_token()
                try:
                    with self._client.stream(
                        "GET",
                        self._base_url + task.content_path,
                        headers={
                            "Authorization": f"Bearer {token}",
                            "Accept": "application/octet-stream",
                            **headers,
                        },
                    ) as response:
                        if response.status_code == 401 and attempt == 0:
                            self._oauth.invalidate(token)
                            continue
                        if response.status_code != 200:
                            raise JavaTermIndexApiError(
                                f"Java content download returned HTTP {response.status_code}"
                            )
                        length = 0
                        with open(target_path, "wb") as output:
                            for chunk in response.iter_bytes(64 * 1024):
                                length += len(chunk)
                                if length > task.file_size:
                                    raise JavaTermIndexApiError(
                                        "Downloaded source exceeds its declared size"
                                    )
                                output.write(chunk)
                        if length != task.file_size:
                            raise JavaTermIndexApiError(
                                "Downloaded source size does not match the task"
                            )
                        return length
                except httpx.HTTPError as exc:
                    raise JavaTermIndexApiError("Java content service is unavailable") from exc
        except OSError as exc:
            raise JavaTermIndexApiError("Could not write the source temporary file") from exc
        except OAuth2TokenError as exc:
            raise JavaTermIndexApiError("OAuth2 task credentials were rejected") from exc
        raise JavaTermIndexApiError("Java content service rejected authentication")

    def heartbeat(self, task: TermIndexTask) -> None:
        self._post_task_action(task, "heartbeat", {"leaseToken": task.lease_token})

    def complete(self, task: TermIndexTask) -> None:
        self._post_task_action(
            task,
            "complete",
            {
                "leaseToken": task.lease_token,
                "sourceSha256": task.source_sha256,
            },
        )

    def fail(self, task: TermIndexTask, reason: str, retryable: bool) -> None:
        self._post_task_action(
            task,
            "fail",
            {
                "leaseToken": task.lease_token,
                "reason": reason[:1000],
                "retryable": retryable,
            },
        )

    def _post_task_action(self, task: TermIndexTask, action: str, payload: dict[str, Any]) -> None:
        response = self._request("POST", f"{self._TASK_ROOT}/{task.task_id}/{action}", json=payload)
        if response.status_code != 200:
            raise JavaTermIndexApiError(f"Java task {action} returned HTTP {response.status_code}")
        try:
            envelope = response.json()
            if (
                not isinstance(envelope, dict)
                or envelope.get("code") != 200
                or envelope.get("data") is not True
            ):
                raise JavaTermIndexApiError(f"Java task {action} was not accepted")
        except ValueError as exc:
            raise JavaTermIndexApiError("Java task API returned invalid JSON") from exc

    def _request(self, method: str, path: str, **kwargs: Any) -> httpx.Response:
        if (
            not path.startswith(self._TASK_ROOT + "/")
            or ".." in path.split("/")
            or "?" in path
            or "#" in path
        ):
            raise JavaTermIndexApiError("Task API path is not allowlisted")
        try:
            extra_headers = kwargs.pop("headers", {})
            for attempt in range(2):
                token = self._oauth.get_access_token()
                try:
                    response = self._client.request(
                        method,
                        self._base_url + path,
                        headers={
                            "Authorization": f"Bearer {token}",
                            "Accept": "application/json",
                            **extra_headers,
                        },
                        **kwargs,
                    )
                except httpx.HTTPError as exc:
                    raise JavaTermIndexApiError("Java task service is unavailable") from exc
                if response.status_code != 401 or attempt == 1:
                    return response
                self._oauth.invalidate(token)
        except OAuth2TokenError as exc:
            raise JavaTermIndexApiError("OAuth2 task credentials were rejected") from exc
        raise JavaTermIndexApiError("Java task service rejected authentication")


__all__ = ["JavaTermIndexApiClient", "JavaTermIndexApiError"]
