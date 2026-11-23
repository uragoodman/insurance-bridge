-- Local resume-demo fixtures. These are explicitly SYNTHETIC and are not carrier terms.
-- The source IDs match the isolated synthetic pgvector indexes created for this checkout.
-- Do not copy this fixture into a production database.

START TRANSACTION;

UPDATE tab_insurance
SET ai_recommend_enabled = 1
WHERE id IN (
  1722515895592460289,
  1721445057887768578,
  1724268602804146177
);

INSERT INTO tab_insurance_term_source (
  id, company_no, insurance_id, profile_version_id, product_version, source_type, title,
  source_uri, original_file_name, content_type, file_size, sha256, storage_provider,
  bucket_name, object_key, status, effective_from, effective_to, uploaded_by,
  create_time, update_time, create_by, update_by, data_state, submitted_by,
  submitted_at, reviewed_by, reviewed_at, review_note, published_by, published_at,
  index_status, indexed_sha256, index_error
) VALUES
  (8971369031322252601, '100000001', 1721437008821260290, 1900000000000000202, 1,
   'SYNTHETIC', 'SIMULATED / DEMO_ONLY — 无忧综合意外险（RAG 演示稿）', NULL,
   'insurance-1721437008821260290-v1.md', 'text/markdown; charset=utf-8', 10860,
   '347771d9d75c4c3b3645657b1f603afa712ec067cdf49e35ed1d48a1eb096ebf', 'MINIO',
   'insurance-source-files', 'synthetic/demo/insurance-1721437008821260290-v1.md',
   'PUBLISHED', NULL, NULL, 1371500419615895600, NOW(3), NOW(3), 1371500419615895600,
   1371500419615895600, '0', 1371500419615895600, NOW(3), 1371500419615895600,
   NOW(3), 'Local demo fixture; synthetic wording, not official carrier terms.',
   1371500419615895600, NOW(3), 'INDEXED',
   '347771d9d75c4c3b3645657b1f603afa712ec067cdf49e35ed1d48a1eb096ebf', NULL),
  (8900000000092460289, '100000001', 1722515895592460289, 1900000000000000203, 1,
   'SYNTHETIC', 'SIMULATED / DEMO_ONLY — 家无忧·家庭意外险（RAG 演示稿）', NULL,
   'insurance-1722515895592460289-v1.md', 'text/markdown; charset=utf-8', 3286,
   '060526c0a4b71c36fca90b88d583bc5f48f9eda60c942d526f5f78f1f9760c56', 'MINIO',
   'insurance-source-files', 'synthetic/demo/insurance-1722515895592460289-v1.md',
   'PUBLISHED', NULL, NULL, 1371500419615895600, NOW(3), NOW(3), 1371500419615895600,
   1371500419615895600, '0', 1371500419615895600, NOW(3), 1371500419615895600,
   NOW(3), 'Local demo fixture; synthetic wording, not official carrier terms.',
   1371500419615895600, NOW(3), 'INDEXED',
   '060526c0a4b71c36fca90b88d583bc5f48f9eda60c942d526f5f78f1f9760c56', NULL),
  (8900000000087768578, '100000001', 1721445057887768578, 1900000000000000204, 1,
   'SYNTHETIC', 'SIMULATED / DEMO_ONLY — 中老年人意外保险（RAG 演示稿）', NULL,
   'insurance-1721445057887768578-v1.md', 'text/markdown; charset=utf-8', 3392,
   '2f45a4ba7b80124f723145a83cf6c1534cbbE25a2c5dfe7d86b4b33be889e420', 'MINIO',
   'insurance-source-files', 'synthetic/demo/insurance-1721445057887768578-v1.md',
   'PUBLISHED', NULL, NULL, 1371500419615895600, NOW(3), NOW(3), 1371500419615895600,
   1371500419615895600, '0', 1371500419615895600, NOW(3), 1371500419615895600,
   NOW(3), 'Local demo fixture; synthetic wording, not official carrier terms.',
   1371500419615895600, NOW(3), 'INDEXED',
   '2f45a4ba7b80124f723145a83cf6c1534cbbE25a2c5dfe7d86b4b33be889e420', NULL),
  (8900000000004146177, '100000001', 1724268602804146177, 1900000000000000201, 1,
   'SYNTHETIC', 'SIMULATED / DEMO_ONLY — 平安境内旅游险（RAG 演示稿）', NULL,
   'insurance-1724268602804146177-v1.md', 'text/markdown; charset=utf-8', 3625,
   'c12c4ab541240c176c9125906ad73a3ec5dc0f692291e2294f629c2a8afdb475', 'MINIO',
   'insurance-source-files', 'synthetic/demo/insurance-1724268602804146177-v1.md',
   'PUBLISHED', NULL, NULL, 1371500419615895600, NOW(3), NOW(3), 1371500419615895600,
   1371500419615895600, '0', 1371500419615895600, NOW(3), 1371500419615895600,
   NOW(3), 'Local demo fixture; synthetic wording, not official carrier terms.',
   1371500419615895600, NOW(3), 'INDEXED',
   'c12c4ab541240c176c9125906ad73a3ec5dc0f692291e2294f629c2a8afdb475', NULL)
ON DUPLICATE KEY UPDATE
  company_no = VALUES(company_no), insurance_id = VALUES(insurance_id),
  profile_version_id = VALUES(profile_version_id), product_version = VALUES(product_version),
  source_type = VALUES(source_type), title = VALUES(title), source_uri = VALUES(source_uri),
  original_file_name = VALUES(original_file_name), content_type = VALUES(content_type),
  file_size = VALUES(file_size), sha256 = VALUES(sha256), storage_provider = VALUES(storage_provider),
  bucket_name = VALUES(bucket_name), object_key = VALUES(object_key), status = VALUES(status),
  effective_from = VALUES(effective_from), effective_to = VALUES(effective_to),
  update_time = VALUES(update_time), data_state = '0', index_status = VALUES(index_status),
  indexed_sha256 = VALUES(indexed_sha256), index_error = NULL;

INSERT INTO tab_insurance_term_source_audit (
  id, company_no, source_id, action, from_status, to_status, actor_id, note, create_time
) VALUES
  (8800000000000000001, '100000001', 8971369031322252601, 'LOCAL_DEMO_SEED', NULL, 'PUBLISHED',
   1371500419615895600, 'Local demo fixture; SYNTHETIC source; not an official terms approval.', NOW(3)),
  (8800000000000000002, '100000001', 8900000000092460289, 'LOCAL_DEMO_SEED', NULL, 'PUBLISHED',
   1371500419615895600, 'Local demo fixture; SYNTHETIC source; not an official terms approval.', NOW(3)),
  (8800000000000000003, '100000001', 8900000000087768578, 'LOCAL_DEMO_SEED', NULL, 'PUBLISHED',
   1371500419615895600, 'Local demo fixture; SYNTHETIC source; not an official terms approval.', NOW(3)),
  (8800000000000000004, '100000001', 8900000000004146177, 'LOCAL_DEMO_SEED', NULL, 'PUBLISHED',
   1371500419615895600, 'Local demo fixture; SYNTHETIC source; not an official terms approval.', NOW(3))
ON DUPLICATE KEY UPDATE note = VALUES(note), create_time = VALUES(create_time);

COMMIT;
