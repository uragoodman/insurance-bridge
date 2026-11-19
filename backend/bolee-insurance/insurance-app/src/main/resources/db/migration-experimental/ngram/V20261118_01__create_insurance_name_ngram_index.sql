-- Additive, manually activated search accelerator for exact-substring product-name searches.
-- The application keeps the existing LIKE predicate as the final semantic check.
CREATE TABLE tab_insurance_name_ngram (
  insurance_id BIGINT NOT NULL,
  ngram VARBINARY(8) NOT NULL,
  PRIMARY KEY (insurance_id, ngram),
  KEY idx_ins_name_ngram_lookup (ngram, insurance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
