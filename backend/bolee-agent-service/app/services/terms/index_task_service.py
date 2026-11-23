"""Process one leased Java INDEX/REMOVE task and keep its lease alive."""

import hashlib
import logging
import os
import re
import tempfile
import threading
from pathlib import Path

from app.clients.java_term_index_api import JavaTermIndexApiClient, JavaTermIndexApiError
from app.config import settings
from app.infrastructure.terms.document_parser import (
    InsuranceTermDocumentParser,
    PermanentDocumentError,
    TransientDocumentError,
)
from app.infrastructure.terms.embedding_provider import (
    EmbeddingProviderError,
    OpenAICompatibleEmbeddingProvider,
)
from app.infrastructure.terms.postgres_term_repository import PostgresTermRepository
from app.schemas.insurance_terms import TermIndexTask
from app.services.terms.chunking import CHUNKER_VERSION, build_chunks
from app.services.terms.tokenizer import TOKENIZER_VERSION

logger = logging.getLogger("bolee-agent.terms-indexer")
_PARSER_VERSION = "terms-parser-v1"
_EMBED_BATCH_SIZE = 32


class LeaseLost(RuntimeError):
    """Raised after Java no longer recognizes this worker lease."""


class SourceIntegrityError(PermanentDocumentError):
    pass


class InsuranceTermIndexTaskService:
    def __init__(
        self,
        java_client: JavaTermIndexApiClient,
        repository: PostgresTermRepository,
        parser: InsuranceTermDocumentParser,
        embeddings: OpenAICompatibleEmbeddingProvider,
    ) -> None:
        self._java = java_client
        self._repository = repository
        self._parser = parser
        self._embeddings = embeddings

    def process(self, task: TermIndexTask) -> None:
        keeper = _LeaseKeeper(self._java, task)
        committed = False
        batch_id = None
        keeper.start()
        try:
            if task.action == "REMOVE":
                self._repository.remove_source(
                    source_id=int(task.source_id), task_id=int(task.task_id)
                )
                committed = True
            else:
                batch_id, already_active = self._index(task, keeper)
                committed = already_active or batch_id is not None
            keeper.check()
        except LeaseLost:
            logger.warning(
                "Term task lease was lost; task_id=%s action=%s", task.task_id, task.action
            )
            return
        except PermanentDocumentError as exc:
            if batch_id is not None:
                self._repository.fail_batch(batch_id, _error_code(exc))
            self._fail(task, str(exc), retryable=False, keeper=keeper)
            return
        except (
            TransientDocumentError,
            EmbeddingProviderError,
            JavaTermIndexApiError,
            OSError,
        ) as exc:
            if batch_id is not None:
                self._repository.fail_batch(batch_id, _error_code(exc))
            self._fail(task, str(exc), retryable=True, keeper=keeper)
            return
        except Exception as exc:
            if batch_id is not None:
                self._repository.fail_batch(batch_id, "UNEXPECTED_ERROR")
            self._fail(task, "UNEXPECTED_INDEXING_ERROR", retryable=True, keeper=keeper)
            logger.warning("Unexpected term task error; type=%s", type(exc).__name__)
            return
        finally:
            keeper.stop()

        if committed:
            try:
                self._java.complete(task)
            except JavaTermIndexApiError:
                # The database has committed. Leave the Java lease to expire so the same task
                # can replay idempotently; never turn a committed task into a failure callback.
                logger.warning("Term task completion callback failed; task_id=%s", task.task_id)
                return

    def _index(self, task: TermIndexTask, keeper: "_LeaseKeeper"):
        allowed_source_types = {"OFFICIAL_PRODUCT_TERM"}
        if settings.terms_synthetic_sources_enabled:
            allowed_source_types.add("SYNTHETIC")
        if task.source_type not in allowed_source_types:
            raise PermanentDocumentError("UNSUPPORTED_SOURCE_TYPE")
        if task.file_size <= 0 or task.file_size > settings.terms_worker_max_file_bytes:
            raise PermanentDocumentError("SOURCE_SIZE_OUT_OF_RANGE")
        with tempfile.TemporaryDirectory(prefix="insurance-term-") as temp_dir:
            suffix = Path(task.original_file_name).suffix.lower()
            path = os.path.join(temp_dir, "source" + suffix)
            self._java.download(task, path)
            digest = hashlib.sha256()
            size = 0
            with open(path, "rb") as source_file:
                while True:
                    data = source_file.read(64 * 1024)
                    if not data:
                        break
                    digest.update(data)
                    size += len(data)
                    if size > settings.terms_worker_max_file_bytes:
                        raise PermanentDocumentError("SOURCE_SIZE_OUT_OF_RANGE")
            if size != task.file_size or digest.hexdigest().lower() != task.source_sha256.lower():
                raise SourceIntegrityError("SOURCE_HASH_OR_SIZE_MISMATCH")
            keeper.check()
            blocks = self._parser.parse(path, task.original_file_name)
            chunks = build_chunks(
                blocks,
                product_id=int(task.product_id),
                profile_version_id=int(task.profile_version_id),
                product_version=task.product_version,
                title=task.title,
            )
            if not chunks:
                raise PermanentDocumentError("DOCUMENT_HAS_NO_INDEXABLE_TEXT")
            fingerprint = _pipeline_fingerprint(self._embeddings)
            started = self._repository.begin_batch(
                task_id=int(task.task_id),
                source_id=int(task.source_id),
                source_sha256=task.source_sha256.lower(),
                pipeline_fingerprint=fingerprint,
            )
            if started is None:
                # REMOVE has already tombstoned this source. This stale INDEX is a no-op;
                # its success callback lets Java finish the stale task and enqueue cleanup.
                return None, True
            batch_id, already_active = started
            if already_active:
                return batch_id, True
            for offset in range(0, len(chunks), _EMBED_BATCH_SIZE):
                keeper.check()
                batch = chunks[offset : offset + _EMBED_BATCH_SIZE]
                vectors = self._embeddings.embed([item["embedding_text"] for item in batch])
                for item, vector in zip(batch, vectors):
                    item["embedding"] = vector
                    item["embedding_model"] = self._embeddings.model
                self._repository.write_chunks(batch_id, batch, source_type=task.source_type)
                self._repository.touch_batch(batch_id)
            keeper.check()
            if not self._repository.activate_batch(batch_id=batch_id, task_id=int(task.task_id)):
                return batch_id, True
            return batch_id, False

    def _fail(
        self, task: TermIndexTask, reason: str, retryable: bool, keeper: "_LeaseKeeper"
    ) -> None:
        try:
            keeper.check()
            self._java.fail(task, reason[:1000], retryable=retryable)
        except LeaseLost:
            return
        except JavaTermIndexApiError:
            logger.warning("Term task failure callback failed; task_id=%s", task.task_id)


class _LeaseKeeper:
    def __init__(self, java_client: JavaTermIndexApiClient, task: TermIndexTask) -> None:
        self._java = java_client
        self._task = task
        self._lost = threading.Event()
        self._stop = threading.Event()
        self._thread = threading.Thread(
            target=self._run, name=f"term-lease-{task.task_id}", daemon=True
        )

    def start(self) -> None:
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread.is_alive():
            self._thread.join(timeout=2)

    def check(self) -> None:
        if self._lost.is_set():
            raise LeaseLost("Java task lease is no longer valid")

    def _run(self) -> None:
        interval = max(5.0, min(45.0, self._task.lease_duration_seconds / 3.0))
        while not self._stop.wait(interval):
            try:
                self._java.heartbeat(self._task)
            except Exception:
                self._lost.set()
                return


def _pipeline_fingerprint(embeddings: OpenAICompatibleEmbeddingProvider) -> str:
    import hashlib

    value = "|".join(
        (
            _PARSER_VERSION,
            CHUNKER_VERSION,
            TOKENIZER_VERSION,
            embeddings.model,
            str(embeddings.dimensions),
        )
    )
    return hashlib.sha256(value.encode("utf-8")).hexdigest()


def _error_code(error: Exception) -> str:
    message = str(error).strip()
    return re.sub(r"[^A-Z0-9_]+", "_", message.upper())[:64] or "INDEXING_ERROR"


__all__ = ["InsuranceTermIndexTaskService", "LeaseLost"]
