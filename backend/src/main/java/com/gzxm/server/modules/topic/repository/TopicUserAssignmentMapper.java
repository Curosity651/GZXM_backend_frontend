package com.gzxm.server.modules.topic.repository;

import org.apache.ibatis.annotations.*;
import java.util.List;

public interface TopicUserAssignmentMapper {
    @Select("SELECT user_id FROM biz_topic_user_assignment WHERE membership_id=#{membershipId} AND enabled=1 ORDER BY user_id")
    List<Long> activeUserIds(long membershipId);

    @Select("SELECT COUNT(*) FROM biz_topic_user_assignment WHERE membership_id=#{membershipId} AND user_id=#{userId} AND enabled=1")
    int assigned(@Param("membershipId") long membershipId, @Param("userId") long userId);

    @Select("""
        SELECT COUNT(*) FROM sys_user u
        JOIN sys_user_role ur ON ur.user_id=u.id
        JOIN sys_role r ON r.id=ur.role_id AND r.enabled=1
        WHERE u.id=#{userId} AND u.unit_id=#{unitId} AND u.enabled=1 AND u.deleted_at IS NULL
          AND r.code IN ('INTERNAL_TOPIC_UNIT','EXTERNAL_TOPIC_UNIT')
        """)
    int eligible(@Param("userId") long userId, @Param("unitId") long unitId);

    @Insert("""
        INSERT INTO biz_topic_user_assignment(membership_id,user_id,enabled,created_by,updated_by)
        VALUES(#{membershipId},#{userId},1,#{actor},#{actor})
        ON DUPLICATE KEY UPDATE enabled=1,updated_by=#{actor}
        """)
    int enable(@Param("membershipId") long membershipId, @Param("userId") long userId, @Param("actor") long actor);

    @Update("UPDATE biz_topic_user_assignment SET enabled=0,updated_by=#{actor} WHERE membership_id=#{membershipId} AND enabled=1")
    int disableAll(@Param("membershipId") long membershipId, @Param("actor") long actor);

    @Select("SELECT COUNT(*) FROM biz_topic_user_assignment WHERE user_id=#{userId} AND enabled=1")
    int activeAssignmentCount(long userId);
}
