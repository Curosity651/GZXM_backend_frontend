package com.gzxm.server.modules.achievement.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.*;

public final class AchievementProgressDtos {
    private AchievementProgressDtos() {}
    public record Stages(long initiated,long submitted,long preApproved,long external,long formal,long supplement,long effective) {}
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Row(String scope,String topicId,String unitId,String nodeId,String indicatorDefinitionId,String achievementType,
                      Long targetQuantity,Integer targetVersion,boolean targetPublished,boolean hasTarget,BigDecimal completionRate,
                      boolean historical,Stages stages) {}
    public record ProgressView(String nodeId,String countingBasis,Map<String,Long> baseTotals,Stages baseStages,List<Row> specialIndicators,List<Row> rows) {
        public ProgressView {baseTotals=Collections.unmodifiableMap(new LinkedHashMap<>(baseTotals));specialIndicators=List.copyOf(specialIndicators);rows=List.copyOf(rows);}
    }
    public record EffectiveAchievement(String id,String topicId,String unitId,String nodeId,String indicatorDefinitionId,String achievementType,boolean historical) {}
}
