package com.gzxm.server.modules.topic.application;

import com.gzxm.server.modules.topic.repository.TopicMembershipMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class DefaultTopicIdentityFacts implements TopicIdentityFacts {
    private final TopicMembershipMapper members;

    public DefaultTopicIdentityFacts(TopicMembershipMapper members) { this.members = members; }

    @Override
    public List<TopicQueryService.Member> activeMembershipsForUnit(long unitId) {
        TopicService.id(Long.toString(unitId));
        return members.activeForUnit(unitId).stream().map(member -> new TopicQueryService.Member(
                member.getId(), member.getTopicId(), member.getUnitId(),
                member.getMembershipType(), member.isEnabled())).toList();
    }
}
