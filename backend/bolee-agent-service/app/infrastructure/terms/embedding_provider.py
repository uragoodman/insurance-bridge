"""OpenAI-compatible embedding adapter isolated from chat model settings."""

import math

import httpx

from app.config import settings


class EmbeddingProviderError(RuntimeError):
    """Embedding API or output validation failure."""


class OpenAICompatibleEmbeddingProvider:
    def __init__(self) -> None:
        if not all(
            (
                settings.terms_embedding_base_url,
                settings.terms_embedding_api_key,
                settings.terms_embedding_model,
                settings.terms_embedding_dimensions > 0,
            )
        ):
            raise ValueError("Terms embedding provider configuration is incomplete")
        self._base_url = settings.terms_embedding_base_url.rstrip("/")
        self.model = settings.terms_embedding_model
        self.dimensions = settings.terms_embedding_dimensions
        self._client = httpx.Client(
            timeout=httpx.Timeout(settings.terms_embedding_timeout_seconds, connect=5.0)
        )

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        try:
            response = self._client.post(
                self._base_url + "/embeddings",
                headers={"Authorization": f"Bearer {settings.terms_embedding_api_key}"},
                json={"model": self.model, "input": texts},
            )
            response.raise_for_status()
            payload = response.json()
            items = payload["data"]
            vectors = [item["embedding"] for item in sorted(items, key=lambda item: item["index"])]
        except (httpx.HTTPError, ValueError, KeyError, TypeError) as exc:
            raise EmbeddingProviderError("Embedding provider request failed") from exc
        if len(vectors) != len(texts):
            raise EmbeddingProviderError("Embedding provider returned a different vector count")
        for vector in vectors:
            if len(vector) != self.dimensions or any(
                not isinstance(value, (int, float)) or not math.isfinite(value) for value in vector
            ):
                raise EmbeddingProviderError("Embedding provider returned an invalid vector")
        return [[float(value) for value in vector] for vector in vectors]
