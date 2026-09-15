package com.gzxm.server.modules.topic.application;

import java.util.List;

/** Trusted in-process authentication adapter only. Never expose this as an HTTP query service.
 * Does not require CurrentUser, and does not grant permission to perform any business action.
 */
public interface TopicIdentityFacts {
    List<TopicQueryService.Member> activeMembershipsForUnit(long unitId);
}
