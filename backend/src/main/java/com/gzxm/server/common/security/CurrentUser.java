package com.gzxm.server.common.security;

import java.util.List;
import java.util.Set;

public record CurrentUser(long id, String username, String contactName, Long unitId, String unitName, String roleCode,
                          Set<String> authorities, List<TopicMembership> memberships,
                          int tokenVersion) {
    public CurrentUser(long id, String username, Long unitId, String roleCode,
                       Set<String> authorities, List<TopicMembership> memberships, int tokenVersion) {
        this(id, username, username, unitId, null, roleCode, authorities, memberships, tokenVersion);
    }
    public record TopicMembership(long id, long topicId, long unitId, String membershipType, boolean enabled) {}

    public boolean isExternalUnit() { return "EXTERNAL_TOPIC_UNIT".equals(roleCode); }
    public boolean isInternalUnit() { return "INTERNAL_TOPIC_UNIT".equals(roleCode); }
    public boolean isGlobalRole() {
        return Set.of("SYSTEM_ADMIN", "PROJECT_TECH_LEADER", "RESEARCH_ASSISTANT").contains(roleCode);
    }
}
