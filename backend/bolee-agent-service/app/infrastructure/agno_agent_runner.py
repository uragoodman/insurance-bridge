"""Agno execution adapter with raw history disabled by default."""

from __future__ import annotations

from collections.abc import Callable
from typing import Any


class AgnoAgentRunner:
    def __init__(self, agent_provider: Callable[[], Any]) -> None:
        self._agent_provider = agent_provider

    def run(
        self,
        message: str,
        *,
        user_id: str,
        session_id: str,
        dependencies: dict[str, Any],
        session_state: dict[str, Any],
        stream: bool,
        yield_run_output: bool = False,
    ) -> Any:
        agent = self._agent_provider()
        return agent.run(
            message,
            user_id=user_id,
            session_id=session_id,
            dependencies=dependencies,
            session_state=session_state,
            add_history_to_context=False,
            add_session_state_to_context=True,
            add_dependencies_to_context=False,
            stream=stream,
            stream_events=False,
            yield_run_output=yield_run_output,
        )

    def is_final_output(self, item: Any) -> bool:
        from agno.run.agent import RunOutput

        return isinstance(item, RunOutput)
