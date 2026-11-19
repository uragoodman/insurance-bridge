"""Tests for shared chat orchestration and session locking."""

import unittest
from contextlib import contextmanager
from types import SimpleNamespace

from app.domain.conversation_context import (
    ContextBudget,
    ContextBudgetPolicy,
    ConversationMemorySnapshot,
)
from app.services.chat_application import ContextAwareChatApplicationService
from app.services.context_management import ContextManagementService


class MemoryStore:
    def __init__(self) -> None:
        self.memory = ConversationMemorySnapshot()

    def load(self, _user_id: str, _session_id: str) -> ConversationMemorySnapshot:
        return self.memory

    def save(self, _user_id: str, _session_id: str, memory: ConversationMemorySnapshot) -> None:
        self.memory = memory


class FakeSummaryGenerator:
    def generate(self, existing_summary, turns, _max_chars):
        return existing_summary + " ".join(turn.user for turn in turns)


class RecordingLock:
    def __init__(self) -> None:
        self.calls: list[tuple[str, str]] = []

    @contextmanager
    def acquire(self, user_id: str, session_id: str):
        self.calls.append((user_id, session_id))
        yield


class FakeRunner:
    def __init__(self, stream_items=None) -> None:
        self.calls: list[dict[str, object]] = []
        self.stream_items = stream_items

    def run(self, message, **kwargs):
        self.calls.append({"message": message, **kwargs})
        if kwargs["stream"]:
            return iter(self.stream_items or [])
        return SimpleNamespace(content="答复", run_id="run-1")

    def is_final_output(self, item):
        return isinstance(item, SimpleNamespace) and hasattr(item, "run_id")


class ChatApplicationTests(unittest.TestCase):
    def setUp(self) -> None:
        self.store = MemoryStore()
        self.lock = RecordingLock()
        self.runner = FakeRunner()
        policy = ContextBudgetPolicy(
            ContextBudget(4096, 100, 500, 100, 0.75), recent_turn_limit=4
        )
        management = ContextManagementService(
            self.store, FakeSummaryGenerator(), policy
        )
        self.app = ContextAwareChatApplicationService(self.runner, management, self.lock)

    def test_sync_turn_disables_raw_history_and_persists_safe_memory(self) -> None:
        collector = SimpleNamespace(
            sensitive_tool_used=False,
            context_references=("product_id=p-1;product_version=2",),
        )
        result = self.app.run(
            "想了解产品",
            user_id="user-1",
            session_id="session-1",
            dependencies={"tool_result_collector": collector},
            tool_result_collector=collector,
        )

        self.assertEqual(result.content, "答复")
        self.assertFalse(self.runner.calls[0]["stream"])
        self.assertIn("bolee_conversation_context", self.runner.calls[0]["session_state"])
        self.assertEqual(self.lock.calls, [("user-1", "session-1")])
        self.assertEqual(self.store.memory.recent_turns[0].user, "想了解产品")
        self.assertEqual(
            self.store.memory.business_references,
            ("product_id=p-1;product_version=2",),
        )

    def test_sensitive_tool_result_is_not_added_to_memory(self) -> None:
        collector = SimpleNamespace(sensitive_tool_used=True)

        self.app.run(
            "查本人保单",
            user_id="user-1",
            session_id="session-1",
            dependencies={},
            tool_result_collector=collector,
        )

        self.assertEqual(self.store.memory.revision, 0)
        self.assertEqual(self.store.memory.recent_turns, ())

    def test_stream_turn_holds_lock_until_final_output_and_records_once(self) -> None:
        final_output = SimpleNamespace(content="流式答复", run_id="run-2")
        self.runner.stream_items = [
            SimpleNamespace(event="RunContent", content="流式"),
            final_output,
        ]
        collector = SimpleNamespace(sensitive_tool_used=False)

        items = list(
            self.app.stream(
                "你好",
                user_id="user-1",
                session_id="session-2",
                dependencies={},
                tool_result_collector=collector,
            )
        )

        self.assertEqual(items[0].content, "流式")
        self.assertIs(items[1], final_output)
        self.assertEqual(self.store.memory.revision, 1)
        self.assertEqual(self.lock.calls, [("user-1", "session-2")])
        self.assertTrue(self.runner.calls[0]["yield_run_output"])


if __name__ == "__main__":
    unittest.main()
