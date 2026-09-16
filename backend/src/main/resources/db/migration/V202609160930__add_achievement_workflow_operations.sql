-- B-specific successful operation replay. Does not replace A's shared idempotency service.
CREATE TABLE achievement_workflow_operation (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  achievement_id BIGINT UNSIGNED NOT NULL,
  actor_id BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  operation_kind VARCHAR(16) NOT NULL,
  request_json JSON NOT NULL,
  response_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_achievement_operation_actor_key(actor_id,request_key),
  KEY idx_achievement_operation_record(achievement_id),
  CONSTRAINT fk_achievement_operation_record FOREIGN KEY(achievement_id) REFERENCES achievement(id),
  CHECK(operation_kind IN ('ACTION','REVIEW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
