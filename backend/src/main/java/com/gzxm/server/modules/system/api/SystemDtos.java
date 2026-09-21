package com.gzxm.server.modules.system.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class SystemDtos {
    private SystemDtos() {}

    public record UserView(String id, String username, String principalName, String principalPhone,
                           String principalEmail, String contactName, String contactPhone, String contactEmail,
                           String unitId, String unitName, String roleId, String roleName,
                           boolean enabled, LocalDateTime createdAt) {}

    public record CreateUserRequest(@NotBlank String username,
                                    @NotBlank String roleId,
                                    @NotBlank String principalName, String principalPhone,
                                    @Email String principalEmail, @NotBlank String contactName,
                                    String contactPhone, @Email String contactEmail, Boolean enabled,
                                    String unitId, String unitName,
                                    @NotBlank @Size(min = 8, max = 72) String password) {}

    public record CreateUserResponse(UserView user) {}

    public record UpdateUserRequest(String username, String roleId, String principalName, String principalPhone,
                                    @Email String principalEmail, String contactName, String contactPhone,
                                    @Email String contactEmail, String unitId, String unitName) {}
    public record StatusRequest(boolean enabled) {}
    public record PasswordChangeRequest(@NotBlank @Size(min = 8, max = 72) String password) {}

    public record RoleView(String id, String code, String name, String description,
                           List<String> pagePermissions, List<String> actionPermissions,
                           boolean enabled, boolean builtIn) {}

    public record RolePermissionRequest(@NotNull List<String> pagePermissions,
                                        @NotNull List<String> actionPermissions,
                                        Boolean enabled) {}

    public record PermissionView(String code, String name, String type, String group,
                                 boolean lockedForExternal) {}

    public record UnitView(String id, String code, String name, boolean internal, boolean enabled,
                           boolean topicUnitEligible) {}
    public record TopicUserView(String id, String username, String principalName, String contactName,
                                String contactPhone, String contactEmail, String unitId, String unitName,
                                boolean enabled) {}
}
