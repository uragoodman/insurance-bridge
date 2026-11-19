"""Privacy cleanup for the Agno Postgres session and trace records."""

import re
from datetime import datetime, timedelta, timezone

from sqlalchemy import text

_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


def _get_db():
    from db import get_postgres_db

    return get_postgres_db()


def _quoted_identifier(value: str) -> str:
    if not _IDENTIFIER.fullmatch(value):
        raise RuntimeError("Unexpected Agno database identifier")
    return f'"{value}"'


def delete_session_and_traces(session_id: str) -> None:
    """Delete Agno session/runs and traces for one session, safely repeatable."""
    db = _get_db()
    db.delete_session(session_id=session_id)

    schema = _quoted_identifier(db.db_schema)
    traces = _quoted_identifier(db.trace_table_name)
    spans = _quoted_identifier(db.span_table_name)
    statement = text(
        f"DELETE FROM {schema}.{spans} "
        f"WHERE trace_id IN (SELECT trace_id FROM {schema}.{traces} WHERE session_id = :session_id)"
    )
    delete_traces = text(f"DELETE FROM {schema}.{traces} WHERE session_id = :session_id")
    with db.db_engine.begin() as connection:
        connection.execute(statement, {"session_id": session_id})
        connection.execute(delete_traces, {"session_id": session_id})


def delete_expired_traces(retention_days: int, batch_size: int = 500) -> int:
    """Delete expired trace spans before their parent traces in bounded batches."""
    if retention_days <= 0:
        raise ValueError("retention_days must be positive")
    db = _get_db()
    schema = _quoted_identifier(db.db_schema)
    traces = _quoted_identifier(db.trace_table_name)
    spans = _quoted_identifier(db.span_table_name)
    cutoff = (datetime.now(timezone.utc) - timedelta(days=retention_days)).isoformat()
    deleted = 0
    span_batch = text(
        f"DELETE FROM {schema}.{spans} WHERE trace_id IN ("
        f"SELECT trace_id FROM {schema}.{traces} "
        "WHERE created_at::timestamptz < :cutoff ORDER BY created_at LIMIT :batch_size)"
    )
    trace_batch = text(
        f"DELETE FROM {schema}.{traces} WHERE trace_id IN ("
        f"SELECT trace_id FROM {schema}.{traces} "
        "WHERE created_at::timestamptz < :cutoff ORDER BY created_at LIMIT :batch_size)"
    )
    while True:
        with db.db_engine.begin() as connection:
            params = {"cutoff": cutoff, "batch_size": batch_size}
            spans_result = connection.execute(span_batch, params)
            traces_result = connection.execute(trace_batch, params)
        deleted += (spans_result.rowcount or 0) + (traces_result.rowcount or 0)
        if (traces_result.rowcount or 0) < batch_size:
            return deleted


def delete_expired_sessions(retention_days: int, batch_size: int = 100) -> int:
    """Remove old Agno sessions and their runs, including legacy unindexed sessions."""
    if retention_days <= 0:
        raise ValueError("retention_days must be positive")
    db = _get_db()
    schema = _quoted_identifier(db.db_schema)
    sessions = _quoted_identifier(db.session_table_name)
    cutoff = datetime.now(timezone.utc) - timedelta(days=retention_days)
    cutoff_ms = int(cutoff.timestamp() * 1000)
    select_batch = text(
        f"SELECT session_id FROM {schema}.{sessions} "
        "WHERE COALESCE(updated_at, created_at) < :cutoff_ms "
        "ORDER BY COALESCE(updated_at, created_at) LIMIT :batch_size"
    )
    deleted = 0
    while True:
        with db.db_engine.connect() as connection:
            session_ids = [row[0] for row in connection.execute(
                select_batch, {"cutoff_ms": cutoff_ms, "batch_size": batch_size}
            ).fetchall()]
        if not session_ids:
            return deleted
        for session_id in session_ids:
            if db.delete_session(session_id=session_id):
                deleted += 1
