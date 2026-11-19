"""Owner-checked, paged reads of customer-visible Agno chat history."""

import json
from datetime import datetime, timezone
from typing import Any
from uuid import NAMESPACE_URL, uuid5

from sqlalchemy import func, select

from app.schemas.history import (
    AgentHistoryMessage,
    AgentHistoryPage,
    AgentSessionPreview,
    AgentSessionPreviewsResponse,
    HistoryMessageRole,
)


def get_session_history(
    session_id: str,
    user_id: str,
    before_run_index: int | None,
    limit: int,
) -> AgentHistoryPage:
    """Return complete turns newest-page-first, with messages in chronological order."""
    if limit < 1 or limit > 50:
        raise ValueError("history limit must be between 1 and 50")

    from db import get_postgres_db

    db = get_postgres_db()
    # The caller is Java, but still bind the session to the authenticated user ID that
    # Java resolved. This prevents a bad internal mapping from crossing Agno users.
    session = db.get_session(
        session_id=session_id,
        user_id=user_id,
        deserialize=False,
        runs_limit=1,
    )
    if session is None:
        return AgentHistoryPage()

    runs_table = db._get_table(table_type="runs")
    if runs_table is None:
        return _legacy_history_page(
            db, session_id, user_id, before_run_index, limit
        )

    statement = (
        select(runs_table.c.run_index, runs_table.c.run_data)
        .where(runs_table.c.session_id == session_id)
        .where(runs_table.c.user_id == user_id)
        .where(runs_table.c.parent_run_id.is_(None))
        .where(runs_table.c.status == "COMPLETED")
    )
    if before_run_index is not None:
        statement = statement.where(runs_table.c.run_index < before_run_index)
    statement = statement.order_by(
        runs_table.c.run_index.desc(),
        runs_table.c.created_at.desc(),
        runs_table.c.run_id.desc(),
    ).limit(limit + 1)

    with db.Session() as database_session:
        rows = database_session.execute(statement).fetchall()

    has_more = len(rows) > limit
    page_rows = rows[:limit]
    next_cursor = str(page_rows[-1][0]) if has_more and page_rows else None
    messages: list[AgentHistoryMessage] = []
    for _, run_data in reversed(page_rows):
        messages.extend(_messages_from_run(run_data))
    return AgentHistoryPage(messages=messages, next_cursor=next_cursor, has_more=has_more)


def get_session_previews(session_ids: list[str], user_id: str) -> AgentSessionPreviewsResponse:
    """Return bounded, user-owned titles and latest-message previews for a conversation list."""
    unique_ids = list(dict.fromkeys(session_ids))
    if not unique_ids or len(unique_ids) > 50:
        raise ValueError("session preview request must contain between 1 and 50 IDs")

    from db import get_postgres_db

    db = get_postgres_db()
    sessions_table = db._get_table(table_type="sessions")
    runs_table = db._get_table(table_type="runs")
    if sessions_table is None or runs_table is None:
        return AgentSessionPreviewsResponse()

    owned_sessions = select(sessions_table.c.session_id).where(
        sessions_table.c.session_id.in_(unique_ids),
        sessions_table.c.user_id == user_id,
    )
    with db.Session() as database_session:
        owned_ids = set(database_session.execute(owned_sessions).scalars().all())
        if not owned_ids:
            return AgentSessionPreviewsResponse()

        ranked = select(
            runs_table.c.session_id,
            runs_table.c.run_data,
            func.row_number()
            .over(
                partition_by=runs_table.c.session_id,
                order_by=(
                    runs_table.c.run_index.asc(),
                    runs_table.c.created_at.asc(),
                    runs_table.c.run_id.asc(),
                ),
            )
            .label("first_rank"),
            func.row_number()
            .over(
                partition_by=runs_table.c.session_id,
                order_by=(
                    runs_table.c.run_index.desc(),
                    runs_table.c.created_at.desc(),
                    runs_table.c.run_id.desc(),
                ),
            )
            .label("last_rank"),
        ).where(
            runs_table.c.session_id.in_(owned_ids),
            runs_table.c.user_id == user_id,
            runs_table.c.parent_run_id.is_(None),
            runs_table.c.status == "COMPLETED",
        ).subquery()
        selected = select(
            ranked.c.session_id, ranked.c.run_data, ranked.c.first_rank, ranked.c.last_rank
        ).where((ranked.c.first_rank == 1) | (ranked.c.last_rank == 1))
        rows = database_session.execute(selected).all()

    first_inputs: dict[str, str] = {}
    latest_inputs: dict[str, str] = {}
    for row in rows:
        run = _parse_run(row.run_data)
        if run is None or run.input is None:
            continue
        content = run.input.input_content
        if not isinstance(content, str) or not content.strip():
            continue
        if row.first_rank == 1:
            first_inputs[row.session_id] = content.strip()
        if row.last_rank == 1:
            latest_inputs[row.session_id] = content.strip()

    previews = []
    for session_id in unique_ids:
        if session_id not in owned_ids:
            continue
        previews.append(
            AgentSessionPreview(
                session_id=session_id,
                title=_truncate(first_inputs.get(session_id, "新对话"), 80),
                preview=_truncate(latest_inputs.get(session_id, ""), 180),
            )
        )
    return AgentSessionPreviewsResponse(sessions=previews)


def _messages_from_run(run_data: Any) -> list[AgentHistoryMessage]:
    run = _parse_run(run_data)
    if run is None:
        return []
    run_id = run.run_id
    if not run_id:
        return []

    created_at = datetime.fromtimestamp(run.created_at, tz=timezone.utc)
    result: list[AgentHistoryMessage] = []
    input_content = run.input.input_content if run.input is not None else None
    if isinstance(input_content, str) and input_content.strip():
        result.append(
            AgentHistoryMessage(
                message_id=str(uuid5(NAMESPACE_URL, f"bolee:{run_id}:user")),
                run_id=run_id,
                role=HistoryMessageRole.USER,
                content=input_content[:12000],
                created_at=created_at,
            )
        )
    if isinstance(run.content, str) and run.content.strip():
        result.append(
            AgentHistoryMessage(
                message_id=str(uuid5(NAMESPACE_URL, f"bolee:{run_id}:assistant")),
                run_id=run_id,
                role=HistoryMessageRole.ASSISTANT,
                content=run.content[:12000],
                created_at=created_at,
            )
        )
    return result


def _parse_run(run_data: Any) -> Any | None:
    from agno.run.agent import RunOutput

    try:
        if isinstance(run_data, str):
            run_data = json.loads(run_data)
        return RunOutput.from_dict(run_data if isinstance(run_data, dict) else {})
    except Exception:
        return None


def _truncate(value: str, limit: int) -> str:
    value = " ".join(value.split())
    return value if len(value) <= limit else value[: limit - 1].rstrip() + "…"


def _legacy_history_page(
    db: Any,
    session_id: str,
    user_id: str,
    before_run_index: int | None,
    limit: int,
) -> AgentHistoryPage:
    from agno.run.agent import RunOutput

    session = db.get_session(
        session_id=session_id,
        user_id=user_id,
        deserialize=False,
    )
    if session is None:
        return AgentHistoryPage()
    raw_runs = session.get("runs") or []
    start = len(raw_runs) if before_run_index is None else min(before_run_index, len(raw_runs))
    page_start = max(0, start - limit)
    selected = raw_runs[page_start:start]
    has_more = page_start > 0
    messages: list[AgentHistoryMessage] = []
    for raw_run in selected:
        try:
            run_data = raw_run if isinstance(raw_run, dict) else {}
            run = RunOutput.from_dict(run_data)
            if str(getattr(run.status, "value", run.status)) != "COMPLETED":
                continue
            messages.extend(_messages_from_run(run.to_dict()))
        except Exception:
            continue
    return AgentHistoryPage(
        messages=messages,
        next_cursor=str(page_start) if has_more else None,
        has_more=has_more,
    )
