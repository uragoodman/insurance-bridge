"""Wire and domain schemas for Java-approved insurance terms."""

from datetime import date, datetime
from typing import Literal

from pydantic import AliasChoices, ConfigDict, Field

from app.schemas.base import ContractModel


class TermProductVersion(ContractModel):
    profile_version_id: str = Field(
        validation_alias=AliasChoices("profileVersionId", "profile_version_id")
    )
    product_version: int = Field(
        validation_alias=AliasChoices("productVersion", "product_version"), ge=1
    )
    effective_from: datetime | None = Field(
        default=None, validation_alias=AliasChoices("effectiveFrom", "effective_from")
    )
    effective_to: datetime | None = Field(
        default=None, validation_alias=AliasChoices("effectiveTo", "effective_to")
    )


class TermSource(ContractModel):
    # Java returns its wider admin source view; only the retrieval contract fields
    # below cross into the Agent. Ignore unrelated storage and audit metadata.
    model_config = ConfigDict(
        extra="ignore",
        populate_by_name=True,
        str_strip_whitespace=True,
    )

    source_id: str = Field(validation_alias=AliasChoices("id", "source_id"))
    product_id: str = Field(validation_alias=AliasChoices("insuranceId", "product_id"))
    profile_version_id: str = Field(
        validation_alias=AliasChoices("profileVersionId", "profile_version_id")
    )
    product_version: int = Field(
        validation_alias=AliasChoices("productVersion", "product_version"), ge=1
    )
    source_type: str = Field(validation_alias=AliasChoices("sourceType", "source_type"))
    title: str
    sha256: str = Field(pattern=r"^[a-fA-F0-9]{64}$")
    status: str
    index_status: str = Field(validation_alias=AliasChoices("indexStatus", "index_status"))
    effective_from: date | None = Field(
        default=None, validation_alias=AliasChoices("effectiveFrom", "effective_from")
    )
    effective_to: date | None = Field(
        default=None, validation_alias=AliasChoices("effectiveTo", "effective_to")
    )


class TermIndexTask(ContractModel):
    task_id: str = Field(validation_alias=AliasChoices("taskId", "task_id"))
    source_id: str = Field(validation_alias=AliasChoices("sourceId", "source_id"))
    product_id: str = Field(validation_alias=AliasChoices("productId", "product_id"))
    profile_version_id: str = Field(
        validation_alias=AliasChoices("profileVersionId", "profile_version_id")
    )
    product_version: int = Field(
        validation_alias=AliasChoices("productVersion", "product_version"), ge=1
    )
    source_type: str = Field(
        default="OFFICIAL_PRODUCT_TERM",
        validation_alias=AliasChoices("sourceType", "source_type"),
    )
    action: Literal["INDEX", "REMOVE"]
    source_sha256: str = Field(
        validation_alias=AliasChoices("sourceSha256", "source_sha256"), pattern=r"^[a-fA-F0-9]{64}$"
    )
    title: str
    original_file_name: str = Field(
        validation_alias=AliasChoices("originalFileName", "original_file_name")
    )
    content_type: str = Field(validation_alias=AliasChoices("contentType", "content_type"))
    file_size: int = Field(
        validation_alias=AliasChoices("fileSize", "file_size"), ge=0, le=52_428_800
    )
    content_path: str | None = Field(
        default=None, validation_alias=AliasChoices("contentPath", "content_path")
    )
    lease_token: str = Field(
        validation_alias=AliasChoices("leaseToken", "lease_token"), min_length=16, max_length=64
    )
    lease_duration_seconds: int = Field(
        validation_alias=AliasChoices("leaseDurationSeconds", "lease_duration_seconds"),
        ge=1,
        le=3600,
    )
    lease_until: datetime = Field(validation_alias=AliasChoices("leaseUntil", "lease_until"))


class ParsedBlock(ContractModel):
    text: str = Field(min_length=1)
    page: int | None = Field(default=None, ge=1)
    heading_path: list[str] = Field(default_factory=list, max_length=12)
    clause_id: str | None = Field(default=None, max_length=128)
    clause_title: str | None = Field(default=None, max_length=255)
    block_type: Literal["paragraph", "table", "heading"] = "paragraph"


class TermEvidence(ContractModel):
    evidence_key: str = Field(min_length=1, max_length=64)
    source_id: str
    source_sha256: str
    product_id: str
    product_version: int
    profile_version_id: str
    source_title: str
    clause_id: str | None = None
    clause_title: str | None = None
    page_start: int | None = Field(default=None, ge=1)
    page_end: int | None = Field(default=None, ge=1)
    chunk_id: str
    excerpt: str = Field(min_length=1, max_length=1200)


class GroundedClaim(ContractModel):
    text: str = Field(min_length=1, max_length=1000)
    evidence_keys: list[str] = Field(min_length=1, max_length=6)


class GroundedAnswer(ContractModel):
    status: Literal["ANSWERED", "INSUFFICIENT_EVIDENCE"]
    claims: list[GroundedClaim] = Field(default_factory=list, max_length=8)


class TermCitation(ContractModel):
    citation_id: str = Field(
        validation_alias=AliasChoices("citationId", "citation_id"), pattern=r"^[0-9a-fA-F-]{36}$"
    )
    source_id: str = Field(
        validation_alias=AliasChoices("sourceId", "source_id"), pattern=r"^[0-9]{1,19}$"
    )
    source_title: str = Field(
        validation_alias=AliasChoices("sourceTitle", "source_title"), max_length=255
    )
    product_id: str = Field(
        validation_alias=AliasChoices("productId", "product_id"), pattern=r"^[0-9]{1,19}$"
    )
    product_version: int = Field(
        validation_alias=AliasChoices("productVersion", "product_version"), ge=1
    )
    profile_version_id: str = Field(
        validation_alias=AliasChoices("profileVersionId", "profile_version_id"),
        pattern=r"^[0-9]{1,19}$",
    )
    clause_id: str | None = Field(
        default=None, validation_alias=AliasChoices("clauseId", "clause_id"), max_length=128
    )
    clause_title: str | None = Field(
        default=None, validation_alias=AliasChoices("clauseTitle", "clause_title"), max_length=255
    )
    page_start: int | None = Field(
        default=None, validation_alias=AliasChoices("pageStart", "page_start"), ge=1
    )
    page_end: int | None = Field(
        default=None, validation_alias=AliasChoices("pageEnd", "page_end"), ge=1
    )
    excerpt: str = Field(min_length=1, max_length=1200)


class TermSearchOutcome(ContractModel):
    status: Literal[
        "ANSWERED",
        "NEEDS_PRODUCT_CLARIFICATION",
        "NEEDS_VERSION_CLARIFICATION",
        "NO_PUBLISHED_TERMS",
        "INSUFFICIENT_EVIDENCE",
        "JAVA_UNAVAILABLE",
        "INDEX_NOT_READY",
    ]
    answer: str = Field(min_length=1, max_length=8000)
    product_id: str | None = None
    profile_version_id: str | None = None
    term_citations: list[TermCitation] = Field(default_factory=list, max_length=6)
