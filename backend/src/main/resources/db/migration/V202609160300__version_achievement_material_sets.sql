ALTER TABLE achievement_material
  ADD COLUMN active TINYINT(1) NOT NULL DEFAULT 1,
  DROP INDEX uk_achievement_material_version,
  ADD UNIQUE KEY uk_achievement_material_set(achievement_id,material_type,file_version,file_id);

-- Keep only the latest legacy version of each material type active; retain every old association.
UPDATE achievement_material m
JOIN (SELECT achievement_id,material_type,MAX(file_version) latest FROM achievement_material GROUP BY achievement_id,material_type) latest
ON m.achievement_id=latest.achievement_id AND m.material_type=latest.material_type
SET m.active=(m.file_version=latest.latest);
