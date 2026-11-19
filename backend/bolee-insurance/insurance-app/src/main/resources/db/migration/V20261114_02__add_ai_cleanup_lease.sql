ALTER TABLE tab_ai_cleanup_task
  ADD COLUMN lease_until DATETIME(3) NULL AFTER last_attempt_at,
  ADD KEY idx_ai_cleanup_lease (status, lease_until);
