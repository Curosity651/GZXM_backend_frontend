-- Product decision: time nodes are configured by the research assistant and the initial list must be empty.
-- Only the five historical seed nodes are removed. A node referenced by an achievement is deliberately preserved.

DELETE item FROM unit_allocation_draft_item item
JOIN unit_allocation_draft draft ON draft.id = item.draft_id
JOIN time_node node ON node.id = draft.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE publication FROM unit_allocation_publication publication
JOIN time_node node ON node.id = publication.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE allocation FROM unit_indicator_allocation allocation
JOIN time_node node ON node.id = allocation.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE draft FROM unit_allocation_draft draft
JOIN time_node node ON node.id = draft.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE target FROM topic_indicator_draft_target target
JOIN topic_indicator_draft draft ON draft.id = target.draft_id
JOIN time_node node ON node.id = draft.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE publication FROM topic_indicator_publication publication
JOIN time_node node ON node.id = publication.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE indicator_row FROM topic_indicator indicator_row
JOIN time_node node ON node.id = indicator_row.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE draft FROM topic_indicator_draft draft
JOIN time_node node ON node.id = draft.node_id
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);

DELETE node FROM time_node node
WHERE node.code IN ('YEAR_1', 'YEAR_2', 'MID_TERM', 'TRIAL_RUN', 'FINAL')
  AND NOT EXISTS (SELECT 1 FROM achievement achievement_row WHERE achievement_row.node_id = node.id);
