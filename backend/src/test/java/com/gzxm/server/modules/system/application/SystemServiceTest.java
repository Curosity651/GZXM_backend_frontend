package com.gzxm.server.modules.system.application;

import com.gzxm.server.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.gzxm.server.modules.system.api.SystemDtos.RolePermissionRequest;
import com.gzxm.server.modules.system.api.SystemDtos.CreateUserRequest;
import com.gzxm.server.modules.system.api.SystemDtos.UpdateUserRequest;
import com.gzxm.server.modules.system.domain.*;
import com.gzxm.server.modules.system.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final RoleMapper roles = mock(RoleMapper.class);
    private final PermissionMapper permissions = mock(PermissionMapper.class);
    private final UnitMapper units = mock(UnitMapper.class);
    private final SystemRelationMapper relations = mock(SystemRelationMapper.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private SystemService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), PermissionEntity.class);
        service = new SystemService(users, roles, permissions, units, relations, passwordEncoder);
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

    @Test
    void creatingExternalTopicAccountCreatesMatchingUnitAndUsesProvidedPassword() {
        RoleEntity external = role(5, "EXTERNAL_TOPIC_UNIT");
        external.setName("外部课题单位");
        when(roles.selectById(5L)).thenReturn(external);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        doAnswer(invocation -> {
            UnitEntity unit = invocation.getArgument(0);
            unit.setId(31L);
            return 1;
        }).when(units).insert(any(UnitEntity.class));
        doAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(41L);
            return 1;
        }).when(users).insert(any(UserEntity.class));
        when(relations.findRoleId(41L)).thenReturn(5L);

        var result = service.createUser(new CreateUserRequest(
                "qinghua_zhang", "5", "张老师", "13800000000", "teacher@example.com", true,
                null, "清华大学", "Password123"));

        assertThat(result.user().username()).isEqualTo("qinghua_zhang");
        assertThat(result.user().unitId()).isEqualTo("31");
        assertThat(result.user().roleName()).isEqualTo("外部课题单位");
        ArgumentCaptor<UnitEntity> unitCaptor = ArgumentCaptor.forClass(UnitEntity.class);
        verify(units).insert((UnitEntity) unitCaptor.capture());
        assertThat(unitCaptor.getValue()).satisfies(unit -> {
            assertThat(unit.getName()).isEqualTo("清华大学");
            assertThat(unit.getInternalFlag()).isFalse();
            assertThat(unit.getEnabled()).isTrue();
        });
        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).insert((UserEntity) userCaptor.capture());
        assertThat(userCaptor.getValue()).satisfies(user -> {
            assertThat(user.getUnitId()).isEqualTo(31L);
            assertThat(user.getAccountType()).isEqualTo("TOPIC_UNIT");
            assertThat(user.getPasswordHash()).isEqualTo("hashed-password");
        });
        verify(relations).assignRole(41L, 5L);
        verify(passwordEncoder).encode("Password123");
    }

    @Test
    void platformAndTopicUnitRolesCannotBeExchangedDirectly() {
        UserEntity user = new UserEntity(); user.setId(41L); user.setAccountType("PLATFORM");
        when(users.selectById(41L)).thenReturn(user);
        when(relations.findRoleId(41L)).thenReturn(2L);
        when(roles.selectById(2L)).thenReturn(role(2, "RESEARCH_ASSISTANT"));
        when(roles.selectById(4L)).thenReturn(role(4, "INTERNAL_TOPIC_UNIT"));

        assertThatThrownBy(() -> service.updateUser(41L,
                new UpdateUserRequest("assistant", "4", "科研助理", null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("USER_ROLE_CATEGORY_CHANGE_DENIED");
        verify(relations, never()).assignRole(41L, 4L);
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
