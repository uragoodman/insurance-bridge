"""Tests for idempotent Agno session and trace cleanup operations."""

import unittest
from contextlib import contextmanager
from types import SimpleNamespace
from unittest.mock import patch

from app.services import retention


class FakeConnection:
    def __init__(self, row_counts: list[int] | None = None) -> None:
        self.statements: list[tuple[str, dict[str, object]]] = []
        self.row_counts = iter(row_counts or [])

    def execute(self, statement: object, params: dict[str, object]) -> SimpleNamespace:
        self.statements.append((str(statement), params))
        return SimpleNamespace(rowcount=next(self.row_counts, 0))


class FakeSessionConnection:
    def __init__(self) -> None:
        self.calls = 0
        self.statements: list[tuple[str, dict[str, object]]] = []

    def execute(self, statement: object, params: dict[str, object]) -> SimpleNamespace:
        self.statements.append((str(statement), params))
        self.calls += 1
        rows = [("legacy-session-1",)] if self.calls == 1 else []
        return SimpleNamespace(fetchall=lambda: rows)


class FakeEngine:
    def __init__(self, connection: FakeConnection) -> None:
        self.connection = connection

    @contextmanager
    def begin(self):
        yield self.connection

    @contextmanager
    def connect(self):
        yield self.connection


class RetentionTests(unittest.TestCase):
    def test_session_cleanup_deletes_agno_history_and_linked_trace_rows(self) -> None:
        connection = FakeConnection()
        db = SimpleNamespace(
            db_schema="ai",
            trace_table_name="agno_traces",
            span_table_name="agno_spans",
            db_engine=FakeEngine(connection),
            delete_session=lambda **kwargs: self.assertEqual(
                kwargs, {"session_id": "session-1"}
            ),
        )

        with patch.object(retention, "_get_db", return_value=db):
            retention.delete_session_and_traces("session-1")

        self.assertEqual(len(connection.statements), 2)
        self.assertIn('DELETE FROM "ai"."agno_spans"', connection.statements[0][0])
        self.assertIn('DELETE FROM "ai"."agno_traces"', connection.statements[1][0])
        self.assertTrue(all(args[1]["session_id"] == "session-1" for args in connection.statements))

    def test_expired_trace_cleanup_uses_bounded_batches(self) -> None:
        connection = FakeConnection([4, 2, 2, 1])
        db = SimpleNamespace(
            db_schema="ai",
            trace_table_name="agno_traces",
            span_table_name="agno_spans",
            db_engine=FakeEngine(connection),
        )

        with patch.object(retention, "_get_db", return_value=db):
            deleted = retention.delete_expired_traces(30, batch_size=2)

        self.assertEqual(deleted, 9)
        self.assertEqual(len(connection.statements), 4)
        self.assertTrue(all(args[1]["batch_size"] == 2 for args in connection.statements))

    def test_trace_cleanup_rejects_non_positive_retention(self) -> None:
        with self.assertRaises(ValueError):
            retention.delete_expired_traces(0)

    def test_session_retention_removes_legacy_sessions_in_batches(self) -> None:
        connection = FakeSessionConnection()
        deleted_sessions: list[str] = []
        db = SimpleNamespace(
            db_schema="ai",
            session_table_name="agno_sessions",
            db_engine=FakeEngine(connection),
            delete_session=lambda **kwargs: deleted_sessions.append(kwargs["session_id"]) or True,
        )

        with patch.object(retention, "_get_db", return_value=db):
            deleted = retention.delete_expired_sessions(90, batch_size=10)

        self.assertEqual(deleted, 1)
        self.assertEqual(deleted_sessions, ["legacy-session-1"])
        self.assertIn('"ai"."agno_sessions"', connection.statements[0][0])
        self.assertGreater(connection.statements[0][1]["cutoff_ms"], 0)

    def test_unexpected_sql_identifiers_are_rejected(self) -> None:
        with self.assertRaises(RuntimeError):
            retention._quoted_identifier('ai"; DROP TABLE users;--')


if __name__ == "__main__":
    unittest.main()
