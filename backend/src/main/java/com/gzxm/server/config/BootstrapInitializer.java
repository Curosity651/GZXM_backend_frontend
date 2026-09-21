package com.gzxm.server.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzxm.server.modules.system.domain.PermissionEntity;
import com.gzxm.server.modules.system.domain.RoleEntity;
import com.gzxm.server.modules.system.domain.UserEntity;
import com.gzxm.server.modules.system.repository.PermissionMapper;
import com.gzxm.server.modules.system.repository.RoleMapper;
import com.gzxm.server.modules.system.repository.SystemRelationMapper;
import com.gzxm.server.modules.system.repository.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Component
public class BootstrapInitializer implements ApplicationRunner {
    private final AppProperties properties;
    private final PermissionMapper permissions;
    private final RoleMapper roles;
    private final UserMapper users;
    private final SystemRelationMapper relations;
    private final PasswordEncoder encoder;

    public BootstrapInitializer(AppProperties properties, PermissionMapper permissions, RoleMapper roles,
                                UserMapper users, SystemRelationMapper relations, PasswordEncoder encoder) {
        this.properties = properties;
        this.permissions = permissions;
        this.roles = roles;
        this.users = users;
        this.relations = relations;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.bootstrap().enabled()) return;
        Set<String> existingRoles = roles.selectList(new LambdaQueryWrapper<RoleEntity>()).stream()
                .map(RoleEntity::getCode).collect(java.util.stream.Collectors.toSet());
        Map<String, PermissionEntity> permissionMap = seedPermissions();
        Map<String, RoleEntity> roleMap = seedRoles();
        assignNewRole(existingRoles, "SYSTEM_ADMIN", roleMap.get("SYSTEM_ADMIN"), permissionMap.keySet(), permissionMap);
        assignNewRole(existingRoles, "PROJECT_TECH_LEADER", roleMap.get("PROJECT_TECH_LEADER"), Set.of(
                "page:user-management", "page:topic-indicator", "page:achievement-entry", "page:report-management", "page:topic-archive",
                "page:self-funded-archive", "page:archive-monitoring", "topic.manage", "indicator.manage", "topic-indicator.publish",
                "achievement.final.approve", "report.final.approve", "archive.topic.submit", "self-funded.manage",
                "file.upload", "file.download"), permissionMap);
        assignNewRole(existingRoles, "RESEARCH_ASSISTANT", roleMap.get("RESEARCH_ASSISTANT"), Set.of(
                "page:user-management", "page:topic-indicator", "page:achievement-entry", "page:report-management", "page:topic-archive",
                "page:self-funded-archive", "page:archive-monitoring", "topic.manage", "indicator.manage", "topic-indicator.publish",
                "achievement.initial.approve", "report.initial.approve", "report.rule.manage", "archive.topic.submit",
                "self-funded.manage", "file.upload", "file.download"), permissionMap);
        assignNewRole(existingRoles, "INTERNAL_TOPIC_UNIT", roleMap.get("INTERNAL_TOPIC_UNIT"), Set.of(
                "page:user-management", "page:topic-indicator", "page:achievement-entry", "page:report-management", "page:topic-archive",
                "page:self-funded-archive", "topic-unit.manage", "unit-allocation.manage", "unit-allocation.publish",
                "achievement.submit", "report.submit", "archive.topic.submit", "self-funded.manage", "file.upload", "file.download"), permissionMap);
        assignNewRole(existingRoles, "EXTERNAL_TOPIC_UNIT", roleMap.get("EXTERNAL_TOPIC_UNIT"), Set.of(
                "page:user-management", "page:topic-indicator", "page:achievement-entry", "page:report-management", "page:topic-archive",
                "topic-unit.manage", "unit-allocation.manage", "unit-allocation.publish", "achievement.submit", "report.submit",
                "archive.topic.submit", "file.upload", "file.download"), permissionMap);
        seedAdmin(roleMap.get("SYSTEM_ADMIN"));
    }

    private Map<String, PermissionEntity> seedPermissions() {
        Map<String, PermissionSeed> seeds = new LinkedHashMap<>();
        page(seeds, "home", "工作台", "工作台", false);
        page(seeds, "topic-indicator", "科研指标配置", "科研指标", false);
        page(seeds, "achievement-entry", "成果管理", "成果管理", false);
        page(seeds, "report-management", "月季报管理", "进度管理", false);
        page(seeds, "topic-archive", "课题国家材料", "归档管理", false);
        page(seeds, "self-funded-archive", "配套自筹材料", "归档管理", true);
        page(seeds, "archive-monitoring", "归档进度监控", "归档管理", false);
        page(seeds, "user-management", "用户管理", "系统管理", false);
        page(seeds, "role-permission", "角色权限管理", "系统管理", false);
        page(seeds, "dictionary", "字典管理", "系统管理", false);
        page(seeds, "system-config", "系统配置", "系统管理", false);
        page(seeds, "system-log", "系统错误日志", "系统管理", true);
        action(seeds, "topic.manage", "维护课题", "科研指标", false);
        action(seeds, "indicator.manage", "维护指标", "科研指标", false);
        action(seeds, "topic-indicator.publish", "下发课题指标", "科研指标", false);
        action(seeds, "topic-unit.manage", "维护承担单位", "科研指标", false);
        action(seeds, "unit-allocation.manage", "维护单位指标", "科研指标", false);
        action(seeds, "unit-allocation.publish", "下发单位指标", "科研指标", false);
        action(seeds, "achievement.submit", "提交成果", "成果管理", false);
        action(seeds, "achievement.initial.approve", "成果初审", "成果管理", false);
        action(seeds, "achievement.final.approve", "成果终审", "成果管理", false);
        action(seeds, "report.submit", "提交月季报", "进度管理", false);
        action(seeds, "report.initial.approve", "月季报初审", "进度管理", false);
        action(seeds, "report.final.approve", "月季报终审", "进度管理", false);
        action(seeds, "report.rule.manage", "配置月季报规则", "进度管理", false);
        action(seeds, "archive.topic.submit", "维护国家材料", "归档管理", false);
        action(seeds, "self-funded.manage", "维护配套自筹项目", "归档管理", true);
        action(seeds, "file.upload", "上传文件", "文件", false);
        action(seeds, "file.download", "查看下载文件", "文件", false);
        action(seeds, "system.manage", "系统管理", "系统管理", false);
        Map<String, PermissionEntity> result = new HashMap<>();
        for (var entry : seeds.entrySet()) {
            PermissionEntity entity = permissions.selectOne(new LambdaQueryWrapper<PermissionEntity>().eq(PermissionEntity::getCode, entry.getKey()));
            if (entity == null) {
                PermissionSeed seed = entry.getValue();
                entity = new PermissionEntity();
                entity.setCode(entry.getKey()); entity.setName(seed.name()); entity.setPermissionType(seed.type());
                entity.setPermissionGroup(seed.group()); entity.setLockedForExternal(seed.locked()); entity.setEnabled(true);
                permissions.insert(entity);
            }
            result.put(entity.getCode(), entity);
        }
        return result;
    }

    private Map<String, RoleEntity> seedRoles() {
        Map<String, String> names = Map.of(
                "SYSTEM_ADMIN", "系统管理员", "PROJECT_TECH_LEADER", "项目技术负责人", "RESEARCH_ASSISTANT", "科研助理",
                "INTERNAL_TOPIC_UNIT", "内部课题单位", "EXTERNAL_TOPIC_UNIT", "外部课题单位");
        Map<String, RoleEntity> result = new HashMap<>();
        names.forEach((code, name) -> {
            RoleEntity role = roles.selectOne(new LambdaQueryWrapper<RoleEntity>().eq(RoleEntity::getCode, code));
            if (role == null) {
                role = new RoleEntity(); role.setCode(code); role.setName(name); role.setBuiltIn(true); role.setEnabled(true);
                role.setDescription(name + "内置角色"); role.setCreatedAt(LocalDateTime.now()); role.setUpdatedAt(LocalDateTime.now()); roles.insert(role);
            }
            result.put(code, role);
        });
        return result;
    }

    private void seedAdmin(RoleEntity adminRole) {
        UserEntity admin = users.findForAuthentication(properties.bootstrap().adminUsername());
        if (admin != null) return;
        admin = new UserEntity();
        admin.setUsername(properties.bootstrap().adminUsername()); admin.setPasswordHash(encoder.encode(properties.bootstrap().adminPassword()));
        admin.setContactName("系统管理员"); admin.setAccountType("PLATFORM"); admin.setEnabled(true); admin.setTokenVersion(0);
        admin.setCreatedAt(LocalDateTime.now()); admin.setUpdatedAt(LocalDateTime.now()); users.insert(admin);
        relations.assignRole(admin.getId(), adminRole.getId());
    }

    private void assign(RoleEntity role, Set<String> codes, Map<String, PermissionEntity> permissionMap) {
        if (!relations.findRolePermissionCodes(role.getId()).isEmpty()) return;
        relations.deleteRolePermissions(role.getId());
        List<Long> ids = codes.stream().map(permissionMap::get).filter(Objects::nonNull).map(PermissionEntity::getId).toList();
        if (!ids.isEmpty()) relations.insertRolePermissions(role.getId(), ids);
    }

    private void assignNewRole(Set<String> existingRoles, String code, RoleEntity role, Set<String> permissions,
                               Map<String, PermissionEntity> permissionMap) {
        if (!existingRoles.contains(code)) assign(role, permissions, permissionMap);
    }

    private void page(Map<String, PermissionSeed> seeds, String code, String name, String group, boolean locked) {
        seeds.put("page:" + code, new PermissionSeed(name, "PAGE", group, locked));
    }
    private void action(Map<String, PermissionSeed> seeds, String code, String name, String group, boolean locked) {
        seeds.put(code, new PermissionSeed(name, "ACTION", group, locked));
    }
    private record PermissionSeed(String name, String type, String group, boolean locked) {}
}
