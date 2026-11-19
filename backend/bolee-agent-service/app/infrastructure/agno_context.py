"""Agno-backed conversation state and isolated summary-model adapters."""

from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

from app.domain.conversation_context import (
    ConversationMemorySnapshot,
    ConversationTurn,
)


class AgnoSessionContextStore:
    """Persist the compact projection in the existing owner-scoped Agno session."""

    def __init__(
        self, agent_provider: Callable[[], Any], summary_max_chars: int = 4000
    ) -> None:
        self._agent_provider = agent_provider
        self._summary_max_chars = summary_max_chars

    def load(self, user_id: str, session_id: str) -> ConversationMemorySnapshot:
        agent = self._agent_provider()
        session = agent.get_session(session_id=session_id, user_id=user_id)
        if session is None:
            # Reject a session ID that already belongs to a different user before Agent.run.
            existing = agent.get_session(session_id=session_id)
            if existing is None:
                return ConversationMemorySnapshot()
            if getattr(existing, "user_id", None) != user_id:
                raise PermissionError("session is not owned by the requested user")
            session = existing
        session_data = getattr(session, "session_data", None) or {}
        state = session_data.get("session_state", {}) if isinstance(session_data, dict) else {}
        return ConversationMemorySnapshot.from_session_state(state, self._summary_max_chars)

    def save(
        self, user_id: str, session_id: str, memory: ConversationMemorySnapshot
    ) -> None:
        agent = self._agent_provider()
        session = agent.get_session(session_id=session_id, user_id=user_id)
        if session is None or getattr(session, "user_id", None) != user_id:
            raise PermissionError("session is not owned by the requested user")
        agent.update_session_state(
            memory.to_session_state(),
            session_id=session_id,
        )


class AgnoSummaryGenerator:
    """Use a tool-free Agno Agent for semantic compaction of screened turns."""

    def __init__(self, model: Any) -> None:
        from agno.agent import Agent

        self._agent = Agent(
            id="conversation-context-summarizer",
            name="Conversation Context Summarizer",
            model=model,
            tools=[],
            add_history_to_context=False,
            add_session_state_to_context=False,
            markdown=False,
            instructions=[
                "你负责压缩客服对话记忆，只能概括输入材料中明确出现的信息。",
                "保留用户目标、已明确确认的条件、未解决问题和后续必要上下文。",
                "不得推断事实、补充保险业务结论、复述敏感信息或调用工具。",
                "只返回 JSON 字符串数组；每项必须是已有摘要或某条用户消息的连续原文摘录。",
                "摘录必须覆盖完整句子或完整短句，保留否定词、条件和先后顺序，不得截取句子中间的片段。",
                "不要使用助手回答作为事实来源，不要输出 JSON 以外的文字。",
            ],
        )

    def generate(
        self,
        existing_summary: str,
        turns: tuple[ConversationTurn, ...],
        max_chars: int,
    ) -> str:
        user_messages = [turn.user for turn in turns]
        prompt = (
            "从以下已有摘要和用户原话中挑选后续对话所需的关键片段。"
            "只能逐字摘录连续原文，不得改写、推断、合并事实或添加标题；"
            "不得使用助手回答中的事实。输出必须是 JSON 字符串数组，"
            f"总长度不超过 {max_chars} 个字符。\n"
            + json.dumps(
                {"existing_summary": existing_summary, "user_messages": user_messages},
                ensure_ascii=False,
            )
        )
        result = self._agent.run(
            prompt,
            stream=False,
            add_history_to_context=False,
            add_session_state_to_context=False,
        )
        content = getattr(result, "content", "")
        if not isinstance(content, str):
            raise ValueError("summary model returned a non-text result")
        content = content.strip()
        if content.startswith("```"):
            content_lines = content.splitlines()
            if content_lines and content_lines[0].startswith("```"):
                content_lines = content_lines[1:]
            if content_lines and content_lines[-1].strip() == "```":
                content_lines = content_lines[:-1]
            content = "\n".join(content_lines).strip()
        excerpts = json.loads(content)
        sources = [existing_summary, *user_messages]
        if not isinstance(excerpts, list) or any(not isinstance(item, str) for item in excerpts):
            raise ValueError("summary model did not return a JSON string array")

        selected: list[str] = []
        for excerpt in excerpts:
            excerpt = excerpt.strip()
            if not excerpt:
                continue
            if not any(excerpt in source for source in sources):
                raise ValueError("summary model returned text not present in allowed sources")
            projected_length = sum(map(len, selected)) + len(selected) + len(excerpt)
            if projected_length > max_chars:
                break
            if excerpt not in selected:
                selected.append(excerpt)
        return "\n".join(selected)
