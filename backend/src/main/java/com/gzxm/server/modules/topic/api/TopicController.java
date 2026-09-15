package com.gzxm.server.modules.topic.api;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.topic.api.TopicDtos.*;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
@Tag(name = "Topics")
public class TopicController {
    private final TopicService service;
    private final AuditService audit;

    public TopicController(TopicService service, AuditService audit) { this.service = service; this.audit = audit; }

    @GetMapping
    @Operation(operationId = "listTopics")
    @PreAuthorize("isAuthenticated()")
    public PageResult<TopicView> list(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "20") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String status,
                                     @RequestParam(required = false) Boolean enabled) {
        return service.list(page, size, keyword, status, enabled);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createTopic")
    @PreAuthorize("hasAuthority('topic.manage')")
    public TopicView create(@Valid @RequestBody TopicWriteRequest request) {
        var result = service.create(request);
        audit.success("topic.create", "TOPIC", result.id());
        return result;
    }

    @GetMapping("/{topicId}")
    @Operation(operationId = "getTopic")
    @PreAuthorize("isAuthenticated()")
    public TopicView get(@PathVariable String topicId) { return service.get(TopicService.id(topicId)); }

    @PutMapping("/{topicId}")
    @Operation(operationId = "updateTopic")
    @PreAuthorize("hasAuthority('topic.manage')")
    public TopicView update(@PathVariable String topicId, @Valid @RequestBody TopicWriteRequest request) {
        var result = service.update(TopicService.id(topicId), request);
        audit.success("topic.update", "TOPIC", result.id());
        return result;
    }

    @PutMapping("/{topicId}/status")
    @Operation(operationId = "setTopicStatus")
    @PreAuthorize("hasAuthority('topic.manage')")
    public TopicView status(@PathVariable String topicId, @Valid @RequestBody TopicStatusRequest request) {
        var result = service.setStatus(TopicService.id(topicId), request);
        audit.success("topic.status", "TOPIC", result.id());
        return result;
    }

    @GetMapping("/{topicId}/members")
    @Operation(operationId = "listTopicMembers")
    @PreAuthorize("isAuthenticated()")
    public List<MembershipView> members(@PathVariable String topicId) { return service.listMembers(TopicService.id(topicId)); }

    @PostMapping("/{topicId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "addTopicParticipant")
    @PreAuthorize("hasAuthority('topic-unit.manage')")
    public MembershipView add(@PathVariable String topicId, @Valid @RequestBody ParticipantRequest request) {
        var result = service.addParticipant(TopicService.id(topicId), request);
        audit.success("topic.member.add", "TOPIC_MEMBERSHIP", result.id());
        return result;
    }

    @PutMapping("/{topicId}/members/{membershipId}/status")
    @Operation(operationId = "setTopicMembershipStatus")
    @PreAuthorize("hasAuthority('topic-unit.manage')")
    public MembershipView memberStatus(@PathVariable String topicId, @PathVariable String membershipId,
                                        @Valid @RequestBody MembershipStatusRequest request) {
        var result = service.setMembershipStatus(TopicService.id(topicId), TopicService.id(membershipId), request);
        audit.success("topic.member.status", "TOPIC_MEMBERSHIP", result.id());
        return result;
    }
}
