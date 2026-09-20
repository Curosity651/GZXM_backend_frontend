ALTER TABLE sys_user
  DROP INDEX uk_one_active_topic_account_per_unit,
  DROP COLUMN active_topic_unit_key;

CREATE TABLE biz_topic_user_assignment (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  membership_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  enabled TINYINT(1) NOT NULL DEFAULT 1,
  created_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_topic_membership_user (membership_id, user_id),
  KEY idx_topic_user_assignment_user (user_id, enabled),
  CONSTRAINT fk_topic_user_assignment_membership FOREIGN KEY (membership_id)
    REFERENCES biz_topic_unit_membership(id),
  CONSTRAINT fk_topic_user_assignment_user FOREIGN KEY (user_id)
    REFERENCES sys_user(id),
  CONSTRAINT fk_topic_user_assignment_created_by FOREIGN KEY (created_by)
    REFERENCES sys_user(id),
  CONSTRAINT fk_topic_user_assignment_updated_by FOREIGN KEY (updated_by)
    REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
