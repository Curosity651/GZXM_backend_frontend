CREATE TABLE unit_allocation_draft (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  topic_id BIGINT UNSIGNED NOT NULL,
  node_id BIGINT UNSIGNED NOT NULL,
  draft_version INT NOT NULL DEFAULT 0,
  published_draft_version INT NOT NULL DEFAULT 0,
  publish_version INT NOT NULL DEFAULT 0,
  topic_indicator_version INT NOT NULL,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_allocation_draft_topic_node(topic_id,node_id),
  CONSTRAINT fk_allocation_draft_topic FOREIGN KEY(topic_id) REFERENCES biz_topic(id),
  CONSTRAINT fk_allocation_draft_node FOREIGN KEY(node_id) REFERENCES time_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE unit_allocation_draft_item (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  draft_id BIGINT UNSIGNED NOT NULL,
  unit_id BIGINT UNSIGNED NOT NULL,
  indicator_definition_id BIGINT UNSIGNED NOT NULL,
  target_quantity INT NOT NULL,
  UNIQUE KEY uk_allocation_draft_dimension(draft_id,unit_id,indicator_definition_id),
  CONSTRAINT fk_allocation_draft_item FOREIGN KEY(draft_id) REFERENCES unit_allocation_draft(id),
  CONSTRAINT fk_allocation_draft_unit FOREIGN KEY(unit_id) REFERENCES sys_unit(id),
  CONSTRAINT fk_allocation_draft_definition FOREIGN KEY(indicator_definition_id) REFERENCES indicator_definition(id),
  CHECK(target_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE unit_allocation_publication (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  topic_id BIGINT UNSIGNED NOT NULL,
  node_id BIGINT UNSIGNED NOT NULL,
  draft_version INT NOT NULL,
  publish_version INT NOT NULL,
  topic_indicator_version INT NOT NULL,
  published_by BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  allocations_json JSON NOT NULL,
  published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_allocation_publication_request(published_by,request_key),
  UNIQUE KEY uk_allocation_publication_version(topic_id,node_id,publish_version),
  CONSTRAINT fk_allocation_publication_topic FOREIGN KEY(topic_id) REFERENCES biz_topic(id),
  CONSTRAINT fk_allocation_publication_node FOREIGN KEY(node_id) REFERENCES time_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO unit_allocation_draft(topic_id,node_id,draft_version,published_draft_version,publish_version,topic_indicator_version,updated_by)
SELECT a.topic_id,a.node_id,MAX(CASE WHEN a.status='DRAFT' THEN 1 ELSE 0 END),0,MAX(a.publish_version),
       COALESCE((SELECT MAX(t.publish_version) FROM topic_indicator t WHERE t.topic_id=a.topic_id AND t.node_id=a.node_id AND t.status='PUBLISHED'),0),COALESCE(MAX(a.published_by),0)
FROM unit_indicator_allocation a GROUP BY a.topic_id,a.node_id;

INSERT INTO unit_allocation_draft_item(draft_id,unit_id,indicator_definition_id,target_quantity)
SELECT d.id,a.unit_id,a.indicator_definition_id,a.target_quantity
FROM unit_indicator_allocation a JOIN unit_allocation_draft d ON d.topic_id=a.topic_id AND d.node_id=a.node_id
WHERE a.status='DRAFT';

INSERT INTO unit_allocation_publication(topic_id,node_id,draft_version,publish_version,topic_indicator_version,published_by,request_key,allocations_json)
SELECT a.topic_id,a.node_id,0,MAX(a.publish_version),MAX(d.topic_indicator_version),0,CONCAT('baseline-',a.topic_id,'-',a.node_id),
       JSON_ARRAYAGG(JSON_OBJECT('unitId',CAST(a.unit_id AS CHAR),'indicatorDefinitionId',CAST(a.indicator_definition_id AS CHAR),'targetQuantity',a.target_quantity))
FROM unit_indicator_allocation a JOIN unit_allocation_draft d ON d.topic_id=a.topic_id AND d.node_id=a.node_id
WHERE a.status='PUBLISHED' GROUP BY a.topic_id,a.node_id;
