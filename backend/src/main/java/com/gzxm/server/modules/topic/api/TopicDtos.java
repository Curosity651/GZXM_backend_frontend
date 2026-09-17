package com.gzxm.server.modules.topic.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public final class TopicDtos {
    private TopicDtos() {}
    public record TopicWriteRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 300) String name,
            String summary,
            @NotBlank @Pattern(regexp = "[1-9][0-9]*") String leadUnitId,
            List<@NotBlank @Pattern(regexp = "[1-9][0-9]*") String> participantUnitIds,
            LocalDate startDate, LocalDate endDate,
            @Positive Integer recordVersion) {}
    public record TopicStatusRequest(@NotNull Boolean enabled,
            @Pattern(regexp = "DRAFT|ACTIVE|PAUSED|CLOSED") String status) {}
    public record ParticipantRequest(@NotBlank @Pattern(regexp = "[1-9][0-9]*") String unitId) {}
    public record MembershipStatusRequest(@NotNull Boolean enabled) {}
    public record TopicView(String id, String code, String name, String summary, String leadUnitId,
                            String status, boolean enabled, LocalDate startDate, LocalDate endDate,
                            int recordVersion, List<MembershipView> members) {}
    public record MembershipView(String id, String topicId, String unitId, String unitName,
                                 String membershipType, boolean enabled) {}
}
