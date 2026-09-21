package com.gzxm.server.modules.system.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzxm.server.modules.system.domain.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Set;

public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("SELECT * FROM sys_user WHERE id=#{id} FOR UPDATE")
    UserEntity lockById(long id);

    @Select("""
            SELECT u.*, r.code AS role_code
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.username = #{username} AND u.deleted_at IS NULL
            """)
    UserEntity findForAuthentication(@Param("username") String username);

    @Select("""
            SELECT DISTINCT u.unit_id
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id=u.id
            JOIN sys_role r ON r.id=ur.role_id AND r.enabled=1
            WHERE u.deleted_at IS NULL AND u.enabled=1 AND u.unit_id IS NOT NULL
              AND r.code IN ('INTERNAL_TOPIC_UNIT','EXTERNAL_TOPIC_UNIT')
            """)
    Set<Long> findEligibleTopicUnitIds();
}
