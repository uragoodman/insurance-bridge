-- Align insurance-term persistence with the shared company tenant interceptor.
-- Existing profile versions and term workflow rows inherit their tenant from
-- the owning insurance product/source before the new columns become required.

ALTER TABLE tab_ai_product_profile_version
  ADD COLUMN company_no VARCHAR(64) NULL AFTER product_id;

UPDATE tab_ai_product_profile_version p
JOIN tab_insurance ins ON ins.id = p.product_id
SET p.company_no = ins.company_no;

ALTER TABLE tab_ai_product_profile_version
  MODIFY COLUMN company_no VARCHAR(64) NOT NULL,
  ADD KEY idx_ai_product_profile_version_tenant_product (company_no, product_id, id);

ALTER TABLE tab_insurance_term_source
  ADD COLUMN company_no VARCHAR(64) NULL AFTER id;

UPDATE tab_insurance_term_source s
JOIN tab_insurance ins ON ins.id = s.insurance_id
SET s.company_no = ins.company_no;

ALTER TABLE tab_insurance_term_source
  MODIFY COLUMN company_no VARCHAR(64) NOT NULL,
  ADD KEY idx_insurance_term_source_tenant_list
    (company_no, data_state, insurance_id, profile_version_id, status, create_time);

ALTER TABLE tab_insurance_term_source_audit
  ADD COLUMN company_no VARCHAR(64) NULL AFTER id;

UPDATE tab_insurance_term_source_audit a
JOIN tab_insurance_term_source s ON s.id = a.source_id
SET a.company_no = s.company_no;

ALTER TABLE tab_insurance_term_source_audit
  MODIFY COLUMN company_no VARCHAR(64) NOT NULL,
  ADD KEY idx_insurance_term_source_audit_tenant_source
    (company_no, source_id, create_time, id);

ALTER TABLE tab_insurance_term_index_task
  ADD COLUMN company_no VARCHAR(64) NULL AFTER id;

UPDATE tab_insurance_term_index_task t
JOIN tab_insurance_term_source s ON s.id = t.source_id
SET t.company_no = s.company_no;

ALTER TABLE tab_insurance_term_index_task
  MODIFY COLUMN company_no VARCHAR(64) NOT NULL,
  ADD KEY idx_insurance_term_index_task_tenant_source (company_no, source_id);
