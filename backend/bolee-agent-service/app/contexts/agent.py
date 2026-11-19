"""Runtime dependencies for an individual Agno run."""

from dataclasses import dataclass, field
from typing import Any

from app.contexts.tool import AgentToolResultCollector


@dataclass(frozen=True, slots=True)
class AgentDependencies:
    java_internal_api: Any
    authorization_context: str = field(repr=False)
    tool_result_collector: AgentToolResultCollector
    selected_policy_id: str | None = None

    def as_agno_dependencies(self) -> dict[str, Any]:
        return {
            "java_internal_api": self.java_internal_api,
            "authorization_context": self.authorization_context,
            "tool_result_collector": self.tool_result_collector,
            "selected_policy_id": self.selected_policy_id,
        }
