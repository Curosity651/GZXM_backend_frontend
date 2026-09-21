ALTER TABLE sys_user
    CHANGE COLUMN contact_name principal_name VARCHAR(100) NOT NULL DEFAULT '';

ALTER TABLE sys_user
    ADD COLUMN principal_phone VARCHAR(32) NULL AFTER principal_name,
    ADD COLUMN principal_email VARCHAR(254) NULL AFTER principal_phone,
    ADD COLUMN contact_name VARCHAR(100) NULL AFTER principal_email;

UPDATE sys_user SET contact_name = principal_name WHERE contact_name IS NULL;

ALTER TABLE sys_user MODIFY COLUMN contact_name VARCHAR(100) NOT NULL;

UPDATE sys_permission
SET name = '维护课题参与单位'
WHERE code = 'topic-unit.manage';
