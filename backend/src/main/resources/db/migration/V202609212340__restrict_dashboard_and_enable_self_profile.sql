DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id=rp.role_id AND r.code<>'SYSTEM_ADMIN'
JOIN sys_permission p ON p.id=rp.permission_id AND p.code='page:home';

INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.id,p.id
FROM sys_role r
JOIN sys_permission p ON p.code='page:user-management' AND p.enabled=1
WHERE r.code IN ('PROJECT_TECH_LEADER','RESEARCH_ASSISTANT','INTERNAL_TOPIC_UNIT','EXTERNAL_TOPIC_UNIT');

UPDATE sys_user u
JOIN sys_user_role ur ON ur.user_id=u.id
JOIN sys_role r ON r.id=ur.role_id AND r.code<>'SYSTEM_ADMIN'
SET u.token_version=u.token_version+1,u.updated_at=NOW(3)
WHERE u.deleted_at IS NULL;
