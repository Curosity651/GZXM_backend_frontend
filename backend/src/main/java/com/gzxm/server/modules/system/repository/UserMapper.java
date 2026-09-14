package com.gzxm.server.modules.system.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzxm.server.modules.system.domain.UserEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface UserMapper extends BaseMapper<UserEntity> {
    @Select("""
            SELECT u.*, r.code AS role_code
            FROM sys_user u
            JOIN sys_user_role ur ON ur.user_id = u.id
            JOIN sys_role r ON r.id = ur.role_id
            WHERE u.username = #{username} AND u.deleted_at IS NULL
            """)
    UserEntity findForAuthentication(@Param("username") String username);
}
