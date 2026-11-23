ALTER TABLE tab_insurance_term_index_task
  ADD COLUMN lease_token VARCHAR(64) NULL;

CREATE TABLE tab_insurance_term_source_audit (
  id BIGINT NOT NULL PRIMARY KEY,
  source_id BIGINT NOT NULL,
  action VARCHAR(32) NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NULL,
  actor_id BIGINT NULL,
  note VARCHAR(1000) NULL,
  create_time DATETIME(3) NOT NULL,
  KEY idx_insurance_term_source_audit_source (source_id, create_time, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
