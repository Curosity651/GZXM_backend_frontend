package com.gzxm.server.common.security;

import com.gzxm.server.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultTopicAccessServiceTest {
    private final SecurityContextFacade security = mock(SecurityContextFacade.class);
    private final DefaultTopicAccessService access = new DefaultTopicAccessService(security);

    @Test
    void allowsMemberAndLeadWithinTheirTopic() {
        when(security.requireCurrentUser()).thenReturn(unitUser("INTERNAL_TOPIC_UNIT", "LEAD", 11));

        assertThatCode(() -> access.requireMember(11)).doesNotThrowAnyException();
        assertThatCode(() -> access.requireLead(11)).doesNotThrowAnyException();
        assertThatCode(access::requireInternalUnit).doesNotThrowAnyException();
        assertThatCode(() -> access.requireOwnedByCurrentUnit(20)).doesNotThrowAnyException();
    }

    @Test
    void rejectsParticipantFromLeadActionAndOtherTopic() {
        when(security.requireCurrentUser()).thenReturn(unitUser("INTERNAL_TOPIC_UNIT", "PARTICIPANT", 11));

        assertThatThrownBy(() -> access.requireLead(11)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("TOPIC_LEAD_REQUIRED");
        assertThatThrownBy(() -> access.requireMember(12)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("TOPIC_SCOPE_DENIED");
    }

    @Test
    void rejectsExternalUnitFromSelfFundedScope() {
        when(security.requireCurrentUser()).thenReturn(unitUser("EXTERNAL_TOPIC_UNIT", "LEAD", 11));

        assertThatThrownBy(access::requireInternalUnit).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("INTERNAL_UNIT_REQUIRED");
    }

    @Test
    void allowsGlobalRoleAcrossTopicsAndUnits() {
        when(security.requireCurrentUser()).thenReturn(new CurrentUser(1, "assistant", null,
                "RESEARCH_ASSISTANT", Set.of(), List.of(), 0));

        assertThatCode(() -> access.requireMember(999)).doesNotThrowAnyException();
        assertThatCode(() -> access.requireLead(999)).doesNotThrowAnyException();
        assertThatCode(access::requireInternalUnit).doesNotThrowAnyException();
        assertThatCode(() -> access.requireOwnedByCurrentUnit(999)).doesNotThrowAnyException();
    }

    private CurrentUser unitUser(String role, String membershipType, long topicId) {
        return new CurrentUser(2, "unit", 20L, role, Set.of(),
                List.of(new CurrentUser.TopicMembership(1, topicId, 20, membershipType, true)), 0);
    }
}
