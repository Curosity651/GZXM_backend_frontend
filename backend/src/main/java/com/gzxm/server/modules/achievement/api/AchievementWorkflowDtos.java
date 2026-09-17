package com.gzxm.server.modules.achievement.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.*;

public final class AchievementWorkflowDtos {
    private AchievementWorkflowDtos() {}
    public record ActionRequest(@NotBlank @Pattern(regexp="SUBMIT_PRE_REVIEW|REGISTER_EXTERNAL_SUBMISSION|START_FORMAL|SUBMIT_FORMAL|SUBMIT_SUPPLEMENT") String action,
                                @NotNull @Positive @JsonDeserialize(using=AchievementIntegerDeserializer.class) Integer recordVersion,
                                @Pattern(regexp="[0-9]{4}-[0-9]{2}-[0-9]{2}") String externalSubmissionDate,
                                @Size(max=500) String externalSubmissionNumber) {}
    public record ReviewRequest(@NotBlank @Pattern(regexp="APPROVE|RETURN") String decision,@Size(max=1000) String opinion,
                                @NotNull @Positive @JsonDeserialize(using=AchievementIntegerDeserializer.class) Integer submittedVersion,
                                @NotNull @Positive @JsonDeserialize(using=AchievementIntegerDeserializer.class) Integer recordVersion) {}
    public record Outcome<T>(T value,boolean replay) {}
}
