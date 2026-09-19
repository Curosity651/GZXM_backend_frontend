package com.gzxm.server.modules.report.api;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.report.application.ReportService;
import com.gzxm.server.modules.report.api.ReportDtos.*;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Reports")
public class ReportController {
    private final ReportService service;
    private final AuditService audit;
    public ReportController(ReportService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping("/topics/{topicId}/report-rule") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getTopicReportRule")
    public Rule rule(@PathVariable String topicId, @RequestParam(required = false) Integer effectiveYear) {
        return service.rule(TopicService.id(topicId), effectiveYear);
    }

    @PutMapping("/topics/{topicId}/report-rule") @PreAuthorize("hasAuthority('report.rule.manage')")
    @Operation(operationId = "updateTopicReportRule")
    public Rule rule(@PathVariable String topicId, @Valid @RequestBody Rule request) {
        var result = service.saveRule(TopicService.id(topicId), request);
        audit.success("report.rule.update", "TOPIC", topicId); return result;
    }

    @GetMapping("/reports") @PreAuthorize("isAuthenticated()") @Operation(operationId = "listReports")
    public PageResult<View> list(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size,
                                 @RequestParam(required = false) String topicId, @RequestParam(required = false) String reportType,
                                 @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer period,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(defaultValue = "false") boolean pendingForMe) {
        return service.list(page, size, topicId == null ? null : TopicService.id(topicId), reportType, year, period, status, pendingForMe);
    }

    @PostMapping("/reports") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('report.submit')")
    @Operation(operationId = "createReport")
    public View create(@Valid @RequestBody Create request) {
        var result = service.create(request); audit.success("report.create", "REPORT", result.id()); return result;
    }

    @GetMapping("/reports/{reportId}") @PreAuthorize("isAuthenticated()") @Operation(operationId = "getReport")
    public View get(@PathVariable String reportId) { return service.get(TopicService.id(reportId)); }

    @PutMapping("/reports/{reportId}") @PreAuthorize("hasAuthority('report.submit')") @Operation(operationId = "updateReport")
    public View update(@PathVariable String reportId, @Valid @RequestBody Content request) {
        var result = service.update(TopicService.id(reportId), request);
        audit.success("report.update", "REPORT", reportId); return result;
    }

    @PostMapping("/reports/{reportId}:submit") @PreAuthorize("hasAuthority('report.submit')")
    @Operation(operationId = "submitReport")
    public View submit(@PathVariable String reportId, @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var result = service.submit(TopicService.id(reportId), key);
        audit.success("report.submit", "REPORT", reportId); return result;
    }

    @PostMapping("/reports/{reportId}/reviews") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('report.initial.approve') or hasAuthority('report.final.approve')")
    @Operation(operationId = "reviewReport")
    public Approval review(@PathVariable String reportId, @RequestHeader(value = "Idempotency-Key", required = false) String key,
                           @Valid @RequestBody Review request) {
        var result = service.review(TopicService.id(reportId), key, request);
        audit.success("report.review", "REPORT", reportId); return result;
    }

    @GetMapping("/reports/{reportId}/reviews") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listReportReviews")
    public List<Approval> reviews(@PathVariable String reportId) {
        return service.approvals(TopicService.id(reportId));
    }

    @GetMapping("/reports/{reportId}/snapshots") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listReportSnapshots")
    public List<Snapshot> snapshots(@PathVariable String reportId) { return service.snapshots(TopicService.id(reportId)); }

    @GetMapping("/report-progress") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getReportProgress")
    public Map<String, Object> progress(@RequestParam(required = false) String topicId,
                                        @RequestParam(required = false) Integer year) {
        return service.progress(topicId == null ? null : TopicService.id(topicId), year);
    }
}
