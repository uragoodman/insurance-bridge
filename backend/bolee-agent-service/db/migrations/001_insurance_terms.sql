CREATE EXTENSION IF NOT EXISTS vector;
CREATE SCHEMA IF NOT EXISTS insurance_terms;

CREATE TABLE insurance_terms.source_index_state (
    source_id BIGINT PRIMARY KEY,
    source_sha256 CHAR(64),
    state VARCHAR(16) NOT NULL CHECK (state IN ('INDEXED', 'REMOVED', 'NEW')),
    active_batch_id UUID NULL,
    chunk_count INTEGER NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    last_task_id BIGINT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error_code VARCHAR(64) NULL
);

CREATE TABLE insurance_terms.term_index_batches (
    batch_id UUID PRIMARY KEY,
    task_id BIGINT NOT NULL UNIQUE,
    source_id BIGINT NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    pipeline_fingerprint VARCHAR(160) NOT NULL,
    state VARCHAR(16) NOT NULL CHECK (state IN ('STAGING', 'ACTIVE', 'RETIRED', 'FAILED')),
    chunk_count INTEGER NOT NULL DEFAULT 0 CHECK (chunk_count >= 0),
    heartbeat_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    error_code VARCHAR(64) NULL
);

CREATE INDEX idx_term_batches_source_state
    ON insurance_terms.term_index_batches (source_id, state, updated_at);

CREATE TABLE insurance_terms.term_chunks (
    chunk_id UUID PRIMARY KEY,
    batch_id UUID NOT NULL REFERENCES insurance_terms.term_index_batches(batch_id) ON DELETE CASCADE,
    source_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    profile_version_id BIGINT NOT NULL,
    product_version INTEGER NOT NULL,
    source_sha256 CHAR(64) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    title VARCHAR(255) NOT NULL,
    clause_id VARCHAR(128) NULL,
    clause_title VARCHAR(255) NULL,
    heading_path JSONB NOT NULL DEFAULT '[]'::jsonb,
    page_start INTEGER NULL,
    page_end INTEGER NULL,
    chunk_index INTEGER NOT NULL,
    text_content TEXT NOT NULL,
    search_tokens TEXT NOT NULL,
    search_vector TSVECTOR NOT NULL,
    embedding VECTOR NOT NULL,
    embedding_model VARCHAR(160) NOT NULL,
    parser_version VARCHAR(64) NOT NULL,
    chunker_version VARCHAR(64) NOT NULL,
    tokenizer_version VARCHAR(64) NOT NULL,
    pipeline_fingerprint VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (batch_id, chunk_index)
);

CREATE INDEX idx_term_chunks_source_hash_version
    ON insurance_terms.term_chunks (source_id, source_sha256, profile_version_id);
CREATE INDEX idx_term_chunks_search_vector
    ON insurance_terms.term_chunks USING GIN (search_vector);

ALTER TABLE insurance_terms.source_index_state
    ADD CONSTRAINT fk_term_source_active_batch
    FOREIGN KEY (active_batch_id) REFERENCES insurance_terms.term_index_batches(batch_id);

-- Apply with a dedicated migration role, then grant SELECT to the Agent API role and
-- SELECT/INSERT/UPDATE/DELETE to the terms indexer role. Do not grant runtime DDL.
