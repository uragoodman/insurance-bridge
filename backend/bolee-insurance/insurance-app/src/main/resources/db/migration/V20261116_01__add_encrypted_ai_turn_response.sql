ALTER TABLE tab_ai_turn
  ADD COLUMN encrypted_response LONGTEXT NULL AFTER agno_run_id;
