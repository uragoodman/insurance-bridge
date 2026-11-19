"""Tests for Agno adapter ownership and run-option boundaries."""

import json
import unittest
from types import SimpleNamespace

from app.domain.conversation_context import ConversationMemorySnapshot, ConversationTurn
from app.infrastructure.agno_agent_runner import AgnoAgentRunner
from app.infrastructure.agno_context import AgnoSessionContextStore, AgnoSummaryGenerator


class FakeAgent:
    def __init__(self, sessions=None) -> None:
        self.sessions = sessions or {}
        self.run_args = None
        self.state_updates = []

    def get_session(self, session_id, user_id=None):
        session = self.sessions.get(session_id)
        if user_id is not None and session is not None and session.user_id != user_id:
            return None
        return session

    def update_session_state(self, state, session_id):
        self.state_updates.append((session_id, state))
        return "updated"

    def run(self, message, **kwargs):
        self.run_args = {"message": message, **kwargs}
        return SimpleNamespace(content="answer")


class FakeSummaryAgent:
    def __init__(self, content) -> None:
        self.content = content
        self.run_args = None

    def run(self, message, **kwargs):
        self.run_args = {"message": message, **kwargs}
        return SimpleNamespace(content=self.content)


class AgnoContextAdapterTests(unittest.TestCase):
    def test_store_loads_and_saves_only_owner_session(self) -> None:
        state = {
            "session_state": {
                "bolee_conversation_context": {
                    "schema_version": 1,
                    "revision": 2,
                    "summary": "已有摘要",
                    "recent_turns": [],
                }
            }
        }
        agent = FakeAgent(
            {"session-1": SimpleNamespace(user_id="user-1", session_data=state)}
        )
        store = AgnoSessionContextStore(lambda: agent)

        loaded = store.load("user-1", "session-1")
        store.save(
            "user-1",
            "session-1",
            ConversationMemorySnapshot(summary="新摘要", revision=3),
        )

        self.assertEqual(loaded.summary, "已有摘要")
        self.assertEqual(agent.state_updates[0][0], "session-1")
        self.assertEqual(
            agent.state_updates[0][1]["bolee_conversation_context"]["revision"], 3
        )

    def test_store_rejects_session_owned_by_another_user(self) -> None:
        agent = FakeAgent(
            {"session-1": SimpleNamespace(user_id="user-2", session_data={})}
        )
        store = AgnoSessionContextStore(lambda: agent)

        with self.assertRaises(PermissionError):
            store.load("user-1", "session-1")

    def test_runner_disables_raw_history_and_injects_managed_state(self) -> None:
        agent = FakeAgent()
        runner = AgnoAgentRunner(lambda: agent)

        runner.run(
            "hello",
            user_id="user-1",
            session_id="session-1",
            dependencies={},
            session_state={"bolee_conversation_context": {"summary": "safe"}},
            stream=False,
        )

        self.assertFalse(agent.run_args["add_history_to_context"])
        self.assertTrue(agent.run_args["add_session_state_to_context"])
        self.assertFalse(agent.run_args["add_dependencies_to_context"])
        self.assertEqual(
            agent.run_args["session_state"]["bolee_conversation_context"]["summary"],
            "safe",
        )

    def test_summary_generator_accepts_only_exact_source_excerpts(self) -> None:
        agent = FakeSummaryAgent(json.dumps(["用户明确说过的目标"], ensure_ascii=False))
        generator = AgnoSummaryGenerator.__new__(AgnoSummaryGenerator)
        generator._agent = agent

        summary = generator.generate(
            "",
            (ConversationTurn("用户明确说过的目标", "助手添加的未经确认内容"),),
            300,
        )

        self.assertEqual(summary, "用户明确说过的目标")
        self.assertNotIn("助手添加的未经确认内容", agent.run_args["message"])

    def test_summary_generator_rejects_model_invented_content(self) -> None:
        agent = FakeSummaryAgent(json.dumps(["模型编造的条件"], ensure_ascii=False))
        generator = AgnoSummaryGenerator.__new__(AgnoSummaryGenerator)
        generator._agent = agent

        with self.assertRaisesRegex(ValueError, "not present in allowed sources"):
            generator.generate("", (ConversationTurn("用户只说了产品分类", "回答"),), 300)


if __name__ == "__main__":
    unittest.main()
