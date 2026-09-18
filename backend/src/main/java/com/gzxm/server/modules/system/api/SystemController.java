package com.gzxm.server.modules.system.api;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.system.api.SystemDtos.*;
import com.gzxm.server.modules.system.application.SystemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class SystemController {
    private final SystemService service;
    private final AuditService audit;

    public SystemController(SystemService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('system.manage')")
    PageResult<UserView> listUsers(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "20") long size,
                                   @RequestParam(required = false) String keyword,
                                   @RequestParam(required = false) Long roleId,
                                   @RequestParam(required = false) Boolean enabled) {
        return service.listUsers(page, Math.min(size, 200), keyword, roleId, enabled);
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.manage')")
    CreateUserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse result = service.createUser(request); audit.success("system.user.create", "USER", result.user().id()); return result;
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('system.manage')")
    UserView getUser(@PathVariable long userId) { return service.getUser(userId); }

    @PatchMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('system.manage')")
    UserView updateUser(@PathVariable long userId, @Valid @RequestBody UpdateUserRequest request) {
        UserView result = service.updateUser(userId, request); audit.success("system.user.update", "USER", String.valueOf(userId)); return result;
    }

    @PutMapping("/users/{userId}/status")
    @PreAuthorize("hasAuthority('system.manage')")
    UserView setUserStatus(@PathVariable long userId, @RequestBody StatusRequest request) {
        UserView result = service.setStatus(userId, request.enabled()); audit.success("system.user.status", "USER", String.valueOf(userId)); return result;
    }

    @PutMapping("/users/{userId}/password")
    @PreAuthorize("hasAuthority('system.manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changePassword(@PathVariable long userId, @Valid @RequestBody PasswordChangeRequest request) {
        service.changePassword(userId, request.password()); audit.success("system.user.password.change", "USER", String.valueOf(userId));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('system.manage')")
    List<RoleView> listRoles() { return service.listRoles(); }

    @GetMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('system.manage')")
    RoleView getRole(@PathVariable long roleId) { return service.getRole(roleId); }

    @PutMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('system.manage')")
    RoleView updateRole(@PathVariable long roleId, @Valid @RequestBody RolePermissionRequest request) {
        RoleView result = service.updateRolePermissions(roleId, request); audit.success("system.role.permissions.update", "ROLE", String.valueOf(roleId)); return result;
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('system.manage')")
    List<PermissionView> listPermissions() { return service.listPermissions(); }

    @GetMapping("/units")
    @PreAuthorize("isAuthenticated()")
    List<UnitView> listUnits(@RequestParam(required = false) String keyword,
                             @RequestParam(required = false) Boolean internal) {
        return service.listUnits(keyword, internal);
    }
}
