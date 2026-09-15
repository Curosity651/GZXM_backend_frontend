package com.gzxm.server.modules.topic.repository;

import com.gzxm.server.modules.topic.domain.TopicMembershipEntity;
import org.apache.ibatis.annotations.*;
import java.util.List;

public interface TopicMembershipMapper {
    @Select("SELECT * FROM biz_topic_unit_membership WHERE topic_id=#{topicId} ORDER BY CASE WHEN membership_type='LEAD' THEN 0 ELSE 1 END,id")
    List<TopicMembershipEntity> list(long topicId);
    @Select("SELECT * FROM biz_topic_unit_membership WHERE topic_id=#{topicId} AND unit_id=#{unitId}")
    TopicMembershipEntity findUnit(@Param("topicId") long topicId, @Param("unitId") long unitId);
    @Select("SELECT * FROM biz_topic_unit_membership WHERE topic_id=#{topicId} AND id=#{id}")
    TopicMembershipEntity find(@Param("topicId") long topicId, @Param("id") long id);
    @Insert("""
        INSERT INTO biz_topic_unit_membership(topic_id,unit_id,membership_type,enabled,created_by,updated_by)
        VALUES(#{topicId},#{unitId},#{membershipType},#{enabled},#{createdBy},#{updatedBy})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TopicMembershipEntity member);
    @Update("""
        UPDATE biz_topic_unit_membership SET membership_type=#{membershipType},enabled=#{enabled},updated_by=#{updatedBy}
        WHERE id=#{id} AND topic_id=#{topicId}
        """)
    int update(TopicMembershipEntity member);
}
