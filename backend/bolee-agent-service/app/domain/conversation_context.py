"""Typed, bounded conversation memory and context-budget decisions."""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from typing import Any

CONVERSATION_CONTEXT_STATE_KEY = "bolee_conversation_context"
_CJK = re.compile(r"[\u3400-\u9fff]")
_CHUNK = re.compile(r"[\u3400-\u9fff]|[^\s\u3400-\u9fff]+")


def estimate_text_tokens(value: str) -> int:
    """Provider-independent conservative estimate used for soft budgeting."""
    count = 0
    for chunk in _CHUNK.findall(value):
        if _CJK.fullmatch(chunk):
            count += 2
        else:
            count += max(1, math.ceil(len(chunk) / 3))
    return count


class SessionTurnInProgressError(TimeoutError):
    """Raised when another worker still owns the session turn lock."""


@dataclass(frozen=True, slots=True)
class ConversationTurn:
    """One safe user/assistant exchange retained for short-term continuity."""

    user: str
    assistant: str

    def as_dict(self) -> dict[str, str]:
        return {"user": self.user, "assistant": self.assistant}


@dataclass(frozen=True, slots=True)
class ConversationMemorySnapshot:
    """Versioned memory projection; deliberately excludes raw tool payloads."""

    summary: str = ""
    recent_turns: tuple[ConversationTurn, ...] = field(default_factory=tuple)
    conversation_goal: str = ""
    current_topic: str = ""
    explicit_confirmations: tuple[str, ...] = field(default_factory=tuple)
    pending_questions: tuple[str, ...] = field(default_factory=tuple)
    business_references: tuple[str, ...] = field(default_factory=tuple)
    business_reference_age: int = 0
    revision: int = 0
    schema_version: int = 2

    def to_state(self) -> dict[str, Any]:
        return {
            "schema_version": self.schema_version,
            "revision": self.revision,
            "summary": self.summary,
            "recent_turns": [turn.as_dict() for turn in self.recent_turns],
            "conversation_goal": self.conversation_goal,
            "current_topic": self.current_topic,
            "explicit_confirmations": list(self.explicit_confirmations),
            "pending_questions": list(self.pending_questions),
            "business_references": list(self.business_references),
            "business_reference_age": self.business_reference_age,
        }

    def to_session_state(self) -> dict[str, Any]:
        return {CONVERSATION_CONTEXT_STATE_KEY: self.to_state()}

    @classmethod
    def from_state(cls, value: Any, summary_max_chars: int) -> ConversationMemorySnapshot:
        if not isinstance(value, dict) or value.get("schema_version") not in {1, 2}:
            return cls()
        schema_version = value.get("schema_version")
        summary = value.get("summary", "")
        if not isinstance(summary, str):
            summary = ""
        turns: list[ConversationTurn] = []
        raw_turns = value.get("recent_turns", [])
        if isinstance(raw_turns, list):
            for item in raw_turns:
                if not isinstance(item, dict):
                    continue
                user = item.get("user")
                assistant = item.get("assistant")
                if isinstance(user, str) and isinstance(assistant, str):
                    turns.append(ConversationTurn(user=user[:4000], assistant=assistant[:12000]))
        revision = value.get("revision", 0)
        if not isinstance(revision, int) or revision < 0:
            revision = 0
        business_reference_age = value.get("business_reference_age", 0)
        if not isinstance(business_reference_age, int) or business_reference_age < 0:
            business_reference_age = 0
        current_topic = value.get("current_topic", "")
        if not isinstance(current_topic, str):
            current_topic = ""
        conversation_goal = value.get("conversation_goal", "")
        if not isinstance(conversation_goal, str):
            conversation_goal = ""

        def bounded_strings(field_name: str, *, limit: int, max_chars: int) -> tuple[str, ...]:
            raw_values = value.get(field_name, [])
            if not isinstance(raw_values, list):
                return ()
            return tuple(
                item[:max_chars] for item in raw_values[-limit:] if isinstance(item, str)
            )

        return cls(
            summary=summary[:summary_max_chars],
            recent_turns=tuple(turns[-32:]),
            # Version 1 treated the first safe user message as a goal. Clear
            # that inferred value during migration; v2 stores only explicit goals.
            conversation_goal=conversation_goal[:300] if schema_version >= 2 else "",
            current_topic=current_topic[:300],
            explicit_confirmations=bounded_strings(
                "explicit_confirmations", limit=8, max_chars=300
            ),
            pending_questions=(
                bounded_strings("pending_questions", limit=4, max_chars=300)
                if schema_version >= 2
                else ()
            ),
            business_references=bounded_strings("business_references", limit=10, max_chars=160),
            business_reference_age=business_reference_age,
            revision=revision,
            schema_version=2,
        )

    @classmethod
    def from_session_state(
        cls, value: Any, summary_max_chars: int
    ) -> ConversationMemorySnapshot:
        if not isinstance(value, dict):
            return cls()
        return cls.from_state(value.get(CONVERSATION_CONTEXT_STATE_KEY), summary_max_chars)

    def as_context_text(self) -> str:
        sections: list[str] = []
        if self.summary:
            sections.append(f"较早对话摘要：\n{self.summary}")
        if self.conversation_goal:
            sections.append(f"当前会话目标：{self.conversation_goal}")
        if self.recent_turns:
            recent = "\n".join(
                f"用户：{turn.user}\n助手：{turn.assistant}" for turn in self.recent_turns
            )
            sections.append(f"近期对话：\n{recent}")
        if self.current_topic:
            sections.append(f"当前话题：{self.current_topic}")
        if self.explicit_confirmations:
            sections.append("用户明确确认过的内容：\n" + "\n".join(self.explicit_confirmations))
        if self.pending_questions:
            sections.append("待澄清问题：\n" + "\n".join(self.pending_questions))
        if self.business_references:
            sections.append(
                "业务对象引用（仅用于重新查询，不代表当前业务事实）：\n"
                + "\n".join(self.business_references)
            )
        return "\n\n".join(sections)


@dataclass(frozen=True, slots=True)
class ContextBudget:
    context_window_tokens: int
    fixed_prompt_tokens: int
    output_reserve_tokens: int
    safety_margin_tokens: int
    soft_limit_ratio: float
    tool_result_reserve_tokens: int = 0

    def __post_init__(self) -> None:
        token_values = (
            self.context_window_tokens,
            self.fixed_prompt_tokens,
            self.output_reserve_tokens,
            self.safety_margin_tokens,
            self.tool_result_reserve_tokens,
        )
        if any(value < 0 for value in token_values):
            raise ValueError("context budget token values must not be negative")
        if self.context_window_tokens <= 0:
            raise ValueError("context_window_tokens must be positive")
        if not 0 < self.soft_limit_ratio < 1:
            raise ValueError("soft_limit_ratio must be between 0 and 1")

    @property
    def input_limit_tokens(self) -> int:
        return max(
            0,
            self.context_window_tokens
            - self.output_reserve_tokens
            - self.safety_margin_tokens
            - self.tool_result_reserve_tokens,
        )

    @property
    def soft_limit_tokens(self) -> int:
        return math.floor(self.input_limit_tokens * self.soft_limit_ratio)


@dataclass(frozen=True, slots=True)
class ContextPlan:
    memory: ConversationMemorySnapshot
    estimated_input_tokens: int
    estimated_input_limit_tokens: int
    needs_compaction: bool
    exceeds_estimated_limit: bool

    @property
    def exceeds_hard_limit(self) -> bool:
        """Compatibility alias; this policy only estimates the final model input."""
        return self.exceeds_estimated_limit


class ContextBudgetPolicy:
    """Pure policy: estimate the bounded memory projection against model limits."""

    def __init__(self, budget: ContextBudget, recent_turn_limit: int = 6) -> None:
        if recent_turn_limit < 1:
            raise ValueError("recent_turn_limit must be positive")
        self._budget = budget
        self._recent_turn_limit = recent_turn_limit

    def estimate_tokens(self, value: str) -> int:
        """Conservative provider-independent estimate; CJK characters count as two tokens."""
        return estimate_text_tokens(value)

    def plan(
        self,
        memory: ConversationMemorySnapshot,
        current_message: str,
    ) -> ContextPlan:
        estimated = self._budget.fixed_prompt_tokens + self.estimate_tokens(
            memory.as_context_text() + "\n" + current_message
        )
        return ContextPlan(
            memory=memory,
            estimated_input_tokens=estimated,
            estimated_input_limit_tokens=self._budget.input_limit_tokens,
            needs_compaction=(
                estimated > self._budget.soft_limit_tokens
                or len(memory.recent_turns) > self._recent_turn_limit
            ),
            exceeds_estimated_limit=estimated > self._budget.input_limit_tokens,
        )

    @property
    def input_limit_tokens(self) -> int:
        return self._budget.input_limit_tokens

    @property
    def fixed_prompt_tokens(self) -> int:
        return self._budget.fixed_prompt_tokens

    @property
    def soft_limit_tokens(self) -> int:
        return self._budget.soft_limit_tokens
