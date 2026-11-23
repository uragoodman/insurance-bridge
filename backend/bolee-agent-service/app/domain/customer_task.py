"""Small, bounded state model for one active customer-service task."""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import StrEnum
from typing import Any
from uuid import uuid4


class CustomerTaskIntent(StrEnum):
    PRODUCT_CATALOG = "PRODUCT_CATALOG"
    PRODUCT_RECOMMENDATION = "PRODUCT_RECOMMENDATION"
    PRODUCT_COMPARISON = "PRODUCT_COMPARISON"
    TERMS_QA = "TERMS_QA"
    OWN_POLICY = "OWN_POLICY"


class CustomerTaskStatus(StrEnum):
    IN_PROGRESS = "IN_PROGRESS"
    WAITING_FOR_USER = "WAITING_FOR_USER"
    COMPLETED = "COMPLETED"
    SUPERSEDED = "SUPERSEDED"


@dataclass(frozen=True, slots=True)
class ActiveTaskState:
    """Persist only bounded task metadata, never raw tool payloads."""

    task_id: str = field(default_factory=lambda: str(uuid4()))
    intent: CustomerTaskIntent | None = None
    status: CustomerTaskStatus = CustomerTaskStatus.COMPLETED
    stage: str = "IDLE"
    slots: tuple[tuple[str, str], ...] = ()
    missing_slots: tuple[str, ...] = ()
    completed_actions: tuple[str, ...] = ()
    age_turns: int = 0

    MAX_AGE_TURNS = 4
    MAX_MISSING_SLOTS = 8
    MAX_COMPLETED_ACTIONS = 12

    @property
    def is_active(self) -> bool:
        return self.intent is not None and self.status in {
            CustomerTaskStatus.IN_PROGRESS,
            CustomerTaskStatus.WAITING_FOR_USER,
        }

    def to_dict(self) -> dict[str, Any]:
        return {
            "task_id": self.task_id[:64],
            "intent": self.intent.value if self.intent is not None else None,
            "status": self.status.value,
            "stage": self.stage[:64],
            "slots": {key[:64]: value[:200] for key, value in self.slots[-8:]},
            "missing_slots": list(self.missing_slots[-self.MAX_MISSING_SLOTS :]),
            "completed_actions": list(self.completed_actions[-self.MAX_COMPLETED_ACTIONS :]),
            "age_turns": self.age_turns,
        }

    @classmethod
    def from_dict(cls, value: Any) -> ActiveTaskState:
        if not isinstance(value, dict):
            return cls()
        try:
            intent = (
                CustomerTaskIntent(value["intent"])
                if value.get("intent") is not None
                else None
            )
            status = CustomerTaskStatus(value.get("status", CustomerTaskStatus.COMPLETED))
        except (TypeError, ValueError):
            return cls()

        task_id = value.get("task_id")
        stage = value.get("stage", "IDLE")
        age_turns = value.get("age_turns", 0)
        missing_slots = value.get("missing_slots", [])
        completed_actions = value.get("completed_actions", [])
        slots = value.get("slots", {})
        if not isinstance(task_id, str) or not task_id or len(task_id) > 64:
            task_id = str(uuid4())
        if not isinstance(stage, str):
            stage = "IDLE"
        if not isinstance(age_turns, int) or age_turns < 0:
            age_turns = 0
        safe_missing = tuple(
            item[:64]
            for item in missing_slots[-cls.MAX_MISSING_SLOTS :]
            if isinstance(item, str)
        ) if isinstance(missing_slots, list) else ()
        safe_actions = tuple(
            item[:64]
            for item in completed_actions[-cls.MAX_COMPLETED_ACTIONS :]
            if isinstance(item, str)
        ) if isinstance(completed_actions, list) else ()
        safe_slots = tuple(
            (key[:64], item[:200])
            for key, item in list(slots.items())[-8:]
            if isinstance(key, str) and isinstance(item, str)
        ) if isinstance(slots, dict) else ()
        task = cls(
            task_id=task_id,
            intent=intent,
            status=status,
            stage=stage[:64],
            slots=safe_slots,
            missing_slots=safe_missing,
            completed_actions=safe_actions,
            age_turns=age_turns,
        )
        if task.age_turns >= cls.MAX_AGE_TURNS:
            return cls()
        return task

    def as_context_text(self) -> str:
        if not self.is_active:
            return ""
        return (
            f"当前待处理任务：{self.intent.value}; 状态：{self.status.value}; "
            f"阶段：{self.stage}; 已确认槽位：{dict(self.slots)}; "
            f"待补信息：{', '.join(self.missing_slots) or '无'}。"
            "仅在用户继续该任务时沿用；业务事实必须重新查询。"
        )

    def next_turn(self) -> ActiveTaskState:
        if not self.is_active:
            return self
        age = self.age_turns + 1
        if age >= self.MAX_AGE_TURNS:
            return ActiveTaskState()
        return ActiveTaskState(
            task_id=self.task_id,
            intent=self.intent,
            status=self.status,
            stage=self.stage,
            slots=self.slots,
            missing_slots=self.missing_slots,
            completed_actions=self.completed_actions,
            age_turns=age,
        )

    def transition(
        self,
        *,
        intent: CustomerTaskIntent,
        action: str,
        status: CustomerTaskStatus,
        stage: str,
        slots: tuple[tuple[str, str], ...] = (),
        missing_slots: tuple[str, ...] = (),
    ) -> ActiveTaskState:
        previous_actions = self.completed_actions if self.intent == intent else ()
        return ActiveTaskState(
            task_id=self.task_id if self.intent == intent and self.is_active else str(uuid4()),
            intent=intent,
            status=status,
            stage=stage[:64],
            slots=(slots if intent != self.intent else (*self.slots, *slots))[-8:],
            missing_slots=tuple(dict.fromkeys(missing_slots))[-self.MAX_MISSING_SLOTS :],
            completed_actions=(*previous_actions, action)[-self.MAX_COMPLETED_ACTIONS :],
            age_turns=0,
        )
