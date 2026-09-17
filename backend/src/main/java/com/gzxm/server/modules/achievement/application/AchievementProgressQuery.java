package com.gzxm.server.modules.achievement.application;

import com.gzxm.server.modules.achievement.api.AchievementProgressDtos.*;
import java.util.List;

/** B's request-bound statistics contract for C. Every call enforces the current user's data scope. */
public interface AchievementProgressQuery {
    ProgressView progress(Long topicId,long nodeId,Long unitId);
    /** Includes historical rows only for global readers; callers must honor historical when aggregating. */
    List<EffectiveAchievement> effectiveAchievements(long topicId,long nodeId,Long unitId);
}
