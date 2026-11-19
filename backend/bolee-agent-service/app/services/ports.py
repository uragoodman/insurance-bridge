"""Ports consumed by application services."""

from contextlib import AbstractContextManager
from typing import Any, Protocol

from app.domain.conversation_context import ConversationMemorySnapshot, ConversationTurn


class JavaInternalApiPort(Protocol):
    def get_json(
        self,
        path: str,
        authorization_context: str,
        params: dict[str, Any] | None = None,
    ) -> Any:
        """Call an allowlisted Java internal API and return its JSON payload."""

    def post_json(
        self,
        path: str,
        authorization_context: str,
        json_body: dict[str, Any],
    ) -> Any:
        """POST a typed payload to an allowlisted Java internal API."""


class SessionContextStorePort(Protocol):
    def load(self, user_id: str, session_id: str) -> ConversationMemorySnapshot:
        """Load the owner-scoped, versioned context projection for a session."""

    def save(
        self, user_id: str, session_id: str, memory: ConversationMemorySnapshot
    ) -> None:
        """Persist the context projection within the Agent session lifecycle."""


class SummaryGeneratorPort(Protocol):
    def generate(
        self,
        existing_summary: str,
        turns: tuple[ConversationTurn, ...],
        max_chars: int,
    ) -> str:
        """Summarize already-screened turns without access to tools or raw history."""


class SessionTurnLockPort(Protocol):
    def acquire(self, user_id: str, session_id: str) -> AbstractContextManager[None]:
        """Serialize context load, Agent execution and context persistence per session."""


class AgentRunnerPort(Protocol):
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
        """Run the configured Agent with an explicit, bounded context projection."""

    def is_final_output(self, item: Any) -> bool:
        """Identify the final Agno run object in a stream without exposing SDK types."""
