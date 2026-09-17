package com.gzxm.server.modules.topic.repository;

import com.gzxm.server.modules.topic.domain.TopicEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface TopicMapper {
    @Select("SELECT id FROM biz_project WHERE enabled=1 ORDER BY id LIMIT 2")
    List<Long> configuredProjects();
    String FILTER = """
        <where>
          <if test="unitId != null">
            EXISTS (SELECT 1 FROM biz_topic_unit_membership m
              WHERE m.topic_id=t.id AND m.unit_id=#{unitId} AND m.enabled=1)
          </if>
          <if test="keyword != null">
            AND (t.code LIKE CONCAT('%',#{keyword},'%') OR t.name LIKE CONCAT('%',#{keyword},'%'))
          </if>
          <if test="status != null">AND t.status=#{status}</if>
          <if test="enabled != null">AND t.enabled=#{enabled}</if>
        </where>
        """;

    @Select("<script>SELECT t.* FROM biz_topic t " + FILTER + " ORDER BY t.id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<TopicEntity> search(@Param("unitId") Long unitId, @Param("keyword") String keyword,
                             @Param("status") String status, @Param("enabled") Boolean enabled,
                             @Param("size") long size, @Param("offset") long offset);

    @Select("<script>SELECT COUNT(*) FROM biz_topic t " + FILTER + "</script>")
    long count(@Param("unitId") Long unitId, @Param("keyword") String keyword,
               @Param("status") String status, @Param("enabled") Boolean enabled);

    @Select("SELECT * FROM biz_topic WHERE id=#{id}")
    TopicEntity find(long id);
    @Select("<script>SELECT t.* FROM biz_topic t WHERE t.project_id=#{project} <if test='unit != null'>AND EXISTS(SELECT 1 FROM biz_topic_unit_membership m WHERE m.topic_id=t.id AND m.unit_id=#{unit} AND m.enabled=1)</if> ORDER BY t.id</script>")
    List<TopicEntity> projectTopics(@Param("project") long project,@Param("unit") Long unit);
    @Select("SELECT * FROM biz_topic WHERE id=#{id} FOR UPDATE")
    TopicEntity lock(long id);
    @Select("SELECT id FROM biz_project WHERE enabled=1 ORDER BY id LIMIT 2 FOR UPDATE")
    List<Long> activeProjects();

    @Insert("""
        INSERT INTO biz_topic(project_id,code,name,summary,lead_unit_id,status,enabled,
          start_date,end_date,record_version,created_by,updated_by)
        VALUES(#{projectId},#{code},#{name},#{summary},#{leadUnitId},#{status},#{enabled},
          #{startDate},#{endDate},#{recordVersion},#{createdBy},#{updatedBy})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TopicEntity topic);

    @Update("""
        UPDATE biz_topic SET code=#{code},name=#{name},summary=#{summary},lead_unit_id=#{leadUnitId},
          status=#{status},enabled=#{enabled},start_date=#{startDate},end_date=#{endDate},
          updated_by=#{updatedBy},record_version=record_version+1
        WHERE id=#{id} AND record_version=#{recordVersion}
        """)
    int update(TopicEntity topic);
}
