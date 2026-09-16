CREATE TABLE topic_indicator_draft (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  topic_id BIGINT UNSIGNED NOT NULL,
  node_id BIGINT UNSIGNED NOT NULL,
  draft_version INT NOT NULL DEFAULT 0,
  published_draft_version INT NOT NULL DEFAULT 0,
  publish_version INT NOT NULL DEFAULT 0,
  updated_by BIGINT UNSIGNED NOT NULL,
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_indicator_draft_topic_node(topic_id,node_id),
  CONSTRAINT fk_indicator_draft_topic FOREIGN KEY(topic_id) REFERENCES biz_topic(id),
  CONSTRAINT fk_indicator_draft_node FOREIGN KEY(node_id) REFERENCES time_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


CREATE TABLE topic_indicator_draft_target (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  draft_id BIGINT UNSIGNED NOT NULL,
  indicator_definition_id BIGINT UNSIGNED NOT NULL,
  target_quantity INT NOT NULL,
  UNIQUE KEY uk_indicator_draft_definition(draft_id,indicator_definition_id),
  CONSTRAINT fk_indicator_draft_target FOREIGN KEY(draft_id) REFERENCES topic_indicator_draft(id),
  CONSTRAINT fk_indicator_draft_definition FOREIGN KEY(indicator_definition_id) REFERENCES indicator_definition(id),
  CHECK(target_quantity>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Immutable business publication history; no access to A's generic api_idempotency table.
CREATE TABLE topic_indicator_publication (
  id BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  topic_id BIGINT UNSIGNED NOT NULL,
  node_id BIGINT UNSIGNED NOT NULL,
  draft_version INT NOT NULL,
  publish_version INT NOT NULL,
  published_by BIGINT UNSIGNED NOT NULL,
  request_key VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  targets_json JSON NOT NULL,
  published_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_indicator_publication_request(published_by,request_key),
  UNIQUE KEY uk_indicator_publication_version(topic_id,node_id,publish_version),
  CONSTRAINT fk_indicator_publication_topic FOREIGN KEY(topic_id) REFERENCES biz_topic(id),
  CONSTRAINT fk_indicator_publication_node FOREIGN KEY(node_id) REFERENCES time_node(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Preserve existing baseline data when upgrading a deployment that already has targets.
INSERT INTO topic_indicator_draft(topic_id,node_id,draft_version,published_draft_version,publish_version,updated_by)
SELECT topic_id,node_id,MAX(CASE WHEN status='DRAFT' THEN 1 ELSE 0 END),0,MAX(publish_version),COALESCE(MAX(published_by),0)
FROM topic_indicator GROUP BY topic_id,node_id;

INSERT INTO topic_indicator_draft_target(draft_id,indicator_definition_id,target_quantity)
SELECT d.id,t.indicator_definition_id,t.target_quantity
FROM topic_indicator t JOIN topic_indicator_draft d ON d.topic_id=t.topic_id AND d.node_id=t.node_id
WHERE t.status='DRAFT';

-- Actor 0 and reserved keys mark imported legacy snapshots, never live authenticated requests.
INSERT INTO topic_indicator_publication(topic_id,node_id,draft_version,publish_version,published_by,request_key,targets_json)
SELECT topic_id,node_id,0,MAX(publish_version),0,CONCAT('baseline-',topic_id,'-',node_id),
       JSON_OBJECTAGG(CAST(indicator_definition_id AS CHAR),target_quantity)
FROM topic_indicator WHERE status='PUBLISHED' GROUP BY topic_id,node_id;
