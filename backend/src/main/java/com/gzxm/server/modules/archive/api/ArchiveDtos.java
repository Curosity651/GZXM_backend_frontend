package com.gzxm.server.modules.archive.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public final class ArchiveDtos {
    private ArchiveDtos() {}
    public record Directory(String topicId, String topicName, String unitId, String unitName,
                            int folderCount, int completedCount, double completionRate) {}
    public record Folder(String id, String topicId, String unitId, String ownerType, String ownerId,
                         String name, boolean required, int requiredQuantity, boolean custom,
                         int fileCount, boolean completed, boolean canDelete) {}
    public record FolderCreate(@NotBlank @Size(max = 200) String name, Boolean required) {}
    public record FileLink(@NotBlank String fileId) {}
    public record ProjectWrite(@NotBlank String topicId, @NotBlank @Size(max = 100) String code,
                               @NotBlank @Size(max = 300) String name, @NotBlank String projectType,
                               @NotBlank @Size(max = 100) String principalName, @NotNull LocalDate startDate,
                               @NotNull LocalDate endDate, @DecimalMin("0") BigDecimal budget, String status,
                               Integer recordVersion) {}
    public record Project(String id, String topicId, String ownerUnitId, String code, String name,
                          String projectType, String principalName, LocalDate startDate, LocalDate endDate,
                          BigDecimal budget, String status, int recordVersion, String templateSnapshotId,
                          double completionRate) {}
    public record Progress(String topicId, String unitId, String ownerType, int requiredCount,
                           int completedCount, double completionRate) {}
}
