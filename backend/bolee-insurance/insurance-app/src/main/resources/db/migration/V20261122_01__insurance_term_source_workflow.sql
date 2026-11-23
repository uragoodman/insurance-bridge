ALTER TABLE tab_insurance_term_source
  ADD COLUMN submitted_by BIGINT NULL,
  ADD COLUMN submitted_at DATETIME(3) NULL,
  ADD COLUMN reviewed_by BIGINT NULL,
  ADD COLUMN reviewed_at DATETIME(3) NULL,
  ADD COLUMN review_note VARCHAR(1000) NULL,
  ADD COLUMN published_by BIGINT NULL,
  ADD COLUMN published_at DATETIME(3) NULL,
  ADD COLUMN withdrawn_by BIGINT NULL,
  ADD COLUMN withdrawn_at DATETIME(3) NULL,
  ADD COLUMN withdrawal_reason VARCHAR(1000) NULL,
  ADD COLUMN index_status VARCHAR(24) NOT NULL DEFAULT 'NOT_INDEXED',
  ADD COLUMN indexed_sha256 CHAR(64) NULL,
  ADD COLUMN index_error VARCHAR(1000) NULL,
  ADD KEY idx_insurance_term_source_workflow (status, insurance_id, profile_version_id, data_state);

CREATE TABLE tab_insurance_term_index_task (
  id BIGINT NOT NULL PRIMARY KEY,
  source_id BIGINT NOT NULL,
  action VARCHAR(24) NOT NULL,
  source_sha256 CHAR(64) NOT NULL,
  task_status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  attempts INT NOT NULL DEFAULT 0,
  next_attempt_at DATETIME(3) NOT NULL,
  lease_until DATETIME(3) NULL,
  last_error VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  KEY idx_insurance_term_index_task_claim (task_status, next_attempt_at, id),
  KEY idx_insurance_term_index_task_source (source_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
