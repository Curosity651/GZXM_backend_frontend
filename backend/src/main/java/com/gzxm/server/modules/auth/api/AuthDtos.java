package com.gzxm.server.modules.auth.api;

import com.gzxm.server.common.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Set;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, CurrentUserView user) {}

    public record CurrentUserView(String id, String username, String unitId, String roleCode,
                                  Set<String> pagePermissions, Set<String> actionPermissions,
                                  List<MembershipView> memberships) {
        public static CurrentUserView from(CurrentUser user) {
            Set<String> pages = user.authorities().stream().filter(p -> p.startsWith("page:")).map(p -> p.substring(5)).collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<String> actions = user.authorities().stream().filter(p -> !p.startsWith("page:") && !p.startsWith("ROLE_")).collect(java.util.stream.Collectors.toUnmodifiableSet());
            return new CurrentUserView(String.valueOf(user.id()), user.username(),
                    user.unitId() == null ? null : String.valueOf(user.unitId()), user.roleCode(), pages, actions,
                    user.memberships().stream().map(MembershipView::from).toList());
        }
    }

    public record MembershipView(String id, String topicId, String unitId, String membershipType, boolean enabled) {
        static MembershipView from(CurrentUser.TopicMembership membership) {
            return new MembershipView(String.valueOf(membership.id()), String.valueOf(membership.topicId()),
                    String.valueOf(membership.unitId()), membership.membershipType(), membership.enabled());
        }
    }
}
