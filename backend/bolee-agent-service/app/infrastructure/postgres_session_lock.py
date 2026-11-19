"""PostgreSQL advisory lock for cross-worker serialization of session turns."""

from __future__ import annotations

from contextlib import contextmanager
from threading import Lock
from time import monotonic, sleep
from typing import Iterator

from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine
from sqlalchemy.pool import NullPool

from app.domain.conversation_context import SessionTurnInProgressError
from db import get_postgres_db


class PostgresSessionTurnLock:
    """Hold a database session-level advisory lock for a complete chat turn."""

    def __init__(self, timeout_seconds: float = 30) -> None:
        if timeout_seconds <= 0:
            raise ValueError("timeout_seconds must be positive")
        self._timeout_seconds = timeout_seconds
        self._engine: Engine | None = None
        self._engine_lock = Lock()

    def _get_lock_engine(self) -> Engine:
        if self._engine is None:
            with self._engine_lock:
                if self._engine is None:
                    db = get_postgres_db()
                    self._engine = create_engine(db.db_engine.url, poolclass=NullPool)
        return self._engine

    @contextmanager
    def acquire(self, user_id: str, session_id: str) -> Iterator[None]:
        connection = self._get_lock_engine().connect()
        # Keying only by session ID also serializes owner-collision checks.
        lock_key = f"bolee-agent-session:{session_id}"
        acquired = False
        deadline = monotonic() + self._timeout_seconds
        try:
            while not acquired:
                acquired = bool(
                    connection.execute(
                        text("SELECT pg_try_advisory_lock(hashtextextended(:lock_key, 0))"),
                        {"lock_key": lock_key},
                    ).scalar_one()
                )
                connection.commit()
                if not acquired:
                    remaining = deadline - monotonic()
                    if remaining <= 0:
                        raise SessionTurnInProgressError(
                            "当前会话有请求正在处理，请稍后重试。"
                        )
                    sleep(min(0.1, remaining))
            yield
        finally:
            try:
                if acquired:
                    connection.execute(
                        text("SELECT pg_advisory_unlock(hashtextextended(:lock_key, 0))"),
                        {"lock_key": lock_key},
                    )
                    connection.commit()
            finally:
                connection.close()
