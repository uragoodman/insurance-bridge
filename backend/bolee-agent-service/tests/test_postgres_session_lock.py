"""Tests for cross-worker advisory-lock acquisition and timeout behavior."""

import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.domain.conversation_context import SessionTurnInProgressError
from app.infrastructure.postgres_session_lock import PostgresSessionTurnLock


class FakeResult:
    def __init__(self, value) -> None:
        self.value = value

    def scalar_one(self):
        return self.value


class FakeConnection:
    def __init__(self, lock_results) -> None:
        self.lock_results = iter(lock_results)
        self.statements: list[str] = []
        self.commits = 0
        self.closed = False

    def execute(self, statement, _params):
        sql = str(statement)
        self.statements.append(sql)
        if "pg_try_advisory_lock" in sql:
            return FakeResult(next(self.lock_results))
        return FakeResult(None)

    def commit(self):
        self.commits += 1

    def close(self):
        self.closed = True


class FakeEngine:
    def __init__(self, connection) -> None:
        self.connection = connection

    def connect(self):
        return self.connection


class PostgresSessionLockTests(unittest.TestCase):
    def make_lock(self, connection, timeout=1):
        engine = FakeEngine(connection)
        db = SimpleNamespace(db_engine=SimpleNamespace(url="postgresql://test"))
        patches = (
            patch("app.infrastructure.postgres_session_lock.get_postgres_db", return_value=db),
            patch("app.infrastructure.postgres_session_lock.create_engine", return_value=engine),
        )
        return PostgresSessionTurnLock(timeout), patches

    def test_lock_retries_until_acquired_and_releases_after_turn(self) -> None:
        connection = FakeConnection([False, True])
        lock, patches = self.make_lock(connection)
        with patches[0], patches[1], patch(
            "app.infrastructure.postgres_session_lock.sleep"
        ) as sleep:
            with lock.acquire("user-1", "session-1"):
                self.assertFalse(connection.closed)

        self.assertEqual(sleep.call_count, 1)
        self.assertTrue(any("pg_advisory_unlock" in sql for sql in connection.statements))
        self.assertTrue(connection.closed)

    def test_lock_timeout_returns_busy_and_closes_connection(self) -> None:
        connection = FakeConnection([False, False])
        lock, patches = self.make_lock(connection, timeout=0.1)
        with patches[0], patches[1], patch(
            "app.infrastructure.postgres_session_lock.monotonic",
            side_effect=[0.0, 0.0, 0.2],
        ), patch("app.infrastructure.postgres_session_lock.sleep"):
            with self.assertRaises(SessionTurnInProgressError):
                with lock.acquire("user-1", "session-1"):
                    self.fail("lock should time out before yielding")

        self.assertTrue(connection.closed)
        self.assertFalse(any("pg_advisory_unlock" in sql for sql in connection.statements))


if __name__ == "__main__":
    unittest.main()
