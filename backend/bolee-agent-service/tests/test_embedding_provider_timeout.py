"""Tests for the configurable terms embedding provider timeout."""

import unittest
from types import SimpleNamespace
from unittest.mock import patch

from app.infrastructure.terms import embedding_provider


class EmbeddingProviderTimeoutTests(unittest.TestCase):
    def test_provider_uses_configured_read_timeout_and_short_connect_timeout(self) -> None:
        configured = SimpleNamespace(
            terms_embedding_base_url="http://embedding:11434/v1",
            terms_embedding_api_key="test-key",
            terms_embedding_model="bge-m3",
            terms_embedding_dimensions=1024,
            terms_embedding_timeout_seconds=120.0,
        )
        with (
            patch.object(embedding_provider, "settings", configured),
            patch.object(embedding_provider.httpx, "Client") as client_factory,
        ):
            embedding_provider.OpenAICompatibleEmbeddingProvider()

        timeout = client_factory.call_args.kwargs["timeout"]
        self.assertEqual(timeout.read, 120.0)
        self.assertEqual(timeout.connect, 5.0)


if __name__ == "__main__":
    unittest.main()
