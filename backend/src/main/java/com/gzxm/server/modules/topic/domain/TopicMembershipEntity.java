package com.gzxm.server.modules.topic.domain;

import lombok.Data;

@Data
public class TopicMembershipEntity {
    private Long id;
    private Long topicId;
    private Long unitId;
    private String membershipType;
    private boolean enabled;
    private Long createdBy;
    private Long updatedBy;
}
