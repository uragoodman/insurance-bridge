"""Typed Java HTTP client for own-policy business endpoints."""

import re
from typing import Any

from app.services.ports import JavaInternalApiPort


class OwnPolicyJavaClient:
    _SUMMARY_PATH = "/internal/agent-tools/v1/policies/summary"
    _DETAIL_PATH_PREFIX = "/internal/agent-tools/v1/policies/"

    def __init__(self, client: JavaInternalApiPort) -> None:
        self._client = client

    def get_summary(self, authorization_context: str) -> Any:
        return self._client.get_json(
            self._SUMMARY_PATH,
            authorization_context=authorization_context,
        )

    def get_policy(self, policy_id: str, authorization_context: str) -> Any:
        if not isinstance(policy_id, str) or not re.fullmatch(r"[0-9]{1,19}", policy_id):
            raise ValueError("Policy reference must be a numeric ID returned by Java")
        return self._client.get_json(
            self._DETAIL_PATH_PREFIX + policy_id,
            authorization_context=authorization_context,
        )
