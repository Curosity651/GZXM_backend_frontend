package com.gzxm.server.modules.achievement.domain;
import lombok.Data;
import java.time.LocalDateTime;
@Data
public class AchievementEntity {
    private Long id,projectId,topicId,membershipId,unitId,nodeId,indicatorDefinitionId,createdBy,updatedBy;
    private String achievementType,title,responsiblePerson,status,detailJson;
    private boolean countsToIndicator;
    private int recordVersion,submittedVersion;
    private LocalDateTime createdAt,updatedAt;
}
