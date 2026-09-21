CREATE TABLE system_error_log (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  trace_id VARCHAR(64) NOT NULL,
  severity VARCHAR(16) NOT NULL,
  user_id BIGINT UNSIGNED NULL,
  username VARCHAR(100) NULL,
  http_method VARCHAR(12) NOT NULL,
  request_path VARCHAR(500) NOT NULL,
  status_code INT NOT NULL,
  error_code VARCHAR(100) NULL,
  error_message VARCHAR(1000) NULL,
  exception_class VARCHAR(255) NULL,
  stack_summary TEXT NULL,
  duration_ms BIGINT UNSIGNED NOT NULL,
  client_ip VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  KEY idx_system_error_created (created_at),
  KEY idx_system_error_trace (trace_id),
  KEY idx_system_error_status (status_code, created_at),
  KEY idx_system_error_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission(code,name,permission_type,permission_group,locked_for_external,enabled)
VALUES('page:system-log','系统错误日志','PAGE','系统管理',1,1)
ON DUPLICATE KEY UPDATE name=VALUES(name), enabled=1;

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT role.id, permission.id FROM sys_role role
JOIN sys_permission permission ON permission.code='page:system-log'
WHERE role.code='SYSTEM_ADMIN';
