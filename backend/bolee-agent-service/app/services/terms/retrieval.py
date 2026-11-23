"""Java-authorized product terms retrieval and evidence-grounded response flow."""

import logging
import re
import uuid

from pydantic import ValidationError

from app.config import settings
from app.contexts.tool import AgentToolContext
from app.infrastructure.terms.embedding_provider import (
    EmbeddingProviderError,
    OpenAICompatibleEmbeddingProvider,
)
from app.infrastructure.terms.postgres_term_repository import (
    PostgresTermRepository,
    TermRepositoryError,
)
from app.schemas.insurance_terms import (
    TermEvidence,
    TermProductVersion,
    TermSearchOutcome,
    TermSource,
)
from app.schemas.java import JavaResponseEnvelope
from app.schemas.product_catalog import ProductMatchType, RecommendationStatus
from app.services.product_catalog import ProductCatalogService, ProductCatalogServiceError
from app.services.terms.grounded_answer import GroundedAnswerGenerator
from app.services.terms.tokenizer import searchable_text

logger = logging.getLogger("bolee-agent.terms-retrieval")
_CLAUSE_ID = re.compile(r"第\s*([一二三四五六七八九十百千万0-9]+)\s*条")
_MAX_SIGNED_BIGINT = 9_223_372_036_854_775_807


class InsuranceTermsServiceError(RuntimeError):
    """Sanitized terms retrieval error."""


class InsuranceTermsService:
    _VERSIONS_TEMPLATE = "/internal/agent-tools/v1/insurance-terms/products/{product_id}/versions"
    _SOURCES_TEMPLATE = "/internal/agent-tools/v1/insurance-terms/products/{product_id}/sources"

    def __init__(
        self,
        catalog_service: ProductCatalogService | None = None,
        repository: PostgresTermRepository | None = None,
        embedding_provider: OpenAICompatibleEmbeddingProvider | None = None,
        answer_generator: GroundedAnswerGenerator | None = None,
    ) -> None:
        self._catalog = catalog_service or ProductCatalogService()
        self._repository = repository
        self._embedding_provider = embedding_provider
        self._answer_generator = answer_generator or GroundedAnswerGenerator()

    def search(
        self, product_name: str, question: str, context: AgentToolContext
    ) -> TermSearchOutcome:
        context.result_collector.clear()
        normalized_product_name = product_name.strip()
        normalized_question = question.strip()
        if not normalized_product_name or not normalized_question:
            return self._record(
                context,
                TermSearchOutcome(
                    status="NEEDS_PRODUCT_CLARIFICATION",
                    answer="请提供要查询条款的具体保险产品名称。",
                ),
            )
        try:
            search = self._catalog.search_by_category(
                category_id=None,
                limit=3,
                context=context,
                keyword=normalized_product_name,
            )
            resolved_candidates = [
                candidate
                for candidate in search.candidates
                if candidate.match_type
                in {ProductMatchType.EXACT_NAME, ProductMatchType.APPROVED_ALIAS}
            ]
            if (
                search.status != RecommendationStatus.MATCHED
                or len(resolved_candidates) != 1
            ):
                names = [candidate.name for candidate in search.candidates]
                answer = (
                    "没有找到名称完全匹配的产品，但找到了相近产品，请确认是否查询："
                    + "、".join(names)
                    if names
                    else "没有找到匹配或相近的产品，请补充保险公司、产品全名或保障类型。"
                )
                return self._record(
                    context,
                    TermSearchOutcome(
                        status="NEEDS_PRODUCT_CLARIFICATION",
                        answer=answer,
                    ),
                )
            product = resolved_candidates[0]
            product_id = self._numeric_id(product.product_id)
            versions = self._get_versions(product_id, context)
            if not versions:
                return self._record(
                    context,
                    TermSearchOutcome(
                        status="NO_PUBLISHED_TERMS",
                        answer="该产品目前没有可检索的已发布条款资料。",
                        product_id=str(product_id),
                    ),
                )
            if len(versions) != 1:
                labels = [f"产品版本 {item.product_version}" for item in versions]
                return self._record(
                    context,
                    TermSearchOutcome(
                        status="NEEDS_VERSION_CLARIFICATION",
                        answer="该产品有多个有效条款版本，请确认查询版本：" + "、".join(labels),
                        product_id=str(product_id),
                    ),
                )
            version = versions[0]
            profile_version_id = self._numeric_id(version.profile_version_id)
            sources = self._get_sources(product_id, profile_version_id, context)
            eligible = [
                source
                for source in sources
                if source.product_id == str(product_id)
                and source.profile_version_id == str(profile_version_id)
                and (
                    source.source_type == "OFFICIAL_PRODUCT_TERM"
                    or (
                        settings.terms_synthetic_sources_enabled
                        and source.source_type == "SYNTHETIC"
                    )
                )
                and source.status == "PUBLISHED"
                and source.index_status == "INDEXED"
            ]
            if not eligible:
                return self._record(
                    context,
                    TermSearchOutcome(
                        status="NO_PUBLISHED_TERMS",
                        answer="该产品版本目前没有可用的正式条款来源。",
                        product_id=str(product_id),
                        profile_version_id=str(profile_version_id),
                    ),
                )
            query_vector = self._embeddings().embed([normalized_question])[0]
            exact_match = _CLAUSE_ID.search(normalized_question)
            rows = self._repo().search(
                product_id=product_id,
                profile_version_id=profile_version_id,
                allowlist=[(int(item.source_id), item.sha256.lower()) for item in eligible],
                source_types=sorted({item.source_type for item in eligible}),
                query_tokens=searchable_text(normalized_question),
                query_embedding=query_vector,
                embedding_model=self._embeddings().model,
                exact_clause_id=exact_match.group(0) if exact_match else None,
                limit=6,
            )
            if not rows:
                return self._record(
                    context,
                    TermSearchOutcome(
                        status="INDEX_NOT_READY",
                        answer="条款来源已发布，但索引暂不可用，请稍后重试。",
                        product_id=str(product_id),
                        profile_version_id=str(profile_version_id),
                    ),
                )
            sources_by_key = {(item.source_id, item.sha256.lower()): item for item in eligible}
            evidence: list[TermEvidence] = []
            for row in rows:
                source = sources_by_key.get(
                    (str(row["source_id"]), row["source_sha256"].strip().lower())
                )
                if source is None:
                    logger.warning("Term retrieval returned a row outside the Java allowlist")
                    continue
                evidence.append(
                    TermEvidence(
                        evidence_key=str(uuid.uuid4()),
                        source_id=str(row["source_id"]),
                        source_sha256=row["source_sha256"].strip().lower(),
                        product_id=str(row["product_id"]),
                        product_version=row["product_version"],
                        profile_version_id=str(row["profile_version_id"]),
                        source_title=source.title,
                        clause_id=row["clause_id"],
                        clause_title=row["clause_title"],
                        page_start=row["page_start"],
                        page_end=row["page_end"],
                        chunk_id=str(row["chunk_id"]),
                        excerpt=row["text_content"][:1200],
                    )
                )
            if not evidence:
                return self._record(
                    context,
                    TermSearchOutcome(
                        status="INDEX_NOT_READY",
                        answer="条款索引与当前 Java 来源状态不一致，暂时无法回答。",
                        product_id=str(product_id),
                        profile_version_id=str(profile_version_id),
                    ),
                )
            answer, citations = self._answer_generator.generate(normalized_question, evidence)
            status = "ANSWERED" if citations else "INSUFFICIENT_EVIDENCE"
            return self._record(
                context,
                TermSearchOutcome(
                    status=status,
                    answer=answer,
                    product_id=str(product_id),
                    profile_version_id=str(profile_version_id),
                    term_citations=citations,
                ),
            )
        except ProductCatalogServiceError:
            logger.warning("Terms product resolution failed")
            return self._record(
                context,
                TermSearchOutcome(
                    status="JAVA_UNAVAILABLE", answer="产品目录暂时不可用，请稍后重试。"
                ),
            )
        except InsuranceTermsServiceError as exc:
            logger.warning("Terms source validation failed; error=%s", type(exc).__name__)
            return self._record(
                context,
                TermSearchOutcome(
                    status="JAVA_UNAVAILABLE", answer="条款来源暂时无法核验，请稍后重试。"
                ),
            )
        except (EmbeddingProviderError, TermRepositoryError, OSError) as exc:
            logger.warning("Terms retrieval infrastructure failed; error=%s", type(exc).__name__)
            return self._record(
                context,
                TermSearchOutcome(
                    status="INDEX_NOT_READY", answer="条款检索服务暂时不可用，请稍后重试。"
                ),
            )
        except Exception as exc:
            # Provider configuration, PostgreSQL driver errors, and unexpected contract
            # failures must fail closed instead of letting the general Agent answer from memory.
            logger.warning("Terms retrieval failed closed; error=%s", type(exc).__name__)
            return self._record(
                context,
                TermSearchOutcome(
                    status="INDEX_NOT_READY", answer="条款检索服务暂时不可用，请稍后重试。"
                ),
            )

    def _get_versions(self, product_id: int, context: AgentToolContext) -> list[TermProductVersion]:
        path = self._VERSIONS_TEMPLATE.format(product_id=product_id)
        try:
            raw = context.java_internal_api.get_json(path, context.authorization_context)
            envelope = JavaResponseEnvelope[list[TermProductVersion]].model_validate(raw)
        except ValidationError as exc:
            error_fields = [
                f"{'.'.join(str(part) for part in issue.get('loc', ()))}:"
                f"{issue.get('type', 'invalid')}"
                for issue in exc.errors(include_input=False)
            ]
            logger.warning(
                "Invalid Java product term versions contract; fields=%s",
                ",".join(error_fields)[:300],
            )
            raise InsuranceTermsServiceError("Invalid Java product term versions response") from exc
        except Exception as exc:
            raise InsuranceTermsServiceError("Invalid Java product term versions response") from exc
        if envelope.code != 200 or envelope.data is None:
            raise InsuranceTermsServiceError("Java product term versions request was rejected")
        return envelope.data

    def _get_sources(
        self, product_id: int, profile_version_id: int, context: AgentToolContext
    ) -> list[TermSource]:
        path = self._SOURCES_TEMPLATE.format(product_id=product_id)
        try:
            raw = context.java_internal_api.get_json(
                path,
                context.authorization_context,
                params={"profileVersionId": profile_version_id},
            )
            envelope = JavaResponseEnvelope[list[TermSource]].model_validate(raw)
        except ValidationError as exc:
            error_fields = [
                f"{'.'.join(str(part) for part in issue.get('loc', ()))}:"
                f"{issue.get('type', 'invalid')}"
                for issue in exc.errors(include_input=False)
            ]
            logger.warning(
                "Invalid Java product term sources contract; fields=%s",
                ",".join(error_fields)[:300],
            )
            raise InsuranceTermsServiceError("Invalid Java product term sources response") from exc
        except Exception as exc:
            logger.warning(
                "Java product term sources request failed; error=%s", type(exc).__name__
            )
            raise InsuranceTermsServiceError("Invalid Java product term sources response") from exc
        if envelope.code != 200 or envelope.data is None:
            raise InsuranceTermsServiceError("Java product term sources request was rejected")
        for source in envelope.data:
            for field_name, value in (
                ("source_id", source.source_id),
                ("product_id", source.product_id),
                ("profile_version_id", source.profile_version_id),
            ):
                try:
                    self._numeric_id(value)
                except InsuranceTermsServiceError:
                    logger.warning(
                        "Java product term source has invalid identifier; field=%s", field_name
                    )
                    raise
        return envelope.data

    def _repo(self) -> PostgresTermRepository:
        if self._repository is None:
            self._repository = PostgresTermRepository(settings.terms_database_url)
        return self._repository

    def _embeddings(self) -> OpenAICompatibleEmbeddingProvider:
        if self._embedding_provider is None:
            self._embedding_provider = OpenAICompatibleEmbeddingProvider()
        return self._embedding_provider

    @staticmethod
    def _numeric_id(value: str) -> int:
        if not value.isdigit() or len(value) > 19:
            raise InsuranceTermsServiceError("Java returned an invalid numeric identifier")
        number = int(value)
        if number <= 0 or number > _MAX_SIGNED_BIGINT:
            raise InsuranceTermsServiceError("Java returned an out-of-range identifier")
        return number

    @staticmethod
    def _record(context: AgentToolContext, result: TermSearchOutcome) -> TermSearchOutcome:
        context.result_collector.record_terms_search(result)
        return result


__all__ = ["InsuranceTermsService", "InsuranceTermsServiceError"]
