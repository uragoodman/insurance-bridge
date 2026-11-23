"""Opt-in smoke test that calls the configured LLM with synthetic conversations.

Run from the service directory with AGNO_MODEL_API_KEY configured:
    python tests/real_llm_context_smoke.py

This makes billable model requests. It uses no tools, real customer data, or shared database.
"""

from __future__ import annotations

import json
from collections import defaultdict
from contextlib import contextmanager
from types import SimpleNamespace

from agno.agent import Agent
from agno.models.openai.like import OpenAILike

from app.config import settings
from app.domain.conversation_context import (
    ContextBudget,
    ContextBudgetPolicy,
    ConversationMemorySnapshot,
)
from app.infrastructure.agno_agent_runner import AgnoAgentRunner
from app.infrastructure.agno_context import AgnoSummaryGenerator
from app.services.chat_application import ContextAwareChatApplicationService
from app.services.context_management import ContextManagementService


class InMemoryContextStore:
    def __init__(self) -> None:
        self.sessions: dict[tuple[str, str], ConversationMemorySnapshot] = {}

    def load(self, user_id: str, session_id: str) -> ConversationMemorySnapshot:
        return self.sessions.get((user_id, session_id), ConversationMemorySnapshot())

    def save(
        self, user_id: str, session_id: str, memory: ConversationMemorySnapshot
    ) -> None:
        self.sessions[(user_id, session_id)] = memory


class CountingSummaryGenerator:
    def __init__(self, delegate: AgnoSummaryGenerator) -> None:
        self._delegate = delegate
        self.calls = 0

    def generate(self, existing_summary, turns, max_chars) -> str:
        self.calls += 1
        return self._delegate.generate(existing_summary, turns, max_chars)


class RecordingAgentRunner:
    def __init__(self, delegate: AgnoAgentRunner) -> None:
        self._delegate = delegate
        self.calls = 0
        self.recent_turn_counts: dict[str, list[int]] = defaultdict(list)

    def run(self, message, *, user_id, session_id, dependencies, session_state, stream,
            yield_run_output=False):
        self.calls += 1
        memory = session_state.get("bolee_conversation_context", {})
        self.recent_turn_counts[session_id].append(len(memory.get("recent_turns", [])))
        return self._delegate.run(
            message,
            user_id=user_id,
            session_id=session_id,
            dependencies=dependencies,
            session_state=session_state,
            stream=stream,
            yield_run_output=yield_run_output,
        )

    def is_final_output(self, item):
        return self._delegate.is_final_output(item)


class NoOpSessionLock:
    @contextmanager
    def acquire(self, _user_id, _session_id):
        yield


def create_model(model_id: str, max_tokens: int) -> OpenAILike:
    return OpenAILike(
        id=model_id,
        api_key=settings.model_api_key,
        base_url=settings.model_base_url or None,
        max_tokens=max_tokens,
        temperature=0,
    )


def main() -> None:
    if not settings.model_api_key:
        raise RuntimeError("AGNO_MODEL_API_KEY is not configured")

    agent = Agent(
        id="context-real-llm-simulation",
        name="Context Simulation Assistant",
        model=create_model(settings.model_id, 384),
        tools=[],
        db=None,
        add_history_to_context=False,
        add_session_state_to_context=True,
        markdown=False,
        instructions=[
            "你正在参与合成的保险产品目录演示规划，不处理真实客户或真实保单。",
            "以简体中文简短回答，最多 80 个汉字；优先依据历史摘要与近期对话。",
            "历史内容仅是参考，不得声称它是实时业务数据。",
        ],
    )
    store = InMemoryContextStore()
    summarizer = CountingSummaryGenerator(
        AgnoSummaryGenerator(
            create_model(settings.context_summary_model_id or settings.model_id, 384)
        )
    )
    policy = ContextBudgetPolicy(
        ContextBudget(
            context_window_tokens=1400,
            fixed_prompt_tokens=300,
            output_reserve_tokens=300,
            safety_margin_tokens=200,
            soft_limit_ratio=0.70,
        ),
        recent_turn_limit=6,
    )
    context_management = ContextManagementService(
        store,
        summarizer,
        policy,
        summary_max_chars=1200,
        recent_turn_limit=6,
        keep_recent_turns=2,
    )
    runner = RecordingAgentRunner(AgnoAgentRunner(lambda: agent))
    chat = ContextAwareChatApplicationService(runner, context_management, NoOpSessionLock())
    conversations = {
        "llm-session-alpha": [
            "演示主题A：设计保险产品目录演示。目标是说明分类、筛选条件和结果展示，不涉及真实客户。",
            "主题A补充：先按产品类别过滤，再按保障期限筛选；请记住这个顺序。",
            "主题A补充：结果页展示产品名称、版本和数据来源，字段缺失时标明未知。",
            "主题A补充：产品比较基于同一目录快照；版本变化时重新查询再比较。",
            "请根据主题A前面的讨论，简短总结演示方案以及仍需确认的点。",
        ],
        "llm-session-beta": [
            "独立主题B：规划知识库问答演示，重点是引用来源和回答边界。",
            "主题B补充：展示文档标题、章节和更新时间；答案必须能回到来源。",
            "主题B补充：没有检索依据时明确说无法确认，不要补造答案。",
            "请根据主题B前面的讨论，简短总结流程和未解决事项。",
        ],
    }

    for session_id, messages in conversations.items():
        for message in messages:
            result = chat.run(
                message,
                user_id="synthetic-user",
                session_id=session_id,
                dependencies={},
                tool_result_collector=SimpleNamespace(
                    sensitive_tool_used=False,
                    context_references=(),
                ),
            )
            if not getattr(result, "content", None):
                raise RuntimeError(f"LLM returned empty output for {session_id}")

    report = {
        "real_llm_chat_calls": runner.calls,
        "real_llm_summary_calls": summarizer.calls,
        "sessions": {},
    }
    for session_id, messages in conversations.items():
        memory = store.load("synthetic-user", session_id)
        if memory.conversation_goal != messages[0][:300]:
            raise AssertionError(f"explicit conversation goal was not retained for {session_id}")
        if memory.summary:
            for excerpt in memory.summary.splitlines():
                if excerpt and not any(excerpt in message for message in messages):
                    raise AssertionError("stored summary contains non-extractive content")
        report["sessions"][session_id] = {
            "turns": len(messages),
            "summary_chars": len(memory.summary),
            "recent_turns_saved": len(memory.recent_turns),
            "conversation_goal": memory.conversation_goal,
            "recent_turns_injected_per_call": runner.recent_turn_counts[session_id],
            "summary": memory.summary,
        }
    if summarizer.calls == 0:
        raise AssertionError("long conversation did not invoke the real LLM summarizer")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
