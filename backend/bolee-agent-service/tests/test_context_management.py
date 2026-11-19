"""Tests for deterministic context budgeting and privacy-aware summarization."""

import unittest

from app.domain.conversation_context import (
    ContextBudget,
    ContextBudgetPolicy,
    ConversationMemorySnapshot,
    ConversationTurn,
)
from app.services.context_management import (
    ContextCompactionUnavailableError,
    ContextInputTooLargeError,
    ContextManagementService,
)


class MemoryStore:
    def __init__(self, memory: ConversationMemorySnapshot | None = None) -> None:
        self.memory = memory or ConversationMemorySnapshot()
        self.saved: list[ConversationMemorySnapshot] = []

    def load(self, _user_id: str, _session_id: str) -> ConversationMemorySnapshot:
        return self.memory

    def save(
        self, _user_id: str, _session_id: str, memory: ConversationMemorySnapshot
    ) -> None:
        self.memory = memory
        self.saved.append(memory)


class FakeSummaryGenerator:
    def __init__(self, fail: bool = False) -> None:
        self.calls: list[tuple[str, tuple[ConversationTurn, ...], int]] = []
        self.fail = fail

    def generate(
        self,
        existing_summary: str,
        turns: tuple[ConversationTurn, ...],
        max_chars: int,
    ) -> str:
        self.calls.append((existing_summary, turns, max_chars))
        if self.fail:
            raise RuntimeError("summary model unavailable")
        pieces = [existing_summary] if existing_summary else []
        pieces.extend(turn.user for turn in turns)
        return "；".join(pieces)


def make_policy(*, context_window: int = 2000, fixed: int = 100) -> ContextBudgetPolicy:
    return ContextBudgetPolicy(
        ContextBudget(
            context_window_tokens=context_window,
            fixed_prompt_tokens=fixed,
            output_reserve_tokens=200,
            safety_margin_tokens=100,
            soft_limit_ratio=0.75,
        ),
        recent_turn_limit=4,
    )


class ContextManagementTests(unittest.TestCase):
    def test_compacts_older_turns_and_keeps_recent_window(self) -> None:
        turns = tuple(
            ConversationTurn(user=f"旧问题 {index}", assistant=f"回答 {index}")
            for index in range(5)
        )
        store = MemoryStore(ConversationMemorySnapshot(summary="原摘要", recent_turns=turns))
        summarizer = FakeSummaryGenerator()
        service = ContextManagementService(
            store,
            summarizer,
            make_policy(),
            recent_turn_limit=4,
            keep_recent_turns=2,
        )

        plan = service.prepare("user-1", "session-1", "继续刚才的话题")

        self.assertEqual([turn.user for turn in plan.memory.recent_turns], ["旧问题 3", "旧问题 4"])
        self.assertIn("旧问题 0", plan.memory.summary)
        self.assertEqual(len(summarizer.calls), 1)
        self.assertFalse(plan.exceeds_hard_limit)

    def test_structured_memory_survives_round_trip_and_compaction(self) -> None:
        memory = ConversationMemorySnapshot(
            recent_turns=tuple(
                ConversationTurn(user=f"历史问题 {index}", assistant=f"答复 {index}")
                for index in range(5)
            ),
            conversation_goal="设计保险产品目录演示",
            current_topic="产品分类",
            explicit_confirmations=("是的，按这个条件",),
            pending_questions=("请问预算范围是多少？",),
            business_references=("product_id=p-1;product_version=2",),
        )
        restored = ConversationMemorySnapshot.from_session_state(
            memory.to_session_state(), summary_max_chars=4000
        )
        store = MemoryStore(restored)
        service = ContextManagementService(
            store,
            FakeSummaryGenerator(),
            make_policy(),
            recent_turn_limit=4,
            keep_recent_turns=2,
        )

        plan = service.prepare("user-1", "session-1", "继续")

        self.assertEqual(plan.memory.current_topic, "产品分类")
        self.assertEqual(plan.memory.conversation_goal, "设计保险产品目录演示")
        self.assertEqual(plan.memory.explicit_confirmations, ("是的，按这个条件",))
        self.assertEqual(plan.memory.pending_questions, ("请问预算范围是多少？",))
        self.assertEqual(plan.memory.business_references, ("product_id=p-1;product_version=2",))

    def test_safe_turn_is_saved_but_policy_tool_turn_is_not(self) -> None:
        store = MemoryStore()
        service = ContextManagementService(store, FakeSummaryGenerator(), make_policy())
        initial = ConversationMemorySnapshot()

        safe = service.record_turn(
            "user-1",
            "session-1",
            initial,
            "想了解产品分类",
            "可以查看产品目录。",
            sensitive_tool_used=False,
        )
        skipped = service.record_turn(
            "user-1",
            "session-1",
            safe,
            "查一下我的保单",
            "查到了。",
            sensitive_tool_used=True,
        )

        self.assertEqual(len(store.saved), 1)
        self.assertEqual(len(skipped.recent_turns), 1)
        self.assertEqual(skipped.revision, 1)

    def test_safe_turn_updates_structured_confirmation_and_pending_question(self) -> None:
        store = MemoryStore()
        service = ContextManagementService(store, FakeSummaryGenerator(), make_policy())

        updated = service.record_turn(
            "user-1",
            "session-1",
            ConversationMemorySnapshot(),
            "是的，按这个预算继续",
            "请问您还希望比较哪些产品？",
            sensitive_tool_used=False,
            business_references=("product_id=p-1;product_version=2",),
        )

        self.assertEqual(updated.current_topic, "是的，按这个预算继续")
        self.assertEqual(updated.explicit_confirmations, ("是的，按这个预算继续",))
        self.assertEqual(updated.pending_questions, ("请问您还希望比较哪些产品？",))
        self.assertEqual(updated.business_references, ("product_id=p-1;product_version=2",))

    def test_personal_data_like_input_is_not_persisted(self) -> None:
        store = MemoryStore()
        service = ContextManagementService(store, FakeSummaryGenerator(), make_policy())

        result = service.record_turn(
            "user-1",
            "session-1",
            ConversationMemorySnapshot(),
            "我的手机号是 13800138000",
            "收到。",
            sensitive_tool_used=False,
        )

        self.assertEqual(result.revision, 0)
        self.assertEqual(store.saved, [])

    def test_numeric_product_id_is_not_mistaken_for_personal_data(self) -> None:
        store = MemoryStore()
        service = ContextManagementService(store, FakeSummaryGenerator(), make_policy())

        result = service.record_turn(
            "user-1",
            "session-1",
            ConversationMemorySnapshot(),
            "请看产品 1234567890123456789",
            "我会先核对目录。",
            sensitive_tool_used=False,
        )

        self.assertEqual(result.revision, 1)
        self.assertEqual(len(store.saved), 1)

    def test_oversized_current_input_is_rejected_before_model_execution(self) -> None:
        service = ContextManagementService(
            MemoryStore(), FakeSummaryGenerator(), make_policy(context_window=256, fixed=100)
        )

        with self.assertRaises(ContextInputTooLargeError):
            service.prepare("user-1", "session-1", "重要问题 " * 400)

    def test_summary_failure_rejects_over_budget_turn_and_preserves_prior_memory(self) -> None:
        turns = tuple(
            ConversationTurn(user=f"历史问题 {index}", assistant="历史回答 " * 40)
            for index in range(7)
        )
        prior = ConversationMemorySnapshot(summary="previous", recent_turns=turns)
        store = MemoryStore(prior)
        service = ContextManagementService(
            store,
            FakeSummaryGenerator(fail=True),
            make_policy(context_window=1200, fixed=100),
            recent_turn_limit=4,
            keep_recent_turns=2,
        )

        with self.assertRaises(ContextCompactionUnavailableError):
            service.prepare("user-1", "session-1", "新问题")

        self.assertEqual(store.memory, prior)
        self.assertEqual(store.saved, [])

    def test_token_estimator_counts_cjk_conservatively(self) -> None:
        policy = make_policy()

        self.assertGreater(policy.estimate_tokens("你好世界"), policy.estimate_tokens("test"))


if __name__ == "__main__":
    unittest.main()
