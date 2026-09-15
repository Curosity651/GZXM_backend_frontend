package com.gzxm.server.modules.topic.application;

import java.util.List;

/** Read-only contract for request-bound consumers such as report/archive. */
public interface TopicQueryService {
    TopicSummary getTopic(long topicId);
    List<Member> listMembers(long topicId, boolean includeDisabled);
    /** Identity fact only; consumers must separately authorize their own write actions. */
    boolean isLeadUnit(long topicId, long unitId);

    record TopicSummary(long id, long projectId, String code, String name, long leadUnitId,
                        String status, boolean enabled) {}
    record Member(long membershipId, long topicId, long unitId, String membershipType, boolean enabled) {}
}
