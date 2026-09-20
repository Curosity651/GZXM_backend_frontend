package com.gzxm.server.modules.system.repository;

import com.gzxm.server.common.security.CurrentUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SystemRelationMapper {
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    Long findRoleId(long userId);

    @Select("SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=#{userId}")
    String findRoleCode(long userId);

    @Insert("""
            INSERT INTO sys_user_role(user_id, role_id) VALUES(#{userId}, #{roleId})
            ON DUPLICATE KEY UPDATE role_id=VALUES(role_id)
            """)
    int assignRole(@Param("userId") long userId, @Param("roleId") long roleId);

    @Select("""
            SELECT p.code FROM sys_user_role ur
            JOIN sys_role r ON r.id=ur.role_id AND r.enabled=1
            JOIN sys_role_permission rp ON rp.role_id=r.id
            JOIN sys_permission p ON p.id=rp.permission_id AND p.enabled=1
            WHERE ur.user_id=#{userId}
            """)
    List<String> findPermissionCodes(long userId);

    @Select("""
            SELECT p.code FROM sys_role_permission rp
            JOIN sys_permission p ON p.id=rp.permission_id AND p.enabled=1
            WHERE rp.role_id=#{roleId}
            """)
    List<String> findRolePermissionCodes(long roleId);

    @Select("""
            SELECT m.id, m.topic_id AS topicId, m.unit_id AS unitId,
                   m.membership_type AS membershipType, m.enabled
            FROM biz_topic_user_assignment a
            JOIN biz_topic_unit_membership m ON m.id=a.membership_id AND m.enabled=1
            WHERE a.user_id=#{userId} AND a.enabled=1
            """)
    List<CurrentUser.TopicMembership> findMemberships(long userId);

    @Select("SELECT COUNT(*) FROM biz_topic_user_assignment WHERE user_id=#{userId} AND enabled=1")
    int activeTopicAssignmentCount(long userId);

    @Update("""
            UPDATE sys_user u JOIN sys_user_role ur ON ur.user_id=u.id
            SET u.token_version=u.token_version+1,u.updated_at=NOW(3)
            WHERE ur.role_id=#{roleId} AND u.deleted_at IS NULL
            """)
    int invalidateRoleUsers(long roleId);

    @Delete("DELETE FROM sys_role_permission WHERE role_id=#{roleId}")
    int deleteRolePermissions(long roleId);

    @Insert("""
            <script>
            INSERT INTO sys_role_permission(role_id, permission_id) VALUES
            <foreach collection='permissionIds' item='permissionId' separator=','>
              (#{roleId}, #{permissionId})
            </foreach>
            </script>
            """)
    int insertRolePermissions(@Param("roleId") long roleId, @Param("permissionIds") List<Long> permissionIds);
}
