package com.gzxm.server.modules.archive.application;

import com.gzxm.server.common.security.CurrentUser;
import java.util.Set;

final class ArchiveFolderPolicy {
    private static final Set<String> PROTECTED_CREATOR_ROLES = Set.of("PROJECT_TECH_LEADER", "RESEARCH_ASSISTANT");

    private ArchiveFolderPolicy() {}

    static boolean canManage(CurrentUser user, long unitId, String ownerType) {
        boolean permitted = user.authorities().contains(requiredAuthority(ownerType));
        return permitted && (user.isGlobalRole()
                || user.unitId() != null && user.unitId() == unitId
                && (user.isInternalUnit() || user.isExternalUnit()));
    }

    static boolean canDelete(CurrentUser user, long unitId, String ownerType, boolean custom, long creatorId, String creatorRole) {
        return custom && ((user.isGlobalRole() && user.authorities().contains(requiredAuthority(ownerType))) || (canManage(user, unitId, ownerType)
                && (creatorRole != null && !PROTECTED_CREATOR_ROLES.contains(creatorRole) || creatorId == user.id())));
    }

    private static String requiredAuthority(String ownerType) {
        return "SELF_FUNDED".equals(ownerType) ? "self-funded.manage" : "archive.topic.submit";
    }
}
