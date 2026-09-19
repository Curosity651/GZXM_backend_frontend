package com.gzxm.server.modules.indicator.api;

import com.gzxm.server.modules.indicator.application.IndicatorCatalogService;
import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.topic.application.TopicService;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Indicators")
@PreAuthorize("hasAuthority('page:topic-indicator')")
public class IndicatorCatalogController {
    private final IndicatorCatalogService service;
    private final AuditService audit;
    public IndicatorCatalogController(IndicatorCatalogService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping("/time-nodes")
    @Operation(operationId = "listTimeNodes")
    public List<TimeNodeView> nodes(@RequestParam(defaultValue = "false") boolean includeDisabled) { return service.nodes(includeDisabled); }

    @PostMapping("/time-nodes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('indicator.manage')")
    @Operation(operationId = "createTimeNode")
    public TimeNodeView createNode(@Valid @RequestBody TimeNodeWrite request) {
        var result = service.createNode(request); audit.success("indicator.time-node.create", "TIME_NODE", result.id()); return result;
    }

    @PutMapping("/time-nodes/{nodeId}")
    @PreAuthorize("hasAuthority('indicator.manage')")
    @Operation(operationId = "updateTimeNode")
    public TimeNodeView updateNode(@PathVariable String nodeId, @Valid @RequestBody TimeNodeWrite request) {
        var result = service.updateNode(TopicService.id(nodeId), request); audit.success("indicator.time-node.update", "TIME_NODE", result.id()); return result;
    }

    @PutMapping("/time-nodes/{nodeId}/status")
    @PreAuthorize("hasAuthority('indicator.manage')")
    @Operation(operationId = "setTimeNodeStatus")
    public TimeNodeView setNodeStatus(@PathVariable String nodeId, @Valid @RequestBody TimeNodeStatus request) {
        var result = service.setNodeStatus(TopicService.id(nodeId), request.enabled()); audit.success("indicator.time-node.status", "TIME_NODE", result.id()); return result;
    }

    @DeleteMapping("/time-nodes/{nodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('indicator.manage')")
    @Operation(operationId = "deleteTimeNode")
    public void deleteNode(@PathVariable String nodeId) {
        service.deleteNode(TopicService.id(nodeId));
        audit.success("indicator.time-node.delete", "TIME_NODE", nodeId);
    }

    @GetMapping("/indicator-definitions")
    @Operation(operationId = "listIndicatorDefinitions")
    public List<DefinitionView> definitions() { return service.definitions(); }
}
