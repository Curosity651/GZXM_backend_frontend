package com.gzxm.server.modules.indicator.api;

import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import com.gzxm.server.modules.indicator.application.TopicIndicatorService;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/topics/{topicId}")
@Tag(name="Indicators")
public class TopicIndicatorController {
    private final TopicIndicatorService service;
    private final AuditService audit;
    public TopicIndicatorController(TopicIndicatorService service,AuditService audit) { this.service=service; this.audit=audit; }

    @GetMapping("/indicator-targets")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId="listTopicIndicatorTargets")
    public ResponseEntity<List<TargetView>> list(@PathVariable String topicId,@RequestParam String nodeId,
                                                @RequestParam(defaultValue="effective") String view) {
        var result=service.list(TopicService.id(topicId),TopicService.id(nodeId),view);
        var response=ResponseEntity.ok();
        if ("draft".equals(view)) response.header("X-Draft-Version",Integer.toString(result.draftVersion()));
        return response.body(result.targets());
    }

    @PutMapping("/indicator-targets")
    @PreAuthorize("hasAuthority('indicator.manage')")
    @Operation(operationId="saveTopicIndicatorTargets")
    public ResponseEntity<List<TargetView>> save(@PathVariable String topicId,@Valid @RequestBody TargetBatch request) {
        var result=service.save(TopicService.id(topicId),request);
        audit.success("indicator.draft.save","TOPIC",topicId);
        return ResponseEntity.ok().header("X-Draft-Version",Integer.toString(result.draftVersion())).body(result.targets());
    }

    @PostMapping("/indicator-targets:publish")
    @PreAuthorize("hasAuthority('topic-indicator.publish')")
    @Operation(operationId="publishTopicIndicatorTargets")
    public ResponseEntity<Void> publish(@PathVariable String topicId,@Valid @RequestBody PublishRequest request,
                                        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        if (service.publish(TopicService.id(topicId),request,key)) audit.success("indicator.publish","TOPIC",topicId);
        return ResponseEntity.noContent().build();
    }
}
