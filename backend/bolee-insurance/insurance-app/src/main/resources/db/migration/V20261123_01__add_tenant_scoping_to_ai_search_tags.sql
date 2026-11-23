ALTER TABLE tab_insurance_ai_search_tag_set
  ADD COLUMN company_no VARCHAR(64) NULL AFTER id;

UPDATE tab_insurance_ai_search_tag_set tag_set
JOIN tab_insurance insurance ON insurance.id = tag_set.insurance_id
SET tag_set.company_no = insurance.company_no;

ALTER TABLE tab_insurance_ai_search_tag_set
  MODIFY COLUMN company_no VARCHAR(64) NOT NULL,
  ADD KEY idx_ai_search_tag_set_tenant_active (company_no, insurance_id, status);

ALTER TABLE tab_insurance_ai_search_tag
  ADD COLUMN company_no VARCHAR(64) NULL AFTER id;

UPDATE tab_insurance_ai_search_tag tag
JOIN tab_insurance_ai_search_tag_set tag_set ON tag_set.id = tag.tag_set_id
SET tag.company_no = tag_set.company_no;

ALTER TABLE tab_insurance_ai_search_tag
  MODIFY COLUMN company_no VARCHAR(64) NOT NULL,
  ADD KEY idx_ai_search_tag_tenant_lookup (company_no, normalized_value, tag_set_id);
