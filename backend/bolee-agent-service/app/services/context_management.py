"""Application policy for preparing and updating bounded Agent context."""

from __future__ import annotations

import logging
import re
from dataclasses import replace

from app.domain.conversation_context import (
    ContextBudgetPolicy,
    ContextPlan,
    ConversationMemorySnapshot,
    ConversationTurn,
)
from app.domain.customer_task import ActiveTaskState
from app.services.ports import (
    SessionContextStorePort,
    SummaryGeneratorPort,
)

logger = logging.getLogger(__name__)
_SENSITIVE_TEXT = re.compile(
    r"(?:保单|投保人|被保险人|理赔|病历|病史|诊断|疾病|健康|用药|手术|孕产"
    r"|身份证|银行卡|缴费账号|手机号|手机号码|邮箱|电子邮箱|住址|家庭地址)"
    r"|(?<!\d)1[3-9]\d{9}(?!\d)|(?<!\d)\d{17}[\dXx](?!\d)"
    r"|(?:[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})",
    re.IGNORECASE,
)
_EXPLICIT_CONFIRMATION = re.compile(r"(?:确认|是的|对的|没错|正确|按这个|就按|我同意)")
_CLARIFICATION = re.compile(r"[？?]|(?:请问|请提供|需要您|方便告诉)")
_GOAL_STATEMENT = re.compile(
    r"(?:我想(?:要|了解|咨询|查询|比较|规划)?|我要|我需要|请帮我|帮我|"
    r"(?:我的)?目标是|计划(?:是|要)|(?:^|[:：]\s*)(?:规划|设计|制定|策划))"
)
_TOPIC_CHANGE = re.compile(r"(?:换个(?:话题|问题)|另一个问题|改聊|先不聊|重新开始|现在改为)")


class ContextInputTooLargeError(ValueError):
    """Raised when the current request cannot fit the configured token estimate."""


class ContextCompactionUnavailableError(RuntimeError):
    """Raised when memory cannot be safely compacted without dropping source turns."""


class ContextManagementService:
    """Coordinate deterministic filtering/budgeting with LLM-generated summaries."""

    def __init__(
        self,
        store: SessionContextStorePort,
        summary_generator: SummaryGeneratorPort,
        budget_policy: ContextBudgetPolicy,
        *,
        summary_max_chars: int = 4000,
        recent_turn_limit: int = 6,
        keep_recent_turns: int = 2,
    ) -> None:
        if summary_max_chars <= 0:
            raise ValueError("summary_max_chars must be positive")
        if recent_turn_limit < 1 or keep_recent_turns < 0:
            raise ValueError("invalid recent turn limits")
        self._store = store
        self._summary_generator = summary_generator
        self._budget_policy = budget_policy
        self._summary_max_chars = summary_max_chars
        self._recent_turn_limit = recent_turn_limit
        self._keep_recent_turns = min(keep_recent_turns, recent_turn_limit)

    def prepare(self, user_id: str, session_id: str, current_message: str) -> ContextPlan:
        memory = self._store.load(user_id, session_id)
        current_message_tokens = self._budget_policy.estimate_tokens(current_message)
        current_only = self._budget_policy.fixed_prompt_tokens + current_message_tokens
        if current_only > self._budget_policy.input_limit_tokens:
            raise ContextInputTooLargeError(
                "当前消息的估算长度超出可用上下文预算，请拆分或缩短后重试。"
            )

        plan = self._budget_policy.plan(memory, current_message)
        should_compact = plan.needs_compaction or len(memory.recent_turns) > self._recent_turn_limit
        if should_compact:
            memory = self._compact(memory, current_message)
            plan = self._budget_policy.plan(memory, current_message)

        if plan.exceeds_estimated_limit:
            memory = self._fit_memory_to_budget(memory, current_message)
            plan = self._budget_policy.plan(memory, current_message)
        if plan.exceeds_estimated_limit:
            raise ContextInputTooLargeError(
                "当前对话上下文的估算长度仍超出可用预算，请缩短本轮问题后重试。"
            )
        return plan

    def record_turn(
        self,
        user_id: str,
        session_id: str,
        previous: ConversationMemorySnapshot,
        user_message: str,
        assistant_message: str,
        *,
        sensitive_tool_used: bool,
        business_references: tuple[str, ...] = (),
        active_task: ActiveTaskState | None = None,
    ) -> ConversationMemorySnapshot:
        """Persist only safe conversational content; business tools stay authoritative."""
        if (
            sensitive_tool_used
            or _SENSITIVE_TEXT.search(user_message)
            or _SENSITIVE_TEXT.search(assistant_message)
        ):
            logger.info("Skipped conversation memory update for sensitive session turn")
            return previous

        turn = ConversationTurn(
            user=user_message[:2000],
            assistant=assistant_message[:4000],
        )
        # A subsequent user turn is treated as the response opportunity for the
        # previous clarification. The recent turns still retain its exact wording.
        pending_questions: tuple[str, ...] = ()
        if _CLARIFICATION.search(assistant_message):
            pending_questions = (*pending_questions, assistant_message[:300])[-4:]
        topic_changed = bool(_TOPIC_CHANGE.search(user_message))
        conversation_goal = previous.conversation_goal
        confirmations = previous.explicit_confirmations
        references = previous.business_references
        reference_age = previous.business_reference_age + 1
        if topic_changed:
            conversation_goal = ""
            confirmations = ()
            references = ()
            reference_age = 0
        if _GOAL_STATEMENT.search(user_message):
            conversation_goal = user_message[:300]
        if _EXPLICIT_CONFIRMATION.search(user_message):
            confirmations = (*confirmations, user_message[:300])[-8:]
        if business_references:
            references = tuple(business_references[-10:])
            reference_age = 0
        elif reference_age >= 3:
            references = ()
            reference_age = 0
        updated = replace(
            previous,
            summary=previous.summary[: self._summary_max_chars],
            recent_turns=(*previous.recent_turns, turn),
            conversation_goal=conversation_goal,
            current_topic=user_message[:300],
            explicit_confirmations=confirmations,
            pending_questions=pending_questions,
            business_references=references,
            business_reference_age=reference_age,
            active_task=(
                active_task
                if active_task is not None and active_task != previous.active_task
                else previous.active_task.next_turn()
            ),
            revision=previous.revision + 1,
        )
        self._store.save(user_id, session_id, updated)
        return updated

    def _compact(
        self, memory: ConversationMemorySnapshot, current_message: str
    ) -> ConversationMemorySnapshot:
        keep_count = self._keep_recent_turns
        current_plan = self._budget_policy.plan(memory, current_message)
        if current_plan.estimated_input_tokens > self._budget_policy.soft_limit_tokens:
            keep_count = min(keep_count, max(0, len(memory.recent_turns) - 1))
        older_turns = memory.recent_turns[:-keep_count] if keep_count else memory.recent_turns
        recent_turns = memory.recent_turns[-keep_count:] if keep_count else ()
        summary = memory.summary

        if older_turns:
            try:
                generated = self._summary_generator.generate(
                    summary, tuple(older_turns), self._summary_max_chars
                ).strip()
                self._validate_summary(
                    generated, summary, tuple(older_turns), self._summary_max_chars
                )
            except Exception as exc:
                if not current_plan.exceeds_estimated_limit:
                    logger.warning(
                        "Summary failed; using unmodified memory within the estimate"
                    )
                    return memory
                logger.warning("Conversation summary generation failed; retaining source turns")
                raise ContextCompactionUnavailableError(
                    "上下文摘要暂时不可用，历史对话已保留，请稍后重试。"
                ) from exc
            if not generated:
                if not current_plan.exceeds_estimated_limit:
                    logger.warning(
                        "Empty summary; using unmodified memory within the estimate"
                    )
                    return memory
                logger.warning("Conversation summary was empty; retaining source turns")
                raise ContextCompactionUnavailableError(
                    "上下文摘要为空，历史对话已保留，请稍后重试。"
                )
            summary = generated

        compacted = replace(
            memory,
            summary=summary,
            recent_turns=tuple(recent_turns),
            revision=memory.revision + 1,
        )
        return self._fit_memory_to_budget(compacted, current_message)

    def _fit_memory_to_budget(
        self, memory: ConversationMemorySnapshot, current_message: str
    ) -> ConversationMemorySnapshot:
        plan = self._budget_policy.plan(memory, current_message)
        if not plan.exceeds_estimated_limit:
            return memory

        # Fold every remaining source turn into a newly validated summary before
        # evicting it. A failed or empty summary must never mutate the snapshot.
        without_verbatim_turns = replace(memory, summary="", recent_turns=())
        fixed_and_current = (
            self._budget_policy.fixed_prompt_tokens
            + self._budget_policy.estimate_tokens(current_message)
            + self._budget_policy.estimate_tokens(without_verbatim_turns.as_context_text())
        )
        available_tokens = self._budget_policy.input_limit_tokens - fixed_and_current
        # CJK text is estimated at two tokens per character, so this cap is
        # conservative for the provider-independent soft budget.
        target_chars = min(self._summary_max_chars, max(0, available_tokens // 2))
        if target_chars <= 0 or not (memory.summary or memory.recent_turns):
            raise ContextInputTooLargeError(
                "当前问题和固定上下文已占满估算预算，请缩短本轮问题后重试。"
            )

        try:
            summary = self._summary_generator.generate(
                memory.summary, memory.recent_turns, target_chars
            ).strip()
            self._validate_summary(
                summary, memory.summary, memory.recent_turns, target_chars
            )
        except Exception as exc:
            logger.warning("Unable to fit memory safely; retaining source turns")
            raise ContextCompactionUnavailableError(
                "上下文暂时无法安全压缩，历史对话已保留，请稍后重试。"
            ) from exc
        if not summary:
            raise ContextCompactionUnavailableError(
                "上下文压缩未生成可用摘要，历史对话已保留，请稍后重试。"
            )

        candidate = replace(
            memory,
            summary=summary,
            recent_turns=(),
            revision=memory.revision + 1,
        )
        if self._budget_policy.plan(candidate, current_message).exceeds_estimated_limit:
            raise ContextInputTooLargeError(
                "压缩后上下文仍超出估算预算，请缩短本轮问题后重试。"
            )
        return candidate

    @staticmethod
    def _validate_summary(
        summary: str,
        existing_summary: str,
        turns: tuple[ConversationTurn, ...],
        max_chars: int,
    ) -> None:
        if not summary or len(summary) > max_chars:
            raise ValueError("summary is empty or exceeds its character limit")
        allowed_sources = (existing_summary, *(turn.user for turn in turns))
        allowed_excerpts: set[str] = set()
        for source in allowed_sources:
            allowed_excerpts.add(source.strip())
            for fragment in re.split(r"(?<=[。！？?!])|[\r\n]+", source):
                fragment = fragment.strip()
                if fragment:
                    allowed_excerpts.add(fragment)
                    allowed_excerpts.add(fragment.rstrip("。！？?!").strip())
        excerpts: list[str] = []
        for line in summary.splitlines():
            line = line.strip()
            if not line:
                continue
            if line in allowed_excerpts:
                excerpts.append(line)
            elif "；" in line:
                excerpts.extend(part.strip() for part in line.split("；") if part.strip())
            else:
                excerpts.append(line)
        if not excerpts or any(excerpt not in allowed_excerpts for excerpt in excerpts):
            raise ValueError("summary contains partial or ungrounded source text")
