package com.gzxm.server.modules.system.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gzxm.server.modules.system.domain.RoleEntity;
import org.apache.ibatis.annotations.Select;

public interface RoleMapper extends BaseMapper<RoleEntity> {
    @Select("SELECT * FROM sys_role WHERE id=#{id} FOR UPDATE")
    RoleEntity lockById(long id);
}
