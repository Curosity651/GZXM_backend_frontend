package com.gzxm.server.modules.topic.application;

import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.topic.repository.TopicMapper;
import com.gzxm.server.modules.topic.repository.TopicMembershipMapper;
import com.gzxm.server.modules.topic.repository.TopicUserAssignmentMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TopicServiceTest {
    @Test
    void businessTopicCountExcludesDraftAndKeepsOtherEnabledStatuses() {
        TopicMapper topics = mock(TopicMapper.class);
        SecurityContextFacade security = mock(SecurityContextFacade.class);
        when(security.requireCurrentUser()).thenReturn(new CurrentUser(
                1, "admin", null, "SYSTEM_ADMIN", Set.of(), List.of(), 1));
        when(topics.count(null, null, "ACTIVE", true)).thenReturn(2L);
        when(topics.count(null, null, "PAUSED", true)).thenReturn(1L);
        when(topics.count(null, null, "CLOSED", true)).thenReturn(3L);

        TopicService service = new TopicService(topics, mock(TopicMembershipMapper.class), mock(TopicUserAssignmentMapper.class),
                mock(TopicUnitDirectory.class), security);

        assertThat(service.countBusinessTopics()).isEqualTo(6);
        verify(topics, never()).count(null, null, "DRAFT", true);
    }
}
