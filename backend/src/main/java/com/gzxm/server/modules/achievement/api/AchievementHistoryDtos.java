package com.gzxm.server.modules.achievement.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/** B response types matching existing shared OpenAPI models without modifying them. */
public final class AchievementHistoryDtos {
    private AchievementHistoryDtos() {}
    public record SnapshotView(String id,String businessType,String businessId,String stage,int submittedVersion,
                               OffsetDateTime submittedAt,String submitterId,JsonNode payload) {}
    public record ApprovalView(String id,String businessType,String businessId,String stage,String level,String decision,
                               String opinion,String operatorId,OffsetDateTime operatedAt,int submittedVersion) {}
}
