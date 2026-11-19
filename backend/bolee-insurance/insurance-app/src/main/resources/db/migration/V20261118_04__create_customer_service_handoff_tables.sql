-- Customer service channels and AI-assisted handoff requests.
-- No demo contact details or support queues are seeded by this migration.

CREATE TABLE tab_customer_service_channel (
  id BIGINT NOT NULL PRIMARY KEY,
  company_no VARCHAR(64) NOT NULL,
  channel_code VARCHAR(64) NOT NULL,
  channel_type VARCHAR(24) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  target_value VARCHAR(512) NOT NULL,
  service_hours_desc VARCHAR(512) NULL,
  timezone_id VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
  sort_no INT NOT NULL DEFAULT 0,
  effective_from DATETIME(3) NULL,
  effective_until DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_customer_service_channel_tenant_code (company_no, channel_code),
  KEY idx_customer_service_channel_tenant_status_sort
    (company_no, status, sort_no, effective_from, effective_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_customer_service_request (
  id BIGINT NOT NULL PRIMARY KEY,
  request_no VARCHAR(36) NOT NULL,
  company_no VARCHAR(64) NOT NULL,
  owner_user_id BIGINT NOT NULL,
  client_request_id VARCHAR(36) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  source_type VARCHAR(24) NOT NULL,
  source_conversation_id BIGINT NULL,
  source_turn_id VARCHAR(36) NULL,
  channel_id BIGINT NULL,
  request_type VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  preferred_contact_method VARCHAR(24) NULL,
  contact_value_ciphertext MEDIUMTEXT NULL,
  contact_key_version VARCHAR(32) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'SUBMITTED',
  assigned_operator_id BIGINT NULL,
  customer_note_ciphertext MEDIUMTEXT NULL,
  note_key_version VARCHAR(32) NULL,
  context_share_consent_at DATETIME(3) NULL,
  submitted_at DATETIME(3) NOT NULL,
  accepted_at DATETIME(3) NULL,
  resolved_at DATETIME(3) NULL,
  cancelled_at DATETIME(3) NULL,
  expires_at DATETIME(3) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_customer_service_request_no (request_no),
  UNIQUE KEY uk_customer_service_request_idempotency
    (company_no, owner_user_id, client_request_id),
  KEY idx_customer_service_request_owner_created (company_no, owner_user_id, create_time, id),
  KEY idx_customer_service_request_queue (company_no, status, update_time, id),
  KEY idx_customer_service_request_assignee
    (company_no, assigned_operator_id, status, update_time),
  KEY idx_customer_service_request_source_conversation (company_no, source_conversation_id),
  KEY idx_customer_service_request_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_customer_service_request_event (
  id BIGINT NOT NULL PRIMARY KEY,
  company_no VARCHAR(64) NOT NULL,
  request_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  actor_type VARCHAR(16) NOT NULL,
  actor_user_id BIGINT NULL,
  event_code VARCHAR(32) NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NULL,
  note_ciphertext MEDIUMTEXT NULL,
  note_key_version VARCHAR(32) NULL,
  create_time DATETIME(3) NOT NULL,
  create_by BIGINT NULL,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  KEY idx_customer_service_event_request_time (company_no, request_id, create_time, id),
  KEY idx_customer_service_event_owner_time (company_no, owner_user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
