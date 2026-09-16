package com.gzxm.server.modules.indicator.api;

import java.time.LocalDate;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public final class IndicatorDtos {
    private IndicatorDtos() {}
    public record TimeNodeView(String id, String name, LocalDate deadline, int sortOrder, boolean enabled) {}
    public record DefinitionView(String id, String code, String name, String achievementType,
                                 String unit, String category, boolean enabled) {}
    public record TargetInput(@NotBlank @Pattern(regexp="[1-9][0-9]*") String indicatorDefinitionId,
                              @NotNull @Min(0) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class) Integer targetQuantity) {}
    public record TargetBatch(@NotBlank @Pattern(regexp="[1-9][0-9]*") String nodeId,
                              @NotNull @Size(max=500) List<@NotNull @Valid TargetInput> targets,
                              @Min(0) @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class) Integer draftVersion) {}
    public record PublishRequest(@NotBlank @Pattern(regexp="[1-9][0-9]*") String nodeId,
                                 @NotNull @Positive @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=StrictIntegerDeserializer.class) Integer draftVersion) {}
    public record TargetView(String id, String topicId, String nodeId, String indicatorDefinitionId,
                             int targetQuantity, String status, int version) {}
    public record DraftResult(int draftVersion, List<TargetView> targets) {}
}
