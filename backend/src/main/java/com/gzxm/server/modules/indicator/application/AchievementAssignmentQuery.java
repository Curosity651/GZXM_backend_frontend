package com.gzxm.server.modules.indicator.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.indicator.repository.*;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public indicator boundary for achievement ownership; never expose indicator Mappers to achievement. */
@Service
@Transactional(readOnly=true)
public class AchievementAssignmentQuery {
    public record Assignment(long projectId,long membershipId,long unitId,String achievementType) {}
    private final TopicQueryService topics;
    private final TopicIndicatorMapper indicators;
    private final SecurityContextFacade security;
    public AchievementAssignmentQuery(TopicQueryService topics,TopicIndicatorMapper indicators,SecurityContextFacade security) {
        this.topics=topics; this.indicators=indicators; this.security=security;
    }
    public Assignment requireEligible(long topicId,long nodeId,long definitionId) {
        var user=security.requireCurrentUser();
        if(!(user.isInternalUnit() || user.isExternalUnit()) || user.unitId()==null)
            throw BusinessException.forbidden("ACHIEVEMENT_UNIT_REQUIRED","只有课题成员单位可以填报成果");
        var topic=topics.getTopic(topicId);
        var member=topics.listMembers(topicId,false).stream().filter(row->row.unitId()==user.unitId()).findFirst()
                .orElseThrow(()->BusinessException.forbidden("TOPIC_SCOPE_DENIED","单位不是有效课题成员"));
        var node=indicators.node(nodeId);
        if(node==null || !node.enabled() || node.projectId()!=topic.projectId())
            throw BusinessException.validation("INVALID_TIME_NODE","节点不存在、已停用或不属于课题项目");
        var definition=indicators.definitions().stream().filter(row->row.id()==definitionId).findFirst()
                .orElseThrow(()->BusinessException.validation("INVALID_INDICATOR_DEFINITION","指标不存在"));
        if(!definition.enabled() || !"BASE".equals(definition.category()) || !java.util.Set.of("PAPER","PATENT","COPYRIGHT","STANDARD","TALENT").contains(definition.achievementType()))
            throw BusinessException.validation("INVALID_ACHIEVEMENT_INDICATOR","成果必须绑定启用的五类基础指标");
        return new Assignment(topic.projectId(),member.membershipId(),user.unitId(),definition.achievementType());
    }
}
