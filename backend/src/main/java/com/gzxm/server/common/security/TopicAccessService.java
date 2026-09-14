package com.gzxm.server.common.security;

public interface TopicAccessService {
    void requireMember(long topicId);
    void requireLead(long topicId);
    void requireInternalUnit();
    void requireOwnedByCurrentUnit(long unitId);
}
