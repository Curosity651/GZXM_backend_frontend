package com.gzxm.server.modules.topic.application;

import com.gzxm.server.modules.topic.repository.TopicMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DefaultTopicQueryService implements TopicQueryService {
    private final TopicService topics;
    private final TopicMapper mapper;
    private final com.gzxm.server.common.security.SecurityContextFacade security;

    public DefaultTopicQueryService(TopicService topics, TopicMapper mapper,
                                   com.gzxm.server.common.security.SecurityContextFacade security) {
        this.topics = topics;
        this.mapper = mapper;
        this.security = security;
    }

    @Override
    public boolean canReadTopic(long topicId) {
        return topics.canRead(topicId);
    }

    @Override
    public long currentProjectId() {
        security.requireCurrentUser();
        var projects = mapper.configuredProjects();
        if (projects.size() != 1)
            throw com.gzxm.server.common.exception.BusinessException.conflict(
                    "PROJECT_CONFIGURATION_REQUIRED", "必须先配置且仅配置一个有效重点项目");
        return projects.getFirst();
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public TopicSummary lockTopic(long topicId) {
        security.requireCurrentUser();
        TopicService.id(Long.toString(topicId));
        var entity = mapper.lock(topicId);
        if (entity == null) throw com.gzxm.server.common.exception.BusinessException.notFound("TOPIC_NOT_FOUND", "课题不存在");
        topics.get(topicId);
        return new TopicSummary(topicId, entity.getProjectId(), entity.getCode(), entity.getName(),
                entity.getLeadUnitId(), entity.getStatus(), entity.isEnabled());
    }

    @Override
    public TopicSummary getTopic(long topicId) {
        // Reuse the same authorization as HTTP reads, within one consistent transaction snapshot.
        var visible = topics.get(topicId);
        var entity = mapper.find(topicId);
        return new TopicSummary(topicId, entity.getProjectId(), visible.code(), visible.name(),
                Long.parseLong(visible.leadUnitId()), visible.status(), visible.enabled());
    }

    @Override
    public List<Member> listMembers(long topicId, boolean includeDisabled) {
        return topics.listMembers(topicId).stream()
                .filter(member -> includeDisabled || member.enabled())
                .map(member -> new Member(Long.parseLong(member.id()), topicId,
                        Long.parseLong(member.unitId()), member.membershipType(), member.enabled()))
                .toList();
    }

    @Override
    public boolean isLeadUnit(long topicId, long unitId) {
        TopicService.id(Long.toString(unitId));
        var topic = getTopic(topicId);
        return topic.leadUnitId() == unitId && listMembers(topicId, false).stream()
                .anyMatch(member -> member.unitId() == unitId && "LEAD".equals(member.membershipType()));
    }
}
