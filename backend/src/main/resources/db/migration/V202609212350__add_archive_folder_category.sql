ALTER TABLE archive_folder
  ADD COLUMN category_name VARCHAR(200) NOT NULL DEFAULT '其他重要材料' AFTER unit_id;
