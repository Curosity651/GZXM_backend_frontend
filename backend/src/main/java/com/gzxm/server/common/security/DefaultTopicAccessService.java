package com.gzxm.server.common.security;

import com.gzxm.server.common.exception.BusinessException;
import org.springframework.stereotype.Service;

@Service
public class DefaultTopicAccessService implements TopicAccessService {
    private final SecurityContextFacade security;

    public DefaultTopicAccessService(SecurityContextFacade security) {
        this.security = security;
    }

    @Override
    public void requireMember(long topicId) {
        CurrentUser user = security.requireCurrentUser();
        if (user.isGlobalRole()) return;
        boolean member = user.memberships().stream().anyMatch(m -> m.topicId() == topicId && m.enabled());
        if (!member) throw BusinessException.forbidden("TOPIC_SCOPE_DENIED", "当前单位不属于该课题");
    }

    @Override
    public void requireLead(long topicId) {
        CurrentUser user = security.requireCurrentUser();
        if (user.isGlobalRole()) return;
        boolean lead = user.memberships().stream().anyMatch(m -> m.topicId() == topicId && m.enabled() && "LEAD".equals(m.membershipType()));
        if (!lead) throw BusinessException.forbidden("TOPIC_LEAD_REQUIRED", "只有该课题牵头单位可以执行此操作");
    }

    @Override
    public void requireInternalUnit() {
        CurrentUser user = security.requireCurrentUser();
        if (!user.isGlobalRole() && !user.isInternalUnit()) {
            throw BusinessException.forbidden("INTERNAL_UNIT_REQUIRED", "外部课题单位不能访问配套自筹业务");
        }
    }

    @Override
    public void requireOwnedByCurrentUnit(long unitId) {
        CurrentUser user = security.requireCurrentUser();
        if (!user.isGlobalRole() && (user.unitId() == null || user.unitId() != unitId)) {
            throw BusinessException.forbidden("UNIT_SCOPE_DENIED", "不能修改其他单位的数据");
        }
    }
}
