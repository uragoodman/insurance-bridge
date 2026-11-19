"""End-to-end in-memory multi-turn simulation of context preparation and compaction."""

import json
import unittest
from contextlib import contextmanager
from types import SimpleNamespace

from app.domain.conversation_context import ContextBudget, ContextBudgetPolicy
from app.services.chat_application import ContextAwareChatApplicationService
from app.services.context_management import ContextManagementService


class InMemoryContextStore:
    def __init__(self) -> None:
        self.sessions = {}

    def load(self, _user_id, session_id):
        from app.domain.conversation_context import ConversationMemorySnapshot

        return self.sessions.get(session_id, ConversationMemorySnapshot())

    def save(self, _user_id, session_id, memory):
        self.sessions[session_id] = memory


class DeterministicSummaryGenerator:
    def __init__(self) -> None:
        self.calls = 0

    def generate(self, existing_summary, turns, max_chars):
        self.calls += 1
        excerpts = [part for part in existing_summary.splitlines() if part]
        excerpts.extend(turn.user for turn in turns)
        selected = []
        for excerpt in excerpts:
            projected = "\n".join((*selected, excerpt))
            if len(projected) <= max_chars:
                selected.append(excerpt)
        return "\n".join(selected)


class LocalFakeAgent:
    def __init__(self) -> None:
        self.context_states = {}

    def run(self, message, *, session_id, session_state, **_kwargs):
        self.context_states.setdefault(session_id, []).append(session_state)
        return SimpleNamespace(
            content=f"针对本轮问题的简短回答：{message[:80]}", run_id="local-run"
        )

    def is_final_output(self, item):
        return hasattr(item, "run_id")


class NoOpSessionLock:
    @contextmanager
    def acquire(self, _user_id, _session_id):
        yield


def run_simulation() -> dict[str, object]:
    store = InMemoryContextStore()
    summary_generator = DeterministicSummaryGenerator()
    policy = ContextBudgetPolicy(
        ContextBudget(
            context_window_tokens=5000,
            fixed_prompt_tokens=400,
            output_reserve_tokens=500,
            safety_margin_tokens=300,
            soft_limit_ratio=0.75,
        ),
        recent_turn_limit=6,
    )
    management = ContextManagementService(
        store,
        summary_generator,
        policy,
        summary_max_chars=1200,
        recent_turn_limit=6,
        keep_recent_turns=2,
    )
    runner = LocalFakeAgent()
    chat = ContextAwareChatApplicationService(runner, management, NoOpSessionLock())

    for turn_number in range(1, 13):
        message = f"长会话第{turn_number}轮：" + "讨论产品目录、筛选需求和待核验事项" * 24
        chat.run(
            message,
            user_id="simulation-user",
            session_id="long-conversation",
            dependencies={},
            tool_result_collector=SimpleNamespace(sensitive_tool_used=False),
        )

    for turn_number in range(1, 4):
        chat.run(
            f"短会话第{turn_number}轮：请解释一个简单概念。",
            user_id="simulation-user",
            session_id="short-conversation",
            dependencies={},
            tool_result_collector=SimpleNamespace(sensitive_tool_used=False),
        )

    before_sensitive = store.sessions["short-conversation"]
    chat.run(
        "查询我的保单",
        user_id="simulation-user",
        session_id="short-conversation",
        dependencies={},
        tool_result_collector=SimpleNamespace(sensitive_tool_used=True),
    )
    after_sensitive = store.sessions["short-conversation"]

    long_memory = store.sessions["long-conversation"]
    injected_states = runner.context_states["long-conversation"]
    observed_recent_counts = [
        len(state["bolee_conversation_context"]["recent_turns"])
        for state in injected_states
    ]
    report = {
        "long_turns": len(injected_states),
        "summary_calls": summary_generator.calls,
        "long_summary_chars": len(long_memory.summary),
        "final_recent_turns": len(long_memory.recent_turns),
        "max_recent_turns_injected": max(observed_recent_counts, default=0),
        "short_turns": len(runner.context_states["short-conversation"]),
        "short_summary_chars": len(store.sessions["short-conversation"].summary),
        "sensitive_turn_preserved": after_sensitive.revision != before_sensitive.revision,
    }
    if report["summary_calls"] <= 0:
        raise AssertionError("Long conversation never invoked the summary generator")
    if report["max_recent_turns_injected"] > 6:
        raise AssertionError("Context preparation failed to bound recent history")
    if report["sensitive_turn_preserved"]:
        raise AssertionError("Sensitive tool turn was incorrectly saved into context memory")
    return report


class ContextConversationSimulationTests(unittest.TestCase):
    def test_multiple_multi_turn_sessions_exercise_compression(self) -> None:
        report = run_simulation()

        self.assertEqual(report["long_turns"], 12)
        self.assertEqual(report["short_turns"], 4)
        self.assertEqual(report["short_summary_chars"], 0)
        self.assertGreater(report["summary_calls"], 0)
        self.assertFalse(report["sensitive_turn_preserved"])


if __name__ == "__main__":
    print(json.dumps(run_simulation(), ensure_ascii=False, indent=2))
