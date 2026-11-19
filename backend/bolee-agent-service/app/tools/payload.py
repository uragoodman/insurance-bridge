"""Bound Java tool payloads before they enter the model's working context."""

import json
from typing import Any

from app.config import settings
from app.domain.conversation_context import estimate_text_tokens


def bounded_tool_payload(payload: Any, result_collector: Any | None = None) -> Any:
    """Return JSON-safe tool data only when it fits the configured token cap.

    Oversized results are not truncated because truncating structured business
    facts could change their meaning. The caller receives a small actionable
    status object instead.
    """
    serialized = json.dumps(payload, ensure_ascii=False, separators=(",", ":"))
    max_tokens = settings.tool_result_max_tokens
    if max_tokens <= 0:
        raise ValueError("AGNO_TOOL_RESULT_MAX_TOKENS must be positive")
    estimated_tokens = estimate_text_tokens(serialized)
    if estimated_tokens > max_tokens:
        if result_collector is not None:
            result_collector.reserve_context_result_tokens(64)
        return {
            "status": "RESULT_TOO_LARGE",
            "message": "Java 返回结果过长，未发送给模型。请缩小产品范围或查询条件后重试。",
        }
    if result_collector is not None and not result_collector.reserve_context_result_tokens(
        estimated_tokens
    ):
        return {
            "status": "TURN_RESULT_BUDGET_EXHAUSTED",
            "message": "本轮工具结果预算已用完，未发送更多结果。请缩小查询范围或分轮查询。",
        }
    return json.loads(serialized)
