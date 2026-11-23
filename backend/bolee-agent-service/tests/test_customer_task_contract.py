"""Tests for the one-entry customer task contract and persisted task state."""

import unittest

from pydantic import ValidationError

from app.domain.conversation_context import ConversationMemorySnapshot
from app.domain.customer_task import (
    ActiveTaskState,
    CustomerTaskIntent,
    CustomerTaskStatus,
)
from app.schemas.customer_task import CustomerTaskRequest


class CustomerTaskContractTests(unittest.TestCase):
    def test_request_rejects_action_from_a_different_intent(self) -> None:
        with self.assertRaises(ValidationError):
            CustomerTaskRequest.model_validate(
                {
                    "intent": "TERMS_QA",
                    "action": "SEARCH_PRODUCTS",
                    "keyword": "旅游险",
                }
            )

    def test_request_requires_java_resolved_products_before_comparison_payload(self) -> None:
        with self.assertRaises(ValidationError):
            CustomerTaskRequest.model_validate(
                {
                    "intent": "PRODUCT_COMPARISON",
                    "action": "COMPARE_PRODUCTS",
                    "category_id": "42",
                    "product_ids": ["p-1"],
                }
            )

    def test_request_rejects_fields_that_do_not_belong_to_action(self) -> None:
        with self.assertRaises(ValidationError):
            CustomerTaskRequest.model_validate(
                {
                    "intent": "TERMS_QA",
                    "action": "SEARCH_PRODUCT_TERMS",
                    "product_name": "旅游险",
                    "question": "等待期多久",
                    "policy_id": "123",
                }
            )

    def test_product_search_limit_matches_downstream_cap(self) -> None:
        with self.assertRaises(ValidationError):
            CustomerTaskRequest.model_validate(
                {
                    "intent": "PRODUCT_CATALOG",
                    "action": "SEARCH_PRODUCTS",
                    "keyword": "旅游险",
                    "limit": 4,
                }
            )

    def test_task_state_round_trips_inside_context_schema_v3(self) -> None:
        task = ActiveTaskState(
            task_id="task-1",
            intent=CustomerTaskIntent.TERMS_QA,
            status=CustomerTaskStatus.WAITING_FOR_USER,
            stage="SEARCH_PRODUCT_TERMS",
            slots=(("product_name", "境内旅游险"),),
            missing_slots=("product_version",),
        )
        memory = ConversationMemorySnapshot(active_task=task)
        restored = ConversationMemorySnapshot.from_session_state(
            memory.to_session_state(), summary_max_chars=1000
        )

        self.assertEqual(restored.schema_version, 3)
        self.assertEqual(restored.active_task.intent, CustomerTaskIntent.TERMS_QA)
        self.assertEqual(restored.active_task.status, CustomerTaskStatus.WAITING_FOR_USER)
        self.assertEqual(restored.active_task.slots, (("product_name", "境内旅游险"),))
        self.assertEqual(restored.active_task.missing_slots, ("product_version",))

    def test_old_context_schema_migrates_without_an_active_task(self) -> None:
        restored = ConversationMemorySnapshot.from_state(
            {"schema_version": 2, "summary": "旧摘要", "recent_turns": []},
            summary_max_chars=1000,
        )

        self.assertFalse(restored.active_task.is_active)
        self.assertEqual(restored.summary, "旧摘要")

    def test_active_task_expires_after_bounded_idle_turns(self) -> None:
        task = ActiveTaskState(
            task_id="task-1",
            intent=CustomerTaskIntent.PRODUCT_RECOMMENDATION,
            status=CustomerTaskStatus.WAITING_FOR_USER,
            stage="RECOMMEND_PRODUCTS",
        )
        for _ in range(ActiveTaskState.MAX_AGE_TURNS):
            task = task.next_turn()

        self.assertFalse(task.is_active)


if __name__ == "__main__":
    unittest.main()
