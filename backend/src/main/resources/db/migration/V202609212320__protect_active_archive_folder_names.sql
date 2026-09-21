ALTER TABLE archive_folder
  ADD COLUMN active_name VARCHAR(300)
    GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN name ELSE NULL END) STORED,
  ADD UNIQUE KEY uk_archive_active_folder_name (owner_type, owner_id, unit_id, active_name);
