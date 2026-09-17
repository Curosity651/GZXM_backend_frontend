package com.gzxm.server.modules.achievement.repository;

import org.apache.ibatis.annotations.*;
import java.util.List;

public interface AchievementProgressMapper {
    record Fact(long id,long unitId,long nodeId,long definitionId,String achievementType,String status,boolean countsToIndicator,
                String detailJson,boolean preApproved,boolean external,boolean formal,boolean supplement) {}
    @Select("""
        <script>
        SELECT a.id,a.unit_id,a.node_id,a.indicator_definition_id definition_id,a.achievement_type,a.status,a.counts_to_indicator,a.detail_json,
        EXISTS(SELECT 1 FROM approval_record r WHERE r.business_type='ACHIEVEMENT' AND r.business_id=a.id AND r.stage='PRE_REVIEW' AND r.approval_level='FINAL' AND r.decision='APPROVED') pre_approved,
        EXISTS(SELECT 1 FROM achievement_workflow_operation o WHERE o.achievement_id=a.id AND o.operation_kind='ACTION' AND JSON_UNQUOTE(JSON_EXTRACT(o.request_json,'$.action'))='REGISTER_EXTERNAL_SUBMISSION') external,
        EXISTS(SELECT 1 FROM submission_snapshot s WHERE s.business_type='ACHIEVEMENT' AND s.business_id=a.id AND s.stage='FORMAL') formal,
        EXISTS(SELECT 1 FROM submission_snapshot s WHERE s.business_type='ACHIEVEMENT' AND s.business_id=a.id AND s.stage='SUPPLEMENT') supplement
        FROM achievement a WHERE a.topic_id=#{topic}
        AND a.node_id IN <foreach collection='nodes' item='n' open='(' close=')' separator=','>#{n}</foreach>
        AND a.unit_id IN <foreach collection='units' item='u' open='(' close=')' separator=','>#{u}</foreach>
        ORDER BY a.id
        </script>
        """)
    List<Fact> facts(@Param("topic") long topic,@Param("nodes") List<Long> nodes,@Param("units") List<Long> units);
}
