package com.gzxm.server.modules.system.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.modules.system.api.SystemDtos.*;
import com.gzxm.server.modules.system.domain.*;
import com.gzxm.server.modules.system.repository.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemService {
    private static final Set<String> UNIT_ROLES = Set.of("INTERNAL_TOPIC_UNIT", "EXTERNAL_TOPIC_UNIT");
    private static final String EXTERNAL_ROLE = "EXTERNAL_TOPIC_UNIT";

    private final UserMapper users;
    private final RoleMapper roles;
    private final PermissionMapper permissions;
    private final UnitMapper units;
    private final SystemRelationMapper relations;
    private final PasswordEncoder passwordEncoder;

    public SystemService(UserMapper users, RoleMapper roles, PermissionMapper permissions,
                         UnitMapper units, SystemRelationMapper relations, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.permissions = permissions;
        this.units = units;
        this.relations = relations;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResult<UserView> listUsers(long page, long size, String keyword, Long roleId, Boolean enabled) {
        LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>()
                .isNull(UserEntity::getDeletedAt)
                .eq(enabled != null, UserEntity::getEnabled, enabled)
                .and(StringUtils.hasText(keyword), q -> q.like(UserEntity::getUsername, keyword)
                        .or().like(UserEntity::getContactName, keyword))
                .orderByDesc(UserEntity::getCreatedAt);
        if (roleId != null) {
            query.inSql(UserEntity::getId, "SELECT user_id FROM sys_user_role WHERE role_id=" + roleId);
        }
        Page<UserEntity> result = users.selectPage(Page.of(page, size), query);
        return PageResult.of(result.getRecords().stream().map(this::toUserView).toList(), page, size, result.getTotal());
    }

    public UserView getUser(long id) {
        UserEntity user = requireUser(id);
        return toUserView(user);
    }

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        RoleEntity role = requireRole(parseId(request.roleId(), "roleId"));
        Long unitId = UNIT_ROLES.contains(role.getCode()) ? createAccountUnit(request.username().trim(), role) : null;
        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setContactName(request.name().trim());
        user.setPhone(trimToNull(request.phone()));
        user.setEmail(trimToNull(request.email()));
        user.setUnitId(unitId);
        user.setAccountType(UNIT_ROLES.contains(role.getCode()) ? "TOPIC_UNIT" : "PLATFORM");
        user.setEnabled(request.enabled() == null || request.enabled());
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        try {
            users.insert(user);
            relations.assignRole(user.getId(), role.getId());
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("USER_UNIQUE_CONFLICT", "用户名已存在，或该单位已经存在有效课题账号");
        }
        return new CreateUserResponse(toUserView(user));
    }

    @Transactional
    public UserView updateUser(long id, UpdateUserRequest request) {
        UserEntity user = requireUser(id);
        try {
            if (StringUtils.hasText(request.username())) {
                String username = request.username().trim();
                user.setUsername(username);
                if ("TOPIC_UNIT".equals(user.getAccountType()) && user.getUnitId() != null) {
                    UnitEntity unit = units.selectById(user.getUnitId());
                    if (unit != null) {
                        unit.setName(username);
                        unit.setUpdatedAt(LocalDateTime.now());
                        units.updateById(unit);
                    }
                }
            }
            if (StringUtils.hasText(request.name())) user.setContactName(request.name().trim());
            user.setPhone(trimToNull(request.phone()));
            user.setEmail(trimToNull(request.email()));
            user.setUpdatedAt(LocalDateTime.now());
            users.updateById(user);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("USERNAME_EXISTS", "用户名或单位名称已存在");
        }
        return toUserView(user);
    }

    @Transactional
    public UserView setStatus(long id, boolean enabled) {
        UserEntity user = requireUser(id);
        user.setEnabled(enabled);
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(LocalDateTime.now());
        try {
            users.updateById(user);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("ACTIVE_UNIT_ACCOUNT_EXISTS", "该单位已经存在另一个有效课题账号");
        }
        return toUserView(user);
    }

    @Transactional
    public void changePassword(long id, String password) {
        UserEntity user = requireUser(id);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setTokenVersion(user.getTokenVersion() + 1);
        user.setUpdatedAt(LocalDateTime.now());
        users.updateById(user);
    }

    public List<RoleView> listRoles() {
        return roles.selectList(new LambdaQueryWrapper<RoleEntity>().orderByAsc(RoleEntity::getId))
                .stream().map(this::toRoleView).toList();
    }

    public RoleView getRole(long id) { return toRoleView(requireRole(id)); }

    @Transactional
    public RoleView updateRolePermissions(long id, RolePermissionRequest request) {
        RoleEntity role = requireRole(id);
        List<String> allCodes = new ArrayList<>();
        allCodes.addAll(request.pagePermissions().stream().map(code -> "page:" + code).toList());
        allCodes.addAll(request.actionPermissions());
        List<PermissionEntity> selected = permissions.selectList(new LambdaQueryWrapper<PermissionEntity>()
                .in(PermissionEntity::getCode, allCodes).eq(PermissionEntity::getEnabled, true));
        if (selected.size() != new HashSet<>(allCodes).size()) {
            throw BusinessException.validation("UNKNOWN_PERMISSION", "包含不存在或已停用的权限");
        }
        if (EXTERNAL_ROLE.equals(role.getCode()) && selected.stream().anyMatch(p -> Boolean.TRUE.equals(p.getLockedForExternal()))) {
            throw BusinessException.forbidden("EXTERNAL_PERMISSION_LOCKED", "外部课题单位不能获得配套自筹权限");
        }
        if ("SYSTEM_ADMIN".equals(role.getCode()) && selected.stream().noneMatch(p -> "system.manage".equals(p.getCode()))) {
            throw BusinessException.validation("SYSTEM_ADMIN_PERMISSION_REQUIRED", "系统管理员必须保留系统管理权限");
        }
        relations.deleteRolePermissions(id);
        if (!selected.isEmpty()) relations.insertRolePermissions(id, selected.stream().map(PermissionEntity::getId).toList());
        if (request.enabled() != null) {
            role.setEnabled(request.enabled());
            role.setUpdatedAt(LocalDateTime.now());
            roles.updateById(role);
        }
        return toRoleView(role);
    }

    public List<PermissionView> listPermissions() {
        return permissions.selectList(new LambdaQueryWrapper<PermissionEntity>()
                        .eq(PermissionEntity::getEnabled, true).orderByAsc(PermissionEntity::getPermissionGroup).orderByAsc(PermissionEntity::getId))
                .stream().map(p -> new PermissionView("PAGE".equals(p.getPermissionType()) ? p.getCode().substring(5) : p.getCode(), p.getName(), p.getPermissionType(),
                        p.getPermissionGroup(), Boolean.TRUE.equals(p.getLockedForExternal()))).toList();
    }

    public List<UnitView> listUnits(String keyword, Boolean internal) {
        Set<Long> eligibleTopicUnits = users.findEligibleTopicUnitIds();
        return units.selectList(new LambdaQueryWrapper<UnitEntity>()
                        .isNull(UnitEntity::getDeletedAt)
                        .like(StringUtils.hasText(keyword), UnitEntity::getName, keyword)
                        .eq(internal != null, UnitEntity::getInternalFlag, internal)
                .orderByAsc(UnitEntity::getName))
                .stream().map(u -> new UnitView(String.valueOf(u.getId()), u.getCode(), u.getName(),
                        Boolean.TRUE.equals(u.getInternalFlag()), Boolean.TRUE.equals(u.getEnabled()),
                        eligibleTopicUnits.contains(u.getId()))).toList();
    }

    private UserView toUserView(UserEntity user) {
        Long roleId = relations.findRoleId(user.getId());
        RoleEntity role = roleId == null ? null : roles.selectById(roleId);
        return new UserView(String.valueOf(user.getId()), user.getUsername(), user.getContactName(),
                user.getUnitId() == null ? null : String.valueOf(user.getUnitId()),
                role == null ? null : String.valueOf(role.getId()), role == null ? null : role.getName(),
                user.getPhone(), user.getEmail(), Boolean.TRUE.equals(user.getEnabled()), user.getCreatedAt());
    }

    private RoleView toRoleView(RoleEntity role) {
        List<String> permissionCodes = relations.findRolePermissionCodes(role.getId());
        Map<String, PermissionEntity> byCode = (permissionCodes.isEmpty() ? List.<PermissionEntity>of()
                : permissions.selectList(new LambdaQueryWrapper<PermissionEntity>()
                        .in(PermissionEntity::getCode, permissionCodes)))
                .stream().collect(Collectors.toMap(PermissionEntity::getCode, Function.identity()));
        List<String> page = byCode.values().stream().filter(p -> "PAGE".equals(p.getPermissionType())).map(PermissionEntity::getCode).map(code -> code.substring(5)).sorted().toList();
        List<String> action = byCode.values().stream().filter(p -> "ACTION".equals(p.getPermissionType())).map(PermissionEntity::getCode).sorted().toList();
        return new RoleView(String.valueOf(role.getId()), role.getCode(), role.getName(), role.getDescription(), page, action,
                Boolean.TRUE.equals(role.getEnabled()), Boolean.TRUE.equals(role.getBuiltIn()));
    }

    private UserEntity requireUser(long id) {
        UserEntity user = users.selectById(id);
        if (user == null || user.getDeletedAt() != null) throw BusinessException.notFound("USER_NOT_FOUND", "用户不存在");
        return user;
    }

    private RoleEntity requireRole(long id) {
        RoleEntity role = roles.selectById(id);
        if (role == null) throw BusinessException.notFound("ROLE_NOT_FOUND", "角色不存在");
        return role;
    }

    private Long createAccountUnit(String name, RoleEntity role) {
        UnitEntity unit = new UnitEntity();
        unit.setCode("UNIT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase(Locale.ROOT));
        unit.setName(name);
        unit.setInternalFlag("INTERNAL_TOPIC_UNIT".equals(role.getCode()));
        unit.setEnabled(true);
        unit.setCreatedAt(LocalDateTime.now());
        unit.setUpdatedAt(LocalDateTime.now());
        try {
            units.insert(unit);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("UNIT_NAME_EXISTS", "该单位名称已经存在");
        }
        return unit.getId();
    }

    private long parseId(String value, String field) {
        try { return Long.parseLong(value); }
        catch (NumberFormatException ex) { throw BusinessException.validation("INVALID_ID", field + "格式不正确"); }
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
