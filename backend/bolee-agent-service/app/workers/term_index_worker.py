"""Poll Java's leased task API and build versioned insurance term indexes."""

import logging
import signal
import threading

from app.clients.java_term_index_api import JavaTermIndexApiClient, JavaTermIndexApiError
from app.clients.oauth2 import OAuth2ClientCredentials
from app.config import settings
from app.infrastructure.terms.document_parser import InsuranceTermDocumentParser
from app.infrastructure.terms.embedding_provider import OpenAICompatibleEmbeddingProvider
from app.infrastructure.terms.postgres_term_repository import PostgresTermRepository
from app.services.terms.index_task_service import InsuranceTermIndexTaskService

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("bolee-agent.term-index-worker")
_stopping = threading.Event()


def main() -> None:
    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)
    oauth = OAuth2ClientCredentials(
        token_url=settings.oauth_token_url,
        client_id=settings.terms_indexer_client_id,
        client_secret=settings.terms_indexer_client_secret,
        scope=settings.terms_indexer_scope,
        timeout_seconds=settings.oauth_timeout_seconds,
    )
    java = JavaTermIndexApiClient(settings.terms_indexer_base_url, oauth)
    repository = PostgresTermRepository(
        settings.terms_index_database_url or settings.terms_database_url
    )
    processor = InsuranceTermIndexTaskService(
        java,
        repository,
        InsuranceTermDocumentParser(),
        OpenAICompatibleEmbeddingProvider(),
    )
    logger.info("Insurance terms index worker started")
    while not _stopping.is_set():
        try:
            task = java.claim()
            if task is not None:
                processor.process(task)
                continue
            repository.cleanup_batches()
        except (JavaTermIndexApiError, OSError, RuntimeError, ValueError) as exc:
            logger.warning("Term index worker iteration failed; type=%s", type(exc).__name__)
        _stopping.wait(max(0.5, settings.terms_worker_poll_seconds))
    logger.info("Insurance terms index worker stopped")


def _stop(_signum: int, _frame) -> None:
    _stopping.set()


if __name__ == "__main__":
    main()
