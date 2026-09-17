ALTER TABLE progress_report
  ADD COLUMN basic_information TEXT NULL AFTER report_type,
  ADD COLUMN research_achievements TEXT NULL AFTER overall_progress;

UPDATE progress_report
SET basic_information = COALESCE(basic_information, ''),
    research_achievements = COALESCE(research_achievements, '');

ALTER TABLE progress_report
  MODIFY COLUMN basic_information TEXT NOT NULL,
  MODIFY COLUMN research_achievements TEXT NOT NULL;

INSERT INTO biz_project(code, name, description, start_date, end_date, enabled, record_version)
SELECT 'GZ-2025-001', '国家科技重大专项示范', '重点项目科研管理系统当前管理项目',
       '2025-01-01', '2028-12-31', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM biz_project WHERE enabled=1);

SET @gzxm_project_id = (SELECT id FROM biz_project WHERE enabled=1 ORDER BY id LIMIT 1);

INSERT INTO time_node(project_id, code, name, deadline, sort_order, enabled) VALUES
(@gzxm_project_id, 'YEAR_1', '第一年度', '2025-12-31', 1, 1),
(@gzxm_project_id, 'YEAR_2', '第二年度', '2026-12-31', 2, 1),
(@gzxm_project_id, 'MID_TERM', '中期检查', '2027-06-30', 3, 1),
(@gzxm_project_id, 'TRIAL_RUN', '系统试运行', '2027-12-31', 4, 1),
(@gzxm_project_id, 'FINAL', '项目结项', '2028-12-31', 5, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), deadline=VALUES(deadline), enabled=1;

INSERT INTO indicator_definition(code, name, achievement_type, category, unit_name, match_rule, built_in, enabled) VALUES
('PAPER', '学术论文', 'PAPER', 'BASE', '篇', NULL, 1, 1),
('PATENT', '发明专利', 'PATENT', 'BASE', '项', NULL, 1, 1),
('COPYRIGHT', '软件著作权', 'COPYRIGHT', 'BASE', '项', NULL, 1, 1),
('STANDARD', '标准规范', 'STANDARD', 'BASE', '项', NULL, 1, 1),
('TALENT', '人才培养', 'TALENT', 'BASE', '人', NULL, 1, 1),
('POWER_GRID_FIRST_AUTHOR_PAPER', '第一作者是广西电网的论文数量', 'PAPER', 'SPECIAL', '篇', JSON_OBJECT('field','isPowerGridFirstAuthor','equals',true), 1, 1),
('POWER_GRID_FIRST_APPLICANT_PATENT', '第一申请人是广西电网的专利数量', 'PATENT', 'SPECIAL', '项', JSON_OBJECT('field','isPowerGridFirstApplicant','equals',true), 1, 1),
('POWER_GRID_FIRST_COMPLETER_COPYRIGHT', '第一完成人是广西电网的软著数量', 'COPYRIGHT', 'SPECIAL', '项', JSON_OBJECT('field','isPowerGridFirstCompleter','equals',true), 1, 1),
('CHINESE_CORE_JOURNAL', '中文核心期刊的数量', 'PAPER', 'SPECIAL', '篇', JSON_OBJECT('field','isChineseCoreJournal','equals',true), 1, 1)
ON DUPLICATE KEY UPDATE name=VALUES(name), achievement_type=VALUES(achievement_type), category=VALUES(category),
                        unit_name=VALUES(unit_name), match_rule=VALUES(match_rule), built_in=1, enabled=1;
