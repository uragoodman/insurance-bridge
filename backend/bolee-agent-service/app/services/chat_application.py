"""Shared synchronous and streaming chat orchestration."""

from __future__ import annotations

import logging
from collections.abc import Iterator
from typing import Any

from app.domain.conversation_context import ContextPlan
from app.services.context_management import ContextManagementService
from app.services.ports import AgentRunnerPort, SessionTurnLockPort

logger = logging.getLogger(__name__)


class ContextAwareChatApplicationService:
    """Apply one context policy and session lock to every chat transport."""

    def __init__(
        self,
        agent_runner: AgentRunnerPort,
        context_management: ContextManagementService,
        session_lock: SessionTurnLockPort,
    ) -> None:
        self._agent_runner = agent_runner
        self._context_management = context_management
        self._session_lock = session_lock

    def run(
        self,
        message: str,
        *,
        user_id: str,
        session_id: str,
        dependencies: dict[str, Any],
        tool_result_collector: Any,
    ) -> Any:
        with self._session_lock.acquire(user_id, session_id):
            plan = self._context_management.prepare(user_id, session_id, message)
            result = self._agent_runner.run(
                message,
                user_id=user_id,
                session_id=session_id,
                dependencies=dependencies,
                session_state=plan.memory.to_session_state(),
                stream=False,
            )
            self._record_result(
                plan, user_id, session_id, message, result, tool_result_collector
            )
            return result

    def stream(
        self,
        message: str,
        *,
        user_id: str,
        session_id: str,
        dependencies: dict[str, Any],
        tool_result_collector: Any,
    ) -> Iterator[Any]:
        with self._session_lock.acquire(user_id, session_id):
            plan = self._context_management.prepare(user_id, session_id, message)
            stream = self._agent_runner.run(
                message,
                user_id=user_id,
                session_id=session_id,
                dependencies=dependencies,
                session_state=plan.memory.to_session_state(),
                stream=True,
                yield_run_output=True,
            )
            final_output = None
            for item in stream:
                if self._agent_runner.is_final_output(item):
                    final_output = item
                else:
                    yield item
            if final_output is None:
                raise RuntimeError("Agent stream ended without final run output")
            self._record_result(
                plan,
                user_id,
                session_id,
                message,
                final_output,
                tool_result_collector,
            )
            yield final_output

    def is_final_output(self, item: Any) -> bool:
        return self._agent_runner.is_final_output(item)

    def _record_result(
        self,
        plan: ContextPlan,
        user_id: str,
        session_id: str,
        message: str,
        result: Any,
        tool_result_collector: Any,
    ) -> None:
        answer = getattr(result, "content", "")
        answer = answer if isinstance(answer, str) else str(answer or "")
        provider_input_tokens = self._provider_input_tokens(result)
        logger.info(
            "Context budget estimate: session=%s estimated_input_tokens=%d "
            "estimated_limit_tokens=%d provider_input_tokens=%s",
            session_id,
            plan.estimated_input_tokens,
            plan.estimated_input_limit_tokens,
            provider_input_tokens if provider_input_tokens is not None else "unavailable",
        )
        try:
            self._context_management.record_turn(
                user_id,
                session_id,
                plan.memory,
                message,
                answer,
                sensitive_tool_used=bool(
                    getattr(tool_result_collector, "sensitive_tool_used", False)
                ),
                business_references=tuple(
                    getattr(tool_result_collector, "context_references", ())
                ),
            )
        except Exception:
            # A failed optional memory write must not turn a completed answer into a retry.
            logger.exception("Unable to persist conversation context for session %s", session_id)

    @staticmethod
    def _provider_input_tokens(result: Any) -> int | None:
        metrics = getattr(result, "metrics", None)
        if hasattr(metrics, "model_dump"):
            metrics = metrics.model_dump()
        elif hasattr(metrics, "dict"):
            metrics = metrics.dict()
        if not isinstance(metrics, dict):
            return None
        value = metrics.get("input_tokens")
        if isinstance(value, int) and value >= 0:
            return value
        usage = metrics.get("usage")
        if isinstance(usage, dict):
            value = usage.get("prompt_tokens", usage.get("input_tokens"))
            if isinstance(value, int) and value >= 0:
                return value
        return None
