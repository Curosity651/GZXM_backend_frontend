package com.gzxm.server.modules.system.api;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.modules.system.api.SystemDtos.*;
import com.gzxm.server.modules.system.application.SystemService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final SystemService service;
    private final AuditService audit;

    public SystemController(SystemService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping("/users")
    @PreAuthorize("isAuthenticated()")
    PageResult<UserView> listUsers(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "20") long size,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Long roleId,
                                   @RequestParam(required = false) Boolean enabled,
                                   @AuthenticationPrincipal CurrentUser currentUser) {
        return service.listUsersFor(currentUser, page, Math.min(size, 200), keyword, roleId, enabled);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("authentication.principal.roleCode == 'SYSTEM_ADMIN'")
    CreateUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse result = service.createUser(request); audit.success("system.user.create", "USER", result.user().id()); return result;
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("isAuthenticated()")
    UserView getUser(@PathVariable long userId, @AuthenticationPrincipal CurrentUser currentUser) {
        return service.getUserFor(currentUser, userId);
    }

    @PatchMapping("/users/{userId}")
    @Operation(operationId = "updateUserProfile")
    @PreAuthorize("isAuthenticated()")
    UserView updateUser(@PathVariable long userId, @Valid @RequestBody UpdateUserRequest request,
                        @AuthenticationPrincipal CurrentUser currentUser) {
        if ("SYSTEM_ADMIN".equals(currentUser.roleCode()) && currentUser.id() == userId && request.roleId() != null
                && !request.roleId().trim().equals(service.getUser(userId).roleId()))
            throw BusinessException.forbidden("SELF_ROLE_CHANGE_DENIED", "不能修改当前登录管理员自己的角色");
        UserView result = service.updateSelfProfile(currentUser, userId, request);
        audit.success("system.user.update", "USER", String.valueOf(userId)); return result;
    }

    @PutMapping("/users/{userId}/status")
    @PreAuthorize("authentication.principal.roleCode == 'SYSTEM_ADMIN'")
    UserView setUserStatus(@PathVariable long userId, @RequestBody StatusRequest request) {
        UserView result = service.setStatus(userId, request.enabled()); audit.success("system.user.status", "USER", String.valueOf(userId)); return result;
    }

    @PutMapping("/users/{userId}/password")
    @Operation(operationId = "changeUserPassword")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@PathVariable long userId, @Valid @RequestBody PasswordChangeRequest request,
                        @AuthenticationPrincipal CurrentUser currentUser) {
        service.requireSelfOrAdministrator(currentUser, userId);
        service.changePassword(userId, request.password()); audit.success("system.user.password.change", "USER", String.valueOf(userId));
    }

    @GetMapping("/roles")
    @PreAuthorize("authentication.principal.roleCode == 'SYSTEM_ADMIN'")
    List<RoleView> listRoles() { return service.listRoles(); }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("authentication.principal.roleCode == 'SYSTEM_ADMIN'")
    RoleView getRole(@PathVariable long roleId) { return service.getRole(roleId); }

    @PutMapping("/roles/{roleId}")
    @Operation(operationId = "updateRolePermissions")
    @PreAuthorize("authentication.principal.roleCode == 'SYSTEM_ADMIN'")
    RoleView updateRole(@PathVariable long roleId, @Valid @RequestBody RolePermissionRequest request) {
        RoleView result = service.updateRolePermissions(roleId, request); audit.success("system.role.permissions.update", "ROLE", String.valueOf(roleId)); return result;
    }

    @GetMapping("/permissions")
    @PreAuthorize("authentication.principal.roleCode == 'SYSTEM_ADMIN'")
    List<PermissionView> listPermissions() { return service.listPermissions(); }

    @GetMapping("/units")
    @PreAuthorize("isAuthenticated()")
    List<UnitView> listUnits(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Boolean internal) {
        return service.listUnits(keyword, internal);
    }

    @GetMapping("/topic-unit-users")
    @PreAuthorize("hasAuthority('topic.manage') or hasAuthority('system.manage')")
    List<TopicUserView> listTopicUsers(@RequestParam(required = false) Long unitId) {
        return service.listTopicUsers(unitId);
    }
}
