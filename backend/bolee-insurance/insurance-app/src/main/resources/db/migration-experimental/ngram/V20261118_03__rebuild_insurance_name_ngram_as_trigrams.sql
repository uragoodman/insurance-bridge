-- The first isolated prototype used bigrams. Replace its rebuildable derived data
-- with trigrams before the read feature is enabled; short terms keep using LIKE.
DROP TABLE tab_insurance_name_ngram_stat;
DROP TABLE tab_insurance_name_ngram;

CREATE TABLE tab_insurance_name_ngram (
  insurance_id BIGINT NOT NULL,
  ngram VARBINARY(12) NOT NULL,
  PRIMARY KEY (insurance_id, ngram),
  KEY idx_ins_name_ngram_lookup (ngram, insurance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE tab_insurance_name_ngram_stat (
  ngram VARBINARY(12) NOT NULL,
  product_count INT UNSIGNED NOT NULL,
  PRIMARY KEY (ngram)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
