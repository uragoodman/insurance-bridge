-- Keep per-gram document frequency separate from postings so query-time rare-gram
-- selection probes only the requested grams instead of scanning the postings index.
CREATE TABLE tab_insurance_name_ngram_stat (
  ngram VARBINARY(8) NOT NULL,
  product_count INT UNSIGNED NOT NULL,
  PRIMARY KEY (ngram)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
