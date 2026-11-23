-- Local development fixture only. Run manually after Flyway migration V20261115.01.
-- This publishes one test snapshot from existing legacy records; it is not a
-- production approval of policy wording or a substitute for insurer documents.
-- Product: 平安境内旅游险 (tab_insurance.id=1724268602804146177)
-- Category: 境内旅游 (tab_category.id=1715259018106130434)
-- Source rows: tab_insurance_condition 1727938025670914050-4052,
-- tab_insurance_plan 1727937288823975938,
-- tab_plan_safeguard 1727937288840753153, 1727937288849141761,
-- 1727937288857530370-371; allowed values from tab_condition SX004/SX005/SX006.

START TRANSACTION;

INSERT INTO tab_ai_rec_attribute_definition (
  id, category_id, attribute_code, definition_version, display_name, value_type, unit_code,
  sensitivity_level, ai_enabled, allow_hard_filter, allow_preference, allow_compare,
  missing_policy, status, reviewer_id, reviewed_at, effective_from,
  create_time, update_time, create_by, update_by, data_state
) VALUES
  (1900000000000000001, 1715259018106130434, 'trip_duration_days', 1, '旅行保障天数', 'NUMBER_RANGE', 'DAY', 'NONE', 1, 1, 0, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0'),
  (1900000000000000002, 1715259018106130434, 'transport_modes', 1, '交通方式', 'MULTI_ENUM', NULL, 'NONE', 1, 1, 0, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0'),
  (1900000000000000003, 1715259018106130434, 'service_options', 1, '特色服务', 'MULTI_ENUM', NULL, 'NONE', 1, 0, 1, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0'),
  (1900000000000000004, 1715259018106130434, 'daily_premium_cny', 1, '每日保费', 'NUMBER', 'CNY_PER_DAY', 'NONE', 1, 0, 0, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0'),
  (1900000000000000005, 1715259018106130434, 'accidental_death_disability_cny', 1, '意外身故伤残保额', 'NUMBER', 'CNY', 'NONE', 1, 0, 0, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0'),
  (1900000000000000006, 1715259018106130434, 'accident_medical_cny', 1, '意外医疗额度', 'NUMBER', 'CNY', 'NONE', 1, 0, 0, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0'),
  (1900000000000000007, 1715259018106130434, 'inpatient_allowance_cny_per_day', 1, '意外住院津贴', 'NUMBER', 'CNY_PER_DAY', 'NONE', 1, 0, 0, 1, 'INCOMPLETE', 'PUBLISHED', 0, NOW(3), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3), 0, 0, '0')
ON DUPLICATE KEY UPDATE
  display_name = VALUES(display_name), value_type = VALUES(value_type), unit_code = VALUES(unit_code),
  sensitivity_level = VALUES(sensitivity_level), ai_enabled = VALUES(ai_enabled),
  allow_hard_filter = VALUES(allow_hard_filter), allow_preference = VALUES(allow_preference),
  allow_compare = VALUES(allow_compare), missing_policy = VALUES(missing_policy),
  status = VALUES(status), reviewer_id = VALUES(reviewer_id), reviewed_at = VALUES(reviewed_at),
  effective_from = VALUES(effective_from), update_time = VALUES(update_time), data_state = VALUES(data_state);

INSERT INTO tab_ai_rec_attribute_operator (id, definition_id, operator_code, create_time) VALUES
  (1900000000000000601, 1900000000000000001, 'RANGE', NOW(3)),
  (1900000000000000602, 1900000000000000002, 'IN', NOW(3)),
  (1900000000000000603, 1900000000000000003, 'IN', NOW(3))
ON DUPLICATE KEY UPDATE operator_code = VALUES(operator_code);

INSERT INTO tab_ai_rec_attribute_option (id, definition_id, value_code, display_name, sort_no, data_state, create_time, update_time) VALUES
  (1900000000000000701, 1900000000000000002, 'SX00501', '飞机', 1, '0', NOW(3), NOW(3)),
  (1900000000000000702, 1900000000000000002, 'SX00502', '私家车', 2, '0', NOW(3), NOW(3)),
  (1900000000000000703, 1900000000000000002, 'SX00503', '网约车/出租车', 3, '0', NOW(3), NOW(3)),
  (1900000000000000704, 1900000000000000002, 'SX00504', '火车/地铁', 4, '0', NOW(3), NOW(3)),
  (1900000000000000705, 1900000000000000002, 'SX00505', '电瓶车', 5, '0', NOW(3), NOW(3)),
  (1900000000000000706, 1900000000000000002, 'SX00506', '其他', 6, '0', NOW(3), NOW(3)),
  (1900000000000000707, 1900000000000000003, 'SX00401', '医疗费用垫付', 1, '0', NOW(3), NOW(3)),
  (1900000000000000708, 1900000000000000003, 'SX00403', '含问诊服务', 2, '0', NOW(3), NOW(3)),
  (1900000000000000709, 1900000000000000003, 'SX00404', '支持就医绿通', 3, '0', NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name), sort_no = VALUES(sort_no), data_state = VALUES(data_state), update_time = VALUES(update_time);

INSERT INTO tab_ai_product_profile_version (
  id, product_id, product_name_snapshot, sort_no, product_version, category_id, status,
  effective_from, source_type, source_ref, source_hash, reviewer_id, reviewed_at,
  create_time, update_time, create_by, update_by, data_state
) VALUES (
  1900000000000000201, 1724268602804146177, '平安境内旅游险', 1, 1, 1715259018106130434, 'PUBLISHED',
  NOW(3) - INTERVAL 1 MINUTE, 'LOCAL_DEV_TEST',
  'tab_insurance:1724268602804146177;tab_insurance_condition:1727938025670914050-4052;tab_insurance_plan:1727937288823975938;tab_plan_safeguard:1727937288840753153,1727937288849141761,1727937288857530370-371',
  SHA2('LOCAL_DEV_TEST|1724268602804146177|1727938025670914050-4052|1727937288823975938|1727937288840753153,1727937288849141761,1727937288857530370-371', 256),
  0, NOW(3), NOW(3), NOW(3), 0, 0, '0'
)
ON DUPLICATE KEY UPDATE product_name_snapshot = VALUES(product_name_snapshot), sort_no = VALUES(sort_no),
  category_id = VALUES(category_id), status = VALUES(status), effective_from = VALUES(effective_from),
  source_type = VALUES(source_type), source_ref = VALUES(source_ref), source_hash = VALUES(source_hash),
  reviewer_id = VALUES(reviewer_id), reviewed_at = VALUES(reviewed_at), update_time = VALUES(update_time),
  data_state = VALUES(data_state);

INSERT INTO tab_ai_product_attribute_value (
  id, profile_version_id, definition_id, value_status, value_code, value_number, value_min, value_max,
  source_ref, source_hash, effective_from, create_time, update_time
) VALUES
  (1900000000000000501, 1900000000000000201, 1900000000000000001, 'KNOWN', NULL, NULL, 1, 7, 'tab_insurance_condition:1727938025670914050;tab_condition:SX006', SHA2('SX00601,SX00602,SX00603,SX00604,SX00605,SX00606,SX00607', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3)),
  (1900000000000000502, 1900000000000000201, 1900000000000000002, 'KNOWN', NULL, NULL, NULL, NULL, 'tab_insurance_condition:1727938025670914051;tab_condition:SX005', SHA2('SX00501,SX00502,SX00503,SX00504,SX00505,SX00506', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3)),
  (1900000000000000503, 1900000000000000201, 1900000000000000003, 'KNOWN', NULL, NULL, NULL, NULL, 'tab_insurance_condition:1727938025670914052;tab_condition:SX004', SHA2('SX00401,SX00403,SX00404', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3)),
  (1900000000000000504, 1900000000000000201, 1900000000000000004, 'KNOWN', NULL, 2.90, NULL, NULL, 'tab_insurance_plan:1727937288823975938.price', SHA2('1727937288823975938|2.90|DAY', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3)),
  (1900000000000000505, 1900000000000000201, 1900000000000000005, 'KNOWN', NULL, 200000, NULL, NULL, 'tab_plan_safeguard:1727937288857530371.safeguard_value', SHA2('1727937288857530371|200000|意外身故残疾', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3)),
  (1900000000000000506, 1900000000000000201, 1900000000000000006, 'KNOWN', NULL, 20000, NULL, NULL, 'tab_plan_safeguard:1727937288849141761.safeguard_value', SHA2('1727937288849141761|20000|意外医疗补贴', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3)),
  (1900000000000000507, 1900000000000000201, 1900000000000000007, 'KNOWN', NULL, 30, NULL, NULL, 'tab_plan_safeguard:1727937288840753153.safeguard_value', SHA2('1727937288840753153|30|意外住院津贴|DAY', 256), NOW(3) - INTERVAL 1 MINUTE, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE value_status = VALUES(value_status), value_code = VALUES(value_code),
  value_number = VALUES(value_number), value_min = VALUES(value_min), value_max = VALUES(value_max),
  source_ref = VALUES(source_ref), source_hash = VALUES(source_hash), effective_from = VALUES(effective_from),
  update_time = VALUES(update_time);

INSERT INTO tab_ai_product_attribute_multi_value (id, attribute_value_id, value_code, create_time) VALUES
  (1900000000000000901, 1900000000000000502, 'SX00501', NOW(3)),
  (1900000000000000902, 1900000000000000502, 'SX00502', NOW(3)),
  (1900000000000000903, 1900000000000000502, 'SX00503', NOW(3)),
  (1900000000000000904, 1900000000000000502, 'SX00504', NOW(3)),
  (1900000000000000905, 1900000000000000502, 'SX00505', NOW(3)),
  (1900000000000000906, 1900000000000000502, 'SX00506', NOW(3)),
  (1900000000000000907, 1900000000000000503, 'SX00401', NOW(3)),
  (1900000000000000908, 1900000000000000503, 'SX00403', NOW(3)),
  (1900000000000000909, 1900000000000000503, 'SX00404', NOW(3))
ON DUPLICATE KEY UPDATE value_code = VALUES(value_code);

INSERT INTO tab_ai_recommendation_rule_set (
  id, category_id, rule_set_version, status, score_strategy, reviewer_id, reviewed_at,
  create_time, update_time, create_by, update_by, data_state
) VALUES (1900000000000000101, 1715259018106130434, 1, 'PUBLISHED', 'SUM', 0, NOW(3), NOW(3), NOW(3), 0, 0, '0')
ON DUPLICATE KEY UPDATE status = VALUES(status), score_strategy = VALUES(score_strategy),
  reviewer_id = VALUES(reviewer_id), reviewed_at = VALUES(reviewed_at), update_time = VALUES(update_time), data_state = VALUES(data_state);

INSERT INTO tab_ai_recommendation_rule (
  id, rule_set_id, definition_id, rule_mode, required_for_clarification, score_weight, priority,
  reason_code, reason_message, missing_policy, create_time, update_time
) VALUES
  (1900000000000000401, 1900000000000000101, 1900000000000000001, 'HARD_FILTER', 1, 0, 1, 'TRIP_DURATION_SUPPORTED', '产品支持所选保障天数（1至7天）。', 'INCOMPLETE', NOW(3), NOW(3)),
  (1900000000000000402, 1900000000000000101, 1900000000000000002, 'HARD_FILTER', 0, 0, 2, 'TRANSPORT_SUPPORTED', '产品支持所选交通方式。', 'INCOMPLETE', NOW(3), NOW(3)),
  (1900000000000000403, 1900000000000000101, 1900000000000000003, 'PREFERENCE_SCORE', 0, 20, 3, 'SERVICE_PREFERENCE', '产品包含所选特色服务。', 'INCOMPLETE', NOW(3), NOW(3)),
  (1900000000000000404, 1900000000000000101, 1900000000000000004, 'COMPARE_ONLY', 0, 0, 4, 'DAILY_PREMIUM', '每日保费来自当前默认方案。', 'INCOMPLETE', NOW(3), NOW(3)),
  (1900000000000000405, 1900000000000000101, 1900000000000000005, 'COMPARE_ONLY', 0, 0, 5, 'DEATH_DISABILITY_COVER', '意外身故伤残额度来自当前默认方案。', 'INCOMPLETE', NOW(3), NOW(3)),
  (1900000000000000406, 1900000000000000101, 1900000000000000006, 'COMPARE_ONLY', 0, 0, 6, 'ACCIDENT_MEDICAL_COVER', '意外医疗额度来自当前默认方案。', 'INCOMPLETE', NOW(3), NOW(3)),
  (1900000000000000407, 1900000000000000101, 1900000000000000007, 'COMPARE_ONLY', 0, 0, 7, 'INPATIENT_ALLOWANCE', '意外住院津贴来自当前默认方案。', 'INCOMPLETE', NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE rule_mode = VALUES(rule_mode), required_for_clarification = VALUES(required_for_clarification),
  score_weight = VALUES(score_weight), priority = VALUES(priority), reason_code = VALUES(reason_code),
  reason_message = VALUES(reason_message), missing_policy = VALUES(missing_policy), update_time = VALUES(update_time);

INSERT INTO tab_ai_recommendation_catalog_snapshot (
  id, category_id, catalog_version, rule_set_id, status, create_time, published_at, create_by
) VALUES (1900000000000000301, 1715259018106130434, 'LOCAL-DEV-TRAVEL-2026-11-15', 1900000000000000101, 'PUBLISHED', NOW(3), NOW(3), 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), published_at = VALUES(published_at);

INSERT INTO tab_ai_recommendation_snapshot_attribute (id, snapshot_id, definition_id, create_time) VALUES
  (1900000000000000801, 1900000000000000301, 1900000000000000001, NOW(3)),
  (1900000000000000802, 1900000000000000301, 1900000000000000002, NOW(3)),
  (1900000000000000803, 1900000000000000301, 1900000000000000003, NOW(3)),
  (1900000000000000804, 1900000000000000301, 1900000000000000004, NOW(3)),
  (1900000000000000805, 1900000000000000301, 1900000000000000005, NOW(3)),
  (1900000000000000806, 1900000000000000301, 1900000000000000006, NOW(3)),
  (1900000000000000807, 1900000000000000301, 1900000000000000007, NOW(3))
ON DUPLICATE KEY UPDATE definition_id = VALUES(definition_id);

INSERT INTO tab_ai_recommendation_snapshot_product (id, snapshot_id, profile_version_id, create_time)
VALUES (1900000000000000808, 1900000000000000301, 1900000000000000201, NOW(3))
ON DUPLICATE KEY UPDATE profile_version_id = VALUES(profile_version_id);

INSERT INTO tab_ai_recommendation_catalog_current (category_id, snapshot_id, update_time, update_by)
VALUES (1715259018106130434, 1900000000000000301, NOW(3), 0)
ON DUPLICATE KEY UPDATE snapshot_id = VALUES(snapshot_id), update_time = VALUES(update_time), update_by = VALUES(update_by);

INSERT INTO tab_ai_recommendation_audit_log (
  id, target_type, target_id, target_version, action_code, from_status, to_status,
  actor_id, action_reason, content_hash, correlation_id, occurred_at
) VALUES (
  1900000000000001001, 'CATALOG_SNAPSHOT', 1900000000000000301, 'LOCAL-DEV-TRAVEL-2026-11-15',
  'DEV_TEST_PUBLISH', 'DRAFT', 'PUBLISHED', 0,
  'Local development test fixture sourced from existing bolee-insurance product, option, plan, and safeguard rows; not production approval.',
  SHA2('LOCAL-DEV-TRAVEL-2026-11-15|1724268602804146177|1727937288823975938', 256), UUID(), NOW(3)
)
ON DUPLICATE KEY UPDATE action_reason = VALUES(action_reason), occurred_at = VALUES(occurred_at);

COMMIT;
