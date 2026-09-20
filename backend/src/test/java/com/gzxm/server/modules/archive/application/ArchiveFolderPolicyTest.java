package com.gzxm.server.modules.archive.application;

import com.gzxm.server.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFolderPolicyTest {
    @Test
    void globalStaffCanManageUnitFoldersButOtherUnitsCannot() {
        assertThat(ArchiveFolderPolicy.canManage(user(1, null, "RESEARCH_ASSISTANT"), 20, "TOPIC_NATIONAL")).isTrue();
        assertThat(ArchiveFolderPolicy.canManage(user(2, 20L, "INTERNAL_TOPIC_UNIT"), 20, "TOPIC_NATIONAL")).isTrue();
        assertThat(ArchiveFolderPolicy.canManage(user(3, 21L, "EXTERNAL_TOPIC_UNIT"), 20, "TOPIC_NATIONAL")).isFalse();
        assertThat(ArchiveFolderPolicy.canManage(withoutPermissions(5, 20L, "INTERNAL_TOPIC_UNIT"), 20, "TOPIC_NATIONAL")).isFalse();
    }

    @Test
    void templateAndStaffCreatedFoldersAreProtectedFromOtherPeople() {
        CurrentUser unit = user(3, 20L, "INTERNAL_TOPIC_UNIT");
        assertThat(ArchiveFolderPolicy.canDelete(unit, 20, "TOPIC_NATIONAL", false, 2, "INTERNAL_TOPIC_UNIT")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(unit, 20, "TOPIC_NATIONAL", true, 1, "RESEARCH_ASSISTANT")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(unit, 20, "TOPIC_NATIONAL", true, 2, "INTERNAL_TOPIC_UNIT")).isTrue();
        assertThat(ArchiveFolderPolicy.canDelete(unit, 21, "TOPIC_NATIONAL", true, 2, "INTERNAL_TOPIC_UNIT")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(user(1, null, "RESEARCH_ASSISTANT"), 20, "TOPIC_NATIONAL", true, 1,
                "RESEARCH_ASSISTANT")).isTrue();
        assertThat(ArchiveFolderPolicy.canDelete(user(4, null, "PROJECT_TECH_LEADER"), 20, "TOPIC_NATIONAL", true, 1,
                "RESEARCH_ASSISTANT")).isTrue();
    }

    @Test
    void permissionsDoNotCrossBetweenNationalAndSelfFundedFolders() {
        CurrentUser nationalOnly = new CurrentUser(1, "test", null, "RESEARCH_ASSISTANT",
                Set.of("archive.topic.submit"), List.of(), 0);
        assertThat(ArchiveFolderPolicy.canManage(nationalOnly, 20, "TOPIC_NATIONAL")).isTrue();
        assertThat(ArchiveFolderPolicy.canManage(nationalOnly, 20, "SELF_FUNDED")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(nationalOnly, 20, "SELF_FUNDED", true, 1,
                "RESEARCH_ASSISTANT")).isFalse();
    }

    private CurrentUser user(long id, Long unitId, String role) {
        Set<String> authorities = Set.of("archive.topic.submit", "self-funded.manage");
        return new CurrentUser(id, "test", unitId, role, authorities, List.of(), 0);
    }

    private CurrentUser withoutPermissions(long id, Long unitId, String role) {
        return new CurrentUser(id, "test", unitId, role, Set.of(), List.of(), 0);
    }
}
