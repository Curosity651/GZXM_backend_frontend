package com.gzxm.server.modules.achievement.api;
import com.fasterxml.jackson.databind.JsonNode;
import com.gzxm.server.modules.file.api.FileDtos.FileView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.util.List;

public final class AchievementDtos {
    private AchievementDtos() {}
    public record MaterialInput(@NotBlank @Pattern(regexp="[1-9][0-9]*") String fileId,@NotBlank @Size(max=100) String materialType) {}
    public record WriteRequest(@NotBlank @Pattern(regexp="[1-9][0-9]*") String topicId,
                               @NotBlank @Pattern(regexp="[1-9][0-9]*") String nodeId,
                               @NotBlank @Pattern(regexp="[1-9][0-9]*") String indicatorDefinitionId,
                               @NotBlank @Size(max=500) String title,@NotBlank @Size(max=100) String responsiblePerson,
                               JsonNode detail,@Positive @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using=AchievementIntegerDeserializer.class) Integer recordVersion,
                               @Size(max=100) List<@NotNull @Valid MaterialInput> materialAttachments,
                               List<String> materialFileIds) {}
    public record MaterialLink(String id,String fileId,String materialType,int version,boolean active,String status) {}
    public record AchievementView(String id,String topicId,String unitId,String nodeId,String indicatorDefinitionId,
                                  String achievementType,String title,String responsiblePerson,String status,boolean countsToIndicator,
                                  int recordVersion,int submittedVersion,JsonNode detail,List<FileView> materials,
                                  List<MaterialLink> materialLinks,OffsetDateTime createdAt,OffsetDateTime updatedAt,
                                  List<AchievementHistoryDtos.ApprovalView> approvals) {}
}
