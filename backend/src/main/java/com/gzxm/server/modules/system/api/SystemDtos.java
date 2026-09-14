package com.gzxm.server.modules.system.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemDtos {
    private SystemDtos() {}

    public record UserView(String id, String username, String name, String unitId,
                           String roleId, String roleName, String phone, String email,
                           boolean enabled, LocalDateTime createdAt) {}

    public record CreateUserRequest(@NotBlank String username,
                                    @NotBlank @Size(min = 8) String password,
                                    @NotBlank String roleId, String unitId,
                                    @NotBlank String name, String phone,
                                    @Email String email, Boolean enabled) {}

    public record UpdateUserRequest(String username, String name, String phone, @Email String email) {}
    public record StatusRequest(boolean enabled) {}
    public record PasswordResetResponse(String temporaryPassword) {}

    public record RoleView(String id, String code, String name, String description,
                           List<String> pagePermissions, List<String> actionPermissions,
                           boolean enabled, boolean builtIn) {}

    public record RolePermissionRequest(@NotNull List<String> pagePermissions,
                                        @NotNull List<String> actionPermissions,
                                        Boolean enabled) {}

    public record PermissionView(String code, String name, String type, String group,
                                 boolean lockedForExternal) {}

    public record UnitView(String id, String code, String name, boolean internal, boolean enabled) {}
}
