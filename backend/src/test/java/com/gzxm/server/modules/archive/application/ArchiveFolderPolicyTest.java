package com.gzxm.server.modules.archive.application;

import com.gzxm.server.common.security.CurrentUser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ArchiveFolderPolicyTest {
    @Test
    void globalStaffCanManageUnitFoldersButOtherUnitsCannot() {
        assertThat(ArchiveFolderPolicy.canManage(user(1, null, "RESEARCH_ASSISTANT"), 20)).isTrue();
        assertThat(ArchiveFolderPolicy.canManage(user(2, 20L, "INTERNAL_TOPIC_UNIT"), 20)).isTrue();
        assertThat(ArchiveFolderPolicy.canManage(user(3, 21L, "EXTERNAL_TOPIC_UNIT"), 20)).isFalse();
    }

    @Test
    void templateAndStaffCreatedFoldersAreProtectedFromOtherPeople() {
        CurrentUser unit = user(3, 20L, "INTERNAL_TOPIC_UNIT");
        assertThat(ArchiveFolderPolicy.canDelete(unit, 20, false, 2, "INTERNAL_TOPIC_UNIT")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(unit, 20, true, 1, "RESEARCH_ASSISTANT")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(unit, 20, true, 2, "INTERNAL_TOPIC_UNIT")).isTrue();
        assertThat(ArchiveFolderPolicy.canDelete(unit, 21, true, 2, "INTERNAL_TOPIC_UNIT")).isFalse();
        assertThat(ArchiveFolderPolicy.canDelete(user(1, null, "RESEARCH_ASSISTANT"), 20, true, 1,
                "RESEARCH_ASSISTANT")).isTrue();
    }

    private CurrentUser user(long id, Long unitId, String role) {
        return new CurrentUser(id, "test", unitId, role, Set.of(), List.of(), 0);
    }
}
