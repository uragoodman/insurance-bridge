ALTER TABLE tab_insurance
  ADD COLUMN ai_recommend_enabled TINYINT(1) NOT NULL DEFAULT 0
  COMMENT 'Whether this on-sale insurance product is available to the AI Agent recommendation catalog';

CREATE INDEX idx_insurance_ai_recommend
  ON tab_insurance (ai_recommend_enabled, insurance_state, data_state, category_no, sort_no);
