"""PostgreSQL persistence for term index batches and allowlisted retrieval."""

from datetime import datetime, timedelta, timezone
from typing import Any
from uuid import UUID, uuid4

import psycopg
from pgvector import Vector
from pgvector.psycopg import register_vector
from psycopg.types.json import Jsonb

from app.config import settings


class TermRepositoryError(RuntimeError):
    """Sanitized database error at the terms persistence boundary."""


class PostgresTermRepository:
    def __init__(self, database_url: str):
        if not database_url:
            raise ValueError("AGNO_TERMS_DATABASE_URL is required")
        self._database_url = database_url

    def begin_batch(
        self, *, task_id: int, source_id: int, source_sha256: str, pipeline_fingerprint: str
    ) -> tuple[UUID, bool] | None:
        batch_id = uuid4()
        with self._connect() as conn, conn.cursor() as cursor:
            self._lock_source(cursor, source_id)
            cursor.execute(
                "INSERT INTO insurance_terms.source_index_state "
                "(source_id, state) VALUES (%s, 'NEW') ON CONFLICT (source_id) DO NOTHING",
                (source_id,),
            )
            cursor.execute(
                "SELECT state FROM insurance_terms.source_index_state "
                "WHERE source_id = %s FOR UPDATE",
                (source_id,),
            )
            state = cursor.fetchone()[0]
            if state == "REMOVED":
                return None
            cursor.execute(
                "SELECT batch_id, state FROM insurance_terms.term_index_batches WHERE task_id = %s",
                (task_id,),
            )
            existing = cursor.fetchone()
            if existing and existing[1] == "ACTIVE":
                return existing[0], True
            if existing:
                batch_id = existing[0]
                cursor.execute(
                    "DELETE FROM insurance_terms.term_chunks WHERE batch_id = %s", (batch_id,)
                )
                cursor.execute(
                    "UPDATE insurance_terms.term_index_batches SET source_sha256 = %s, "
                    "pipeline_fingerprint = %s, state = 'STAGING', chunk_count = 0, "
                    "heartbeat_at = now(), updated_at = now(), error_code = NULL "
                    "WHERE batch_id = %s",
                    (source_sha256, pipeline_fingerprint, batch_id),
                )
            else:
                cursor.execute(
                    "INSERT INTO insurance_terms.term_index_batches "
                    "(batch_id, task_id, source_id, source_sha256, pipeline_fingerprint, state) "
                    "VALUES (%s, %s, %s, %s, %s, 'STAGING')",
                    (batch_id, task_id, source_id, source_sha256, pipeline_fingerprint),
                )
        return batch_id, False

    def write_chunks(
        self, batch_id: UUID, chunks: list[dict[str, Any]], *, source_type: str
    ) -> None:
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT source_id, source_sha256, pipeline_fingerprint FROM "
                "insurance_terms.term_index_batches WHERE batch_id = %s AND state = 'STAGING'",
                (batch_id,),
            )
            batch = cursor.fetchone()
            if batch is None:
                raise TermRepositoryError("Staging batch is unavailable")
            source_id, source_sha256, fingerprint = batch
            for chunk in chunks:
                cursor.execute(
                    "INSERT INTO insurance_terms.term_chunks (chunk_id, batch_id, source_id, "
                    "product_id, profile_version_id, product_version, source_sha256, source_type, "
                    "title, clause_id, clause_title, heading_path, page_start, page_end, "
                    "chunk_index, "
                    "text_content, search_tokens, search_vector, embedding, embedding_model, "
                    "parser_version, chunker_version, tokenizer_version, pipeline_fingerprint) "
                    "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, "
                    "%s, %s, %s, %s, %s, to_tsvector('simple', %s), %s, %s, %s, %s, %s, %s)",
                    (
                        chunk["chunk_id"],
                        batch_id,
                        source_id,
                        chunk["product_id"],
                        chunk["profile_version_id"],
                        chunk["product_version"],
                        source_sha256,
                        source_type,
                        chunk["title"],
                        chunk.get("clause_id"),
                        chunk.get("clause_title"),
                        Jsonb(chunk["heading_path"]),
                        chunk.get("page_start"),
                        chunk.get("page_end"),
                        chunk["chunk_index"],
                        chunk["text_content"],
                        chunk["search_tokens"],
                        chunk["search_tokens"],
                        chunk["embedding"],
                        chunk["embedding_model"],
                        chunk["parser_version"],
                        chunk["chunker_version"],
                        chunk["tokenizer_version"],
                        fingerprint,
                    ),
                )
            cursor.execute(
                "UPDATE insurance_terms.term_index_batches SET chunk_count = chunk_count + %s, "
                "heartbeat_at = now(), updated_at = now() "
                "WHERE batch_id = %s AND state = 'STAGING'",
                (len(chunks), batch_id),
            )

    def touch_batch(self, batch_id: UUID) -> None:
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "UPDATE insurance_terms.term_index_batches "
                "SET heartbeat_at = now(), updated_at = now() "
                "WHERE batch_id = %s AND state = 'STAGING'",
                (batch_id,),
            )

    def activate_batch(self, *, batch_id: UUID, task_id: int) -> bool:
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT source_id, source_sha256, chunk_count, pipeline_fingerprint FROM "
                "insurance_terms.term_index_batches WHERE batch_id = %s AND task_id = %s "
                "AND state = 'STAGING' FOR UPDATE",
                (batch_id, task_id),
            )
            batch = cursor.fetchone()
            if batch is None:
                cursor.execute(
                    "SELECT 1 FROM insurance_terms.term_index_batches WHERE batch_id = %s "
                    "AND task_id = %s AND state = 'ACTIVE'",
                    (batch_id, task_id),
                )
                return cursor.fetchone() is not None
            source_id, source_sha256, chunk_count, fingerprint = batch
            self._lock_source(cursor, source_id)
            cursor.execute(
                "INSERT INTO insurance_terms.source_index_state (source_id, state) "
                "VALUES (%s, 'NEW') ON CONFLICT (source_id) DO NOTHING",
                (source_id,),
            )
            cursor.execute(
                "SELECT state, source_sha256, active_batch_id FROM "
                "insurance_terms.source_index_state WHERE source_id = %s FOR UPDATE",
                (source_id,),
            )
            state, known_hash, active_batch_id = cursor.fetchone()
            if state == "REMOVED" or (known_hash and known_hash != source_sha256):
                cursor.execute(
                    "UPDATE insurance_terms.term_index_batches SET state = 'FAILED', "
                    "error_code = 'SOURCE_STALE', updated_at = now() WHERE batch_id = %s",
                    (batch_id,),
                )
                return False
            if active_batch_id:
                cursor.execute(
                    "UPDATE insurance_terms.term_index_batches "
                    "SET state = 'RETIRED', updated_at = now() "
                    "WHERE batch_id = %s AND state = 'ACTIVE'",
                    (active_batch_id,),
                )
            cursor.execute(
                "UPDATE insurance_terms.term_index_batches SET state = 'ACTIVE', "
                "updated_at = now(), heartbeat_at = now() WHERE batch_id = %s",
                (batch_id,),
            )
            cursor.execute(
                "UPDATE insurance_terms.source_index_state "
                "SET source_sha256 = %s, state = 'INDEXED', "
                "active_batch_id = %s, chunk_count = %s, last_task_id = %s, updated_at = now(), "
                "last_error_code = NULL WHERE source_id = %s",
                (source_sha256, batch_id, chunk_count, task_id, source_id),
            )
        return True

    def remove_source(self, *, source_id: int, task_id: int) -> None:
        with self._connect() as conn, conn.cursor() as cursor:
            self._lock_source(cursor, source_id)
            cursor.execute(
                "INSERT INTO insurance_terms.source_index_state "
                "(source_id, state, last_task_id, updated_at) VALUES (%s, 'REMOVED', %s, now()) "
                "ON CONFLICT (source_id) DO UPDATE SET state = 'REMOVED', active_batch_id = NULL, "
                "chunk_count = 0, last_task_id = EXCLUDED.last_task_id, updated_at = now()",
                (source_id, task_id),
            )
            cursor.execute(
                "UPDATE insurance_terms.term_index_batches "
                "SET state = 'RETIRED', updated_at = now() "
                "WHERE source_id = %s",
                (source_id,),
            )
        # The tombstone is durable before chunk deletion, so an overlapping INDEX cannot reactivate.
        while True:
            with self._connect() as conn, conn.cursor() as cursor:
                cursor.execute(
                    "DELETE FROM insurance_terms.term_chunks WHERE chunk_id IN ("
                    "SELECT chunk_id FROM insurance_terms.term_chunks WHERE source_id = %s "
                    "LIMIT 1000) RETURNING chunk_id",
                    (source_id,),
                )
                if not cursor.fetchall():
                    break
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "DELETE FROM insurance_terms.term_index_batches WHERE source_id = %s", (source_id,)
            )

    def fail_batch(self, batch_id: UUID, error_code: str) -> None:
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "UPDATE insurance_terms.term_index_batches SET state = 'FAILED', error_code = %s, "
                "updated_at = now() WHERE batch_id = %s AND state = 'STAGING'",
                (error_code[:64], batch_id),
            )

    def search(
        self,
        *,
        product_id: int,
        profile_version_id: int,
        allowlist: list[tuple[int, str]],
        source_types: list[str] | None = None,
        query_tokens: str,
        query_embedding: list[float],
        embedding_model: str,
        exact_clause_id: str | None,
        limit: int = 6,
    ) -> list[dict[str, Any]]:
        if not allowlist:
            return []
        allowed_source_types = source_types or ["OFFICIAL_PRODUCT_TERM"]
        if any(
            value not in {"OFFICIAL_PRODUCT_TERM", "SYNTHETIC"}
            or (value == "SYNTHETIC" and not settings.terms_synthetic_sources_enabled)
            for value in allowed_source_types
        ):
            return []
        query_vector = Vector(query_embedding)
        allow_sql = " OR ".join("(c.source_id = %s AND c.source_sha256 = %s)" for _ in allowlist)
        allow_params: list[Any] = [value for pair in allowlist for value in pair]
        common = (
            "c.product_id = %s AND c.profile_version_id = %s AND "
            "c.source_type = ANY(%s) AND (" + allow_sql + ") "
            "AND s.state = 'INDEXED' AND s.active_batch_id = b.batch_id AND b.state = 'ACTIVE'"
        )
        params: list[Any] = [product_id, profile_version_id, allowed_source_types, *allow_params]
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "WITH allowed AS (SELECT c.chunk_id, row_number() OVER (ORDER BY "
                "CASE WHEN %s <> '' AND lower(c.clause_id) = lower(%s) THEN 0 ELSE 1 END, "
                "ts_rank_cd(c.search_vector, plainto_tsquery('simple', %s)) DESC) AS rank "
                "FROM insurance_terms.term_chunks c JOIN insurance_terms.term_index_batches b "
                "ON b.batch_id = c.batch_id JOIN insurance_terms.source_index_state s "
                "ON s.source_id = c.source_id WHERE " + common + " AND "
                "((%s <> '' AND lower(c.clause_id) = lower(%s)) OR "
                "c.search_vector @@ plainto_tsquery('simple', %s)) LIMIT %s), "
                "lex AS (SELECT chunk_id, rank FROM allowed), "
                "sem AS (SELECT c.chunk_id, "
                "row_number() OVER (ORDER BY c.embedding <=> %s) AS rank "
                "FROM insurance_terms.term_chunks c JOIN insurance_terms.term_index_batches b "
                "ON b.batch_id = c.batch_id JOIN insurance_terms.source_index_state s "
                "ON s.source_id = c.source_id WHERE "
                + common
                + " AND c.embedding_model = %s LIMIT %s), "
                "candidate_ids AS (SELECT chunk_id FROM lex UNION SELECT chunk_id FROM sem) "
                "SELECT c.chunk_id, c.source_id, c.source_sha256, c.product_id, "
                "c.profile_version_id, "
                "c.product_version, c.title, c.clause_id, c.clause_title, c.heading_path, "
                "c.page_start, c.page_end, c.text_content, c.pipeline_fingerprint, "
                "coalesce(1.0 / (60 + lex.rank), 0) + coalesce(1.0 / (60 + sem.rank), 0) + "
                "CASE WHEN %s <> '' AND lower(c.clause_id) = lower(%s) "
                "THEN 1.0 ELSE 0 END AS score "
                "FROM candidate_ids ids JOIN insurance_terms.term_chunks c "
                "ON c.chunk_id = ids.chunk_id "
                "JOIN insurance_terms.term_index_batches b ON b.batch_id = c.batch_id "
                "JOIN insurance_terms.source_index_state s ON s.source_id = c.source_id "
                "LEFT JOIN lex ON lex.chunk_id = c.chunk_id "
                "LEFT JOIN sem ON sem.chunk_id = c.chunk_id "
                "WHERE " + common + " ORDER BY score DESC LIMIT %s",
                (
                    exact_clause_id or "",
                    exact_clause_id or "",
                    query_tokens,
                    *params,
                    exact_clause_id or "",
                    exact_clause_id or "",
                    query_tokens,
                    limit,
                    query_vector,
                    *params,
                    embedding_model,
                    limit,
                    exact_clause_id or "",
                    exact_clause_id or "",
                    *params,
                    limit,
                ),
            )
            names = [column.name for column in cursor.description]
            return [dict(zip(names, row)) for row in cursor.fetchall()]

    def cleanup_batches(self, staging_hours: int = 24, retired_days: int = 2) -> int:
        removed = 0
        cutoff = datetime.now(timezone.utc) - timedelta(hours=staging_hours)
        with self._connect() as conn, conn.cursor() as cursor:
            cursor.execute(
                "SELECT batch_id FROM insurance_terms.term_index_batches b "
                "WHERE ((b.state IN ('STAGING', 'FAILED') AND b.heartbeat_at < %s) OR "
                "(b.state = 'RETIRED' AND b.updated_at < now() - (%s * interval '1 day'))) "
                "AND NOT EXISTS (SELECT 1 FROM insurance_terms.source_index_state s "
                "WHERE s.active_batch_id = b.batch_id) LIMIT 100",
                (cutoff, retired_days),
            )
            batch_ids = [row[0] for row in cursor.fetchall()]
            if batch_ids:
                cursor.execute(
                    "DELETE FROM insurance_terms.term_index_batches WHERE batch_id = ANY(%s)",
                    (batch_ids,),
                )
                removed = cursor.rowcount
        return removed

    def _connect(self):
        conn = psycopg.connect(self._database_url)
        register_vector(conn)
        return conn

    @staticmethod
    def _lock_source(cursor, source_id: int) -> None:
        cursor.execute(
            "SELECT pg_advisory_xact_lock(hashtextextended(%s::text, 20260322))",
            (str(source_id),),
        )


__all__ = ["PostgresTermRepository", "TermRepositoryError"]
