"""Resolve Agno runtime dependencies without exposing credentials to the model."""

import re
from typing import Any, cast

from agno.run import RunContext

from app.contexts.tool import AgentToolContext, AgentToolResultCollector
from app.services.ports import JavaInternalApiPort


class AgentToolContextError(RuntimeError):
    """Raised when a tool does not have a valid per-run Java grant."""


def resolve_tool_context(run_context: RunContext) -> AgentToolContext:
    dependencies: Any = getattr(run_context, "dependencies", None)
    if not isinstance(dependencies, dict):
        raise AgentToolContextError("Authorized tool context is unavailable.")

    client = dependencies.get("java_internal_api")
    authorization_context = dependencies.get("authorization_context")
    collector = dependencies.get("tool_result_collector")
    selected_policy_id = dependencies.get("selected_policy_id")
    if not callable(getattr(client, "get_json", None)):
        raise AgentToolContextError("Java insurance client is unavailable.")
    if not isinstance(authorization_context, str) or not re.fullmatch(
        r"[A-Za-z0-9_-]{43}", authorization_context
    ):
        raise AgentToolContextError("Java authorization grant is invalid or missing.")
    if not isinstance(collector, AgentToolResultCollector):
        raise AgentToolContextError("Per-run result collector is unavailable.")
    if selected_policy_id is not None and (
        not isinstance(selected_policy_id, str)
        or re.fullmatch(r"[0-9]{1,19}", selected_policy_id) is None
    ):
        raise AgentToolContextError("Selected policy reference is invalid.")

    return AgentToolContext(
        java_internal_api=cast(JavaInternalApiPort, client),
        authorization_context=authorization_context,
        result_collector=collector,
        selected_policy_id=selected_policy_id,
    )
