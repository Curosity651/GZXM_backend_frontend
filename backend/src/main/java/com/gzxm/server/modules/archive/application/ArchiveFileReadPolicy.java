package com.gzxm.server.modules.archive.application;

import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.modules.file.application.FileReadPolicy;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class ArchiveFileReadPolicy implements FileReadPolicy {
    private final JdbcTemplate db;
    private final TopicQueryService topics;
    public ArchiveFileReadPolicy(JdbcTemplate db, TopicQueryService topics) { this.db = db; this.topics = topics; }
    @Override
    public boolean canRead(long fileId, CurrentUser user) {
        var links = db.queryForList("SELECT f.topic_id,f.unit_id,f.owner_type FROM archive_folder_file x " +
                "JOIN archive_folder f ON f.id=x.folder_id WHERE x.file_id=? AND x.deleted_at IS NULL AND f.deleted_at IS NULL",
                fileId);
        return links.stream().anyMatch(row -> {
            long topicId = ((Number) row.get("topic_id")).longValue();
            long unitId = ((Number) row.get("unit_id")).longValue();
            if ("SELF_FUNDED".equals(row.get("owner_type")) && user.isExternalUnit()) return false;
            if (!topics.canReadTopic(topicId)) return false;
            if (user.isGlobalRole()) return true;
            return user.unitId() != null && (user.unitId() == unitId || topics.isLeadUnit(topicId, user.unitId()));
        });
    }
}
