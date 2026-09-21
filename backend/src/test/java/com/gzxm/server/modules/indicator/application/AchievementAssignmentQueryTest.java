package com.gzxm.server.modules.indicator.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.indicator.repository.TopicIndicatorMapper;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AchievementAssignmentQueryTest {
    private final TopicQueryService topics = mock(TopicQueryService.class);
    private final TopicIndicatorMapper indicators = mock(TopicIndicatorMapper.class);
    private final SecurityContextFacade security = mock(SecurityContextFacade.class);
    private AchievementAssignmentQuery query;

    @BeforeEach
    void setUp() {
        query = new AchievementAssignmentQuery(topics, indicators, security);
        when(security.requireCurrentUser()).thenReturn(new CurrentUser(101, "unit-user", 20L,
                "INTERNAL_TOPIC_UNIT", Set.of(), List.of(), 0));
        when(topics.getTopic(1)).thenReturn(new TopicQueryService.TopicSummary(1, 9, "T1", "课题", 20,
                "ACTIVE", true));
        when(topics.listMembers(1, false)).thenReturn(List.of(new TopicQueryService.Member(7, 1, 20, "LEAD", true)));
        when(indicators.node(2)).thenReturn(new TopicIndicatorMapper.Node(2, 9, "第一阶段", 1, true));
    }

    @Test
    void baseAchievementCanBeCreatedWithoutPublishedTargetOrUnitAllocation() {
        when(indicators.definitions()).thenReturn(List.of(new TopicIndicatorMapper.Definition(3, "学术论文总数", "PAPER", "BASE", true)));

        var assignment = query.requireEligible(1, 2, 3);

        assertThat(assignment).isEqualTo(new AchievementAssignmentQuery.Assignment(9, 7, 20, "PAPER"));
    }

    @Test
    void specialIndicatorStillCannotBeUsedAsAchievementType() {
        when(indicators.definitions()).thenReturn(List.of(new TopicIndicatorMapper.Definition(4, "其中：中文核心期刊", "PAPER", "SPECIAL", true)));

        assertThatThrownBy(() -> query.requireEligible(1, 2, 4))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("INVALID_ACHIEVEMENT_INDICATOR");
    }
}
