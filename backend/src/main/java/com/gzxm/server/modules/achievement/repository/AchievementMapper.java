package com.gzxm.server.modules.achievement.repository;
import com.gzxm.server.modules.achievement.domain.AchievementEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface AchievementMapper {
    record Filter(Long unit,List<Long> memberTopics,List<Long> leadTopics,Long topic,Long node,Long requestedUnit,Long definition,String status,
                  List<String> visibleStates,List<String> pendingStates,long offset,long size) {}
    String FILTER="""
        <where>
        <if test='unit != null'>
          AND ((unit_id=#{unit} AND topic_id IN <foreach collection='memberTopics' item='t' open='(' close=')' separator=','>#{t}</foreach>)
          OR topic_id IN <foreach collection='leadTopics' item='t' open='(' close=')' separator=','>#{t}</foreach>)
        </if>
        <if test='topic != null'>AND topic_id=#{topic}</if>
        <if test='node != null'>AND node_id=#{node}</if>
        <if test='requestedUnit != null'>AND unit_id=#{requestedUnit}</if>
        <if test='definition != null'>AND indicator_definition_id=#{definition}</if>
        <if test='status != null'>AND status=#{status}</if>
        <if test='visibleStates != null'>AND status IN <foreach collection='visibleStates' item='s' open='(' close=')' separator=','>#{s}</foreach></if>
        <if test='pendingStates != null'>AND status IN <foreach collection='pendingStates' item='s' open='(' close=')' separator=','>#{s}</foreach></if>
        </where>
        """;
    @Select("<script>SELECT * FROM achievement "+FILTER+" ORDER BY id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<AchievementEntity> list(Filter filter);
    @Select("<script>SELECT COUNT(*) FROM achievement "+FILTER+"</script>")
    long count(Filter filter);
    @Select("SELECT * FROM achievement WHERE id=#{id}")
    AchievementEntity find(long id);
    @Select("SELECT * FROM achievement WHERE id=#{id} FOR UPDATE")
    AchievementEntity lock(long id);
    @Insert("INSERT INTO achievement(project_id,topic_id,membership_id,unit_id,node_id,indicator_definition_id,achievement_type,title,responsible_person,status,counts_to_indicator,detail_json,record_version,submitted_version,created_by,updated_by) VALUES(#{projectId},#{topicId},#{membershipId},#{unitId},#{nodeId},#{indicatorDefinitionId},#{achievementType},#{title},#{responsiblePerson},#{status},0,#{detailJson},1,0,#{createdBy},#{updatedBy})")
    @Options(useGeneratedKeys=true,keyProperty="id")
    void insert(AchievementEntity row);
    @Update("UPDATE achievement SET title=#{title},responsible_person=#{responsiblePerson},detail_json=#{detailJson},record_version=record_version+1,updated_by=#{updatedBy} WHERE id=#{id} AND record_version=#{recordVersion}")
    int update(AchievementEntity row);

    @Update("UPDATE achievement SET status=#{status},counts_to_indicator=#{countsToIndicator},detail_json=#{detailJson},record_version=record_version+1,submitted_at=CASE WHEN submitted_version < #{submittedVersion} THEN CURRENT_TIMESTAMP(3) ELSE submitted_at END,submitted_version=#{submittedVersion},updated_by=#{updatedBy} WHERE id=#{id} AND record_version=#{recordVersion}")
    int transition(AchievementEntity row);
}
