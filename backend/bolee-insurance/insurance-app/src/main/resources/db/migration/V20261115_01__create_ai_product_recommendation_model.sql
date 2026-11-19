CREATE TABLE tab_ai_rec_attribute_definition (
  id BIGINT NOT NULL PRIMARY KEY,
  category_id BIGINT NOT NULL,
  attribute_code VARCHAR(80) NOT NULL,
  definition_version INT NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  value_type VARCHAR(24) NOT NULL,
  unit_code VARCHAR(24) NULL,
  sensitivity_level VARCHAR(24) NOT NULL DEFAULT 'NONE',
  ai_enabled TINYINT(1) NOT NULL DEFAULT 0,
  allow_hard_filter TINYINT(1) NOT NULL DEFAULT 0,
  allow_preference TINYINT(1) NOT NULL DEFAULT 0,
  allow_compare TINYINT(1) NOT NULL DEFAULT 0,
  missing_policy VARCHAR(24) NOT NULL DEFAULT 'INCOMPLETE',
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  reviewer_id BIGINT NULL,
  reviewed_at DATETIME(3) NULL,
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_ai_rec_attr_version (category_id, attribute_code, definition_version),
  KEY idx_ai_rec_attr_category_status (category_id, status, ai_enabled, attribute_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_rec_attribute_operator (
  id BIGINT NOT NULL PRIMARY KEY,
  definition_id BIGINT NOT NULL,
  operator_code VARCHAR(16) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_rec_attr_operator (definition_id, operator_code),
  CONSTRAINT fk_ai_rec_operator_definition FOREIGN KEY (definition_id)
    REFERENCES tab_ai_rec_attribute_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_rec_attribute_option (
  id BIGINT NOT NULL PRIMARY KEY,
  definition_id BIGINT NOT NULL,
  value_code VARCHAR(80) NOT NULL,
  display_name VARCHAR(120) NOT NULL,
  sort_no INT NOT NULL DEFAULT 0,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_rec_attr_option (definition_id, value_code),
  CONSTRAINT fk_ai_rec_option_definition FOREIGN KEY (definition_id)
    REFERENCES tab_ai_rec_attribute_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_product_profile_version (
  id BIGINT NOT NULL PRIMARY KEY,
  product_id BIGINT NOT NULL,
  product_name_snapshot VARCHAR(255) NOT NULL,
  sort_no INT NULL,
  product_version INT NOT NULL,
  category_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  source_type VARCHAR(32) NULL,
  source_ref VARCHAR(512) NULL,
  source_hash CHAR(64) NULL,
  reviewer_id BIGINT NULL,
  reviewed_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_ai_product_profile_version (product_id, product_version),
  KEY idx_ai_profile_category_status (category_id, status, effective_from, effective_to),
  KEY idx_ai_profile_product_status (product_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_product_attribute_value (
  id BIGINT NOT NULL PRIMARY KEY,
  profile_version_id BIGINT NOT NULL,
  definition_id BIGINT NOT NULL,
  value_status VARCHAR(24) NOT NULL,
  value_code VARCHAR(80) NULL,
  value_number DECIMAL(20,6) NULL,
  value_min DECIMAL(20,6) NULL,
  value_max DECIMAL(20,6) NULL,
  source_ref VARCHAR(512) NULL,
  source_hash CHAR(64) NULL,
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_product_attr_value (profile_version_id, definition_id),
  KEY idx_ai_product_attr_definition (definition_id, value_code),
  CONSTRAINT fk_ai_product_attr_profile FOREIGN KEY (profile_version_id)
    REFERENCES tab_ai_product_profile_version (id),
  CONSTRAINT fk_ai_product_attr_definition FOREIGN KEY (definition_id)
    REFERENCES tab_ai_rec_attribute_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_product_attribute_multi_value (
  id BIGINT NOT NULL PRIMARY KEY,
  attribute_value_id BIGINT NOT NULL,
  value_code VARCHAR(80) NOT NULL,
  create_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_product_attr_multi_value (attribute_value_id, value_code),
  KEY idx_ai_product_attr_multi_code (value_code, attribute_value_id),
  CONSTRAINT fk_ai_product_multi_value FOREIGN KEY (attribute_value_id)
    REFERENCES tab_ai_product_attribute_value (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_rule_set (
  id BIGINT NOT NULL PRIMARY KEY,
  category_id BIGINT NOT NULL,
  rule_set_version INT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  score_strategy VARCHAR(24) NOT NULL DEFAULT 'SUM',
  effective_from DATETIME(3) NULL,
  effective_to DATETIME(3) NULL,
  reviewer_id BIGINT NULL,
  reviewed_at DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  create_by BIGINT NULL,
  update_by BIGINT NULL,
  data_state CHAR(1) NOT NULL DEFAULT '0',
  UNIQUE KEY uk_ai_rec_rule_set_version (category_id, rule_set_version),
  KEY idx_ai_rec_rule_set_status (category_id, status, effective_from, effective_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_rule (
  id BIGINT NOT NULL PRIMARY KEY,
  rule_set_id BIGINT NOT NULL,
  definition_id BIGINT NOT NULL,
  rule_mode VARCHAR(24) NOT NULL,
  required_for_clarification TINYINT(1) NOT NULL DEFAULT 0,
  score_weight INT NOT NULL DEFAULT 0,
  priority INT NOT NULL DEFAULT 0,
  reason_code VARCHAR(80) NOT NULL,
  reason_message VARCHAR(255) NOT NULL,
  missing_policy VARCHAR(24) NOT NULL DEFAULT 'INCOMPLETE',
  create_time DATETIME(3) NOT NULL,
  update_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_rec_rule_attribute (rule_set_id, definition_id),
  KEY idx_ai_rec_rule_order (rule_set_id, priority, id),
  CONSTRAINT fk_ai_rec_rule_set FOREIGN KEY (rule_set_id)
    REFERENCES tab_ai_recommendation_rule_set (id),
  CONSTRAINT fk_ai_rec_rule_definition FOREIGN KEY (definition_id)
    REFERENCES tab_ai_rec_attribute_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_catalog_snapshot (
  id BIGINT NOT NULL PRIMARY KEY,
  category_id BIGINT NOT NULL,
  catalog_version VARCHAR(64) NOT NULL,
  rule_set_id BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  create_time DATETIME(3) NOT NULL,
  published_at DATETIME(3) NULL,
  create_by BIGINT NULL,
  UNIQUE KEY uk_ai_rec_snapshot_category_version (category_id, catalog_version),
  KEY idx_ai_rec_snapshot_category_status (category_id, status, published_at),
  CONSTRAINT fk_ai_rec_snapshot_rule_set FOREIGN KEY (rule_set_id)
    REFERENCES tab_ai_recommendation_rule_set (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_snapshot_attribute (
  id BIGINT NOT NULL PRIMARY KEY,
  snapshot_id BIGINT NOT NULL,
  definition_id BIGINT NOT NULL,
  create_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_rec_snapshot_attribute (snapshot_id, definition_id),
  CONSTRAINT fk_ai_rec_snapshot_attr_snapshot FOREIGN KEY (snapshot_id)
    REFERENCES tab_ai_recommendation_catalog_snapshot (id),
  CONSTRAINT fk_ai_rec_snapshot_attr_definition FOREIGN KEY (definition_id)
    REFERENCES tab_ai_rec_attribute_definition (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_snapshot_product (
  id BIGINT NOT NULL PRIMARY KEY,
  snapshot_id BIGINT NOT NULL,
  profile_version_id BIGINT NOT NULL,
  create_time DATETIME(3) NOT NULL,
  UNIQUE KEY uk_ai_rec_snapshot_product (snapshot_id, profile_version_id),
  KEY idx_ai_rec_snapshot_profile (profile_version_id, snapshot_id),
  CONSTRAINT fk_ai_rec_snapshot_product_snapshot FOREIGN KEY (snapshot_id)
    REFERENCES tab_ai_recommendation_catalog_snapshot (id),
  CONSTRAINT fk_ai_rec_snapshot_product_profile FOREIGN KEY (profile_version_id)
    REFERENCES tab_ai_product_profile_version (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_catalog_current (
  category_id BIGINT NOT NULL PRIMARY KEY,
  snapshot_id BIGINT NOT NULL,
  update_time DATETIME(3) NOT NULL,
  update_by BIGINT NULL,
  UNIQUE KEY uk_ai_rec_current_snapshot (snapshot_id),
  CONSTRAINT fk_ai_rec_current_snapshot FOREIGN KEY (snapshot_id)
    REFERENCES tab_ai_recommendation_catalog_snapshot (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tab_ai_recommendation_audit_log (
  id BIGINT NOT NULL PRIMARY KEY,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  target_version VARCHAR(64) NULL,
  action_code VARCHAR(24) NOT NULL,
  from_status VARCHAR(24) NULL,
  to_status VARCHAR(24) NULL,
  actor_id BIGINT NOT NULL,
  action_reason VARCHAR(1000) NULL,
  content_hash CHAR(64) NULL,
  correlation_id CHAR(36) NOT NULL,
  occurred_at DATETIME(3) NOT NULL,
  KEY idx_ai_rec_audit_target (target_type, target_id, occurred_at),
  KEY idx_ai_rec_audit_actor (actor_id, occurred_at),
  KEY idx_ai_rec_audit_correlation (correlation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
