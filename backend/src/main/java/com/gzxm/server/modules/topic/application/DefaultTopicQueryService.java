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

    public DefaultTopicQueryService(TopicService topics, TopicMapper mapper) {
        this.topics = topics;
        this.mapper = mapper;
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
