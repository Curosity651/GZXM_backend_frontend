package com.gzxm.server.modules.indicator.api;

import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import com.gzxm.server.modules.indicator.application.UnitAllocationService;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/topics/{topicId}")
@Tag(name="Indicators")
public class UnitAllocationController {
    private final UnitAllocationService service;
    private final AuditService audit;
    public UnitAllocationController(UnitAllocationService service,AuditService audit) { this.service=service; this.audit=audit; }

    @GetMapping("/unit-allocations")
    @PreAuthorize("isAuthenticated()")
    @Operation(operationId="listUnitAllocations")
    public ResponseEntity<List<AllocationView>> list(@PathVariable String topicId,@RequestParam String nodeId,
                                                    @RequestParam(defaultValue="effective") String view) {
        var result=service.list(TopicService.id(topicId),TopicService.id(nodeId),view);
        var response=ResponseEntity.ok();
        if("draft".equals(view)) response.header("X-Draft-Version",Integer.toString(result.draftVersion()))
                .header("X-Topic-Indicator-Version",Integer.toString(result.topicIndicatorVersion()));
        return response.body(result.allocations());
    }
    @PutMapping("/unit-allocations")
    @PreAuthorize("hasAuthority('unit-allocation.manage')")
    @Operation(operationId="saveUnitAllocations")
    public ResponseEntity<List<AllocationView>> save(@PathVariable String topicId,@Valid @RequestBody AllocationBatch request) {
        var result=service.save(TopicService.id(topicId),request);
        audit.success("allocation.draft.save","TOPIC",topicId);
        return ResponseEntity.ok().header("X-Draft-Version",Integer.toString(result.draftVersion()))
                .header("X-Topic-Indicator-Version",Integer.toString(result.topicIndicatorVersion())).body(result.allocations());
    }
    @PostMapping("/unit-allocations:publish")
    @PreAuthorize("hasAuthority('unit-allocation.publish')")
    @Operation(operationId="publishUnitAllocations")
    public ResponseEntity<Void> publish(@PathVariable String topicId,@Valid @RequestBody PublishRequest request,
                                        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        if(service.publish(TopicService.id(topicId),request,key)) audit.success("allocation.publish","TOPIC",topicId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/unit-allocations:confirm")
    @PreAuthorize("hasAuthority('unit-allocation.manage') and hasAuthority('unit-allocation.publish')")
    @Operation(operationId="confirmUnitAllocations")
    public ResponseEntity<Void> confirm(@PathVariable String topicId,@Valid @RequestBody AllocationBatch request,
                                        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        service.confirm(TopicService.id(topicId), request, key);
        audit.success("allocation.confirm","TOPIC",topicId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/unit-allocations:confirm-plan")
    @PreAuthorize("hasAuthority('unit-allocation.manage') and hasAuthority('unit-allocation.publish')")
    @Operation(operationId="confirmUnitAllocationPlan")
    public ResponseEntity<Void> confirmPlan(@PathVariable String topicId,@Valid @RequestBody AllocationPlanBatch request,
                                            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        service.confirmPlan(TopicService.id(topicId), request, key);
        audit.success("allocation.plan.confirm","TOPIC",topicId);
        return ResponseEntity.noContent().build();
    }
}
