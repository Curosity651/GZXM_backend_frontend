package com.gzxm.server.modules.dashboard.api;

import com.gzxm.server.modules.archive.application.ArchiveService;
import com.gzxm.server.modules.report.application.ReportService;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {
    private final TopicService topics;
    private final ReportService reports;
    private final ArchiveService archives;
    public DashboardController(TopicService topics, ReportService reports, ArchiveService archives) {
        this.topics = topics; this.reports = reports; this.archives = archives;
    }
    @GetMapping("/summary")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getDashboardSummary")
    public Map<String, Object> summary() {
        var report = reports.progress(null, null);
        var archive = archives.progress(null, null, null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicCount", topics.countBusinessTopics());
        result.put("reportTotal", report.get("total"));
        result.put("reportApproved", report.get("approved"));
        result.put("reportOverdue", report.get("overdue"));
        result.put("archiveRequired", archive.stream().mapToInt(row -> row.requiredCount()).sum());
        result.put("archiveCompleted", archive.stream().mapToInt(row -> row.completedCount()).sum());
        result.put("pendingReports", reports.list(1, 200, null, null, null, null, null, true).total());
        return result;
    }
}
