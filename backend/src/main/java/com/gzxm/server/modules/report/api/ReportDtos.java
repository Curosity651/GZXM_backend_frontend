package com.gzxm.server.modules.report.api;

import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ReportDtos {
    private ReportDtos() {}

    public record Rule(@Min(2000) @Max(2100) int effectiveYear, boolean monthlyEnabled,
                       @Min(1) @Max(31) int monthlyOpenDay, @Min(1) @Max(31) int monthlyDeadlineDay,
                       boolean quarterlyEnabled, @Min(1) @Max(31) int quarterlyOpenDay,
                       @Min(1) @Max(31) int quarterlyDeadlineDay, @NotNull List<Integer> quarterlyMonths,
                       Integer recordVersion) {}
    public record Create(@NotBlank String topicId, @NotBlank String reportType,
                         @Min(2000) @Max(2100) int year, @Min(1) @Max(12) int period) {}
    public record Content(@NotNull String milestoneProgress, @NotNull String overallProgress,
                          @NotNull String demonstrationProgress, @NotNull String fundUsage,
                          @NotNull String nextPlan, @NotNull String problemsAndMeasures,
                          @Positive int recordVersion) {}
    public record View(String id, String topicId, String reportType, int year, int period,
                       LocalDate openDate, LocalDate deadline, String milestoneProgress,
                       String overallProgress, String demonstrationProgress, String fundUsage,
                       String nextPlan, String problemsAndMeasures, String status, boolean overdue,
                       int recordVersion, int submittedVersion, LocalDateTime submittedAt) {}
    public record Review(@NotBlank String decision, String opinion, @Positive int submittedVersion) {}
    public record Approval(String id, String businessType, String businessId, String stage, String level,
                           String decision, String opinion, String operatorId, LocalDateTime operatedAt,
                           int submittedVersion) {}
    public record Snapshot(String id, String businessType, String businessId, String stage,
                           int submittedVersion, LocalDateTime submittedAt, String submitterId,
                           Map<String, Object> payload) {}
}
