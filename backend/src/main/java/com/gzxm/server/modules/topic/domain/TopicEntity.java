package com.gzxm.server.modules.topic.domain;

import lombok.Data;
import java.time.LocalDate;

@Data
public class TopicEntity {
    private Long id;
    private Long projectId;
    private String code;
    private String name;
    private String summary;
    private Long leadUnitId;
    private String status;
    private boolean enabled;
    private LocalDate startDate;
    private LocalDate endDate;
    private int recordVersion;
    private Long createdBy;
    private Long updatedBy;
}
