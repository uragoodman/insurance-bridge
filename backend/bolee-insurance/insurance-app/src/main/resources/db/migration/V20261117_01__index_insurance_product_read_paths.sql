-- Support the existing batch lookups used to assemble insurance product responses.
-- Keep the leading columns aligned with the equality/IN predicates in the services.
ALTER TABLE tab_insurance_condition
  ADD INDEX idx_ins_condition_product_state_time (insurance_id, data_state, create_time);

ALTER TABLE tab_insurance_coefficent
  ADD INDEX idx_ins_coeff_product_state_sort (insurance_id, data_state, sort_no);

ALTER TABLE tab_insurance_plan
  ADD INDEX idx_ins_plan_product_state_time (insurance_id, data_state, create_time);

ALTER TABLE tab_plan_safeguard
  ADD INDEX idx_plan_safeguard_plan_id (plan_id);

ALTER TABLE tab_plan_earnings
  ADD INDEX idx_plan_earnings_plan_id (paln_id);
