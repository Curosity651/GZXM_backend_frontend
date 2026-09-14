package com.gzxm.server.modules.system.application;

import com.gzxm.server.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gzxm.server.modules.system.api.SystemDtos.RolePermissionRequest;
import com.gzxm.server.modules.system.domain.PermissionEntity;
import com.gzxm.server.modules.system.domain.RoleEntity;
import com.gzxm.server.modules.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final UnitMapper units = mock(UnitMapper.class);
    private final SystemRelationMapper relations = mock(SystemRelationMapper.class);
    private SystemService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), PermissionEntity.class);
        service = new SystemService(users, roles, permissions, units, relations, mock(PasswordEncoder.class));
    }

    @Test
    void externalRoleCannotBeGrantedSelfFundedPermission() {
        when(roles.selectById(5L)).thenReturn(role(5, "EXTERNAL_TOPIC_UNIT"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("page:self-funded-archive", "PAGE", true)));

        assertThatThrownBy(() -> service.updateRolePermissions(5,
                new RolePermissionRequest(List.of("self-funded-archive"), List.of(), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("EXTERNAL_PERMISSION_LOCKED");
    }

    @Test
    void systemAdminCannotLoseSystemManagementPermission() {
        when(roles.selectById(1L)).thenReturn(role(1, "SYSTEM_ADMIN"));
        when(permissions.selectList(any())).thenReturn(List.of(permission("page:home", "PAGE", false)));

        assertThatThrownBy(() -> service.updateRolePermissions(1,
                new RolePermissionRequest(List.of("home"), List.of(), true)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("SYSTEM_ADMIN_PERMISSION_REQUIRED");
    }

    private RoleEntity role(long id, String code) {
        RoleEntity role = new RoleEntity();
        role.setId(id); role.setCode(code); role.setEnabled(true);
        return role;
    }

    private PermissionEntity permission(String code, String type, boolean locked) {
        PermissionEntity permission = new PermissionEntity();
        permission.setId(10L); permission.setCode(code); permission.setPermissionType(type);
        permission.setEnabled(true); permission.setLockedForExternal(locked);
        return permission;
    }
}
