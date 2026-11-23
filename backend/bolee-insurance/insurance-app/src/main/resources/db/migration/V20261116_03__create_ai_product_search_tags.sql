CREATE TABLE tab_insurance_ai_search_tag_set (
  id BIGINT NOT NULL COMMENT 'Snowflake ID',
  insurance_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  status VARCHAR(16) NOT NULL COMMENT 'ACTIVE or ARCHIVED',
  taxonomy_version VARCHAR(32) NOT NULL DEFAULT 'v1',
  source_type VARCHAR(16) NOT NULL COMMENT 'MANUAL or AI',
  model_id VARCHAR(128) DEFAULT NULL,
  prompt_version VARCHAR(32) DEFAULT NULL,
  create_by BIGINT DEFAULT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_search_tag_version (insurance_id, version_no),
  KEY idx_ai_search_tag_active (insurance_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Versioned AI insurance product search tag sets';

CREATE TABLE tab_insurance_ai_search_tag (
  id BIGINT NOT NULL COMMENT 'Snowflake ID',
  tag_set_id BIGINT NOT NULL,
  tag_type VARCHAR(24) NOT NULL COMMENT 'PRODUCT_TYPE, AUDIENCE, SCENARIO, KEYWORD, or manually reviewed ALIAS',
  tag_value VARCHAR(128) NOT NULL,
  normalized_value VARCHAR(128) NOT NULL,
  evidence VARCHAR(512) DEFAULT NULL,
  confidence DECIMAL(5,4) DEFAULT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_ai_search_tag_value (tag_set_id, tag_type, normalized_value),
  KEY idx_ai_search_tag_lookup (normalized_value, tag_set_id),
  CONSTRAINT fk_ai_search_tag_set FOREIGN KEY (tag_set_id)
    REFERENCES tab_insurance_ai_search_tag_set (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Normalized AI insurance product search tags';
