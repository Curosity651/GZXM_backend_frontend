package com.gzxm.server.modules.achievement.api;
import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.achievement.api.AchievementDtos.*;
import com.gzxm.server.modules.achievement.application.AchievementService;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/achievements")
@Tag(name="Achievements")
public class AchievementController {
    private final AchievementService service;private final AuditService audit;
    public AchievementController(AchievementService service,AuditService audit){this.service=service;this.audit=audit;}
    @GetMapping @PreAuthorize("isAuthenticated()") @Operation(operationId="listAchievements")
    public PageResult<AchievementView> list(@RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,
            @RequestParam(required=false) String topicId,@RequestParam(required=false) String nodeId,@RequestParam(required=false) String unitId,
            @RequestParam(required=false) String indicatorDefinitionId,@RequestParam(required=false) String status,@RequestParam(defaultValue="false") boolean pendingForMe) {
        return service.list(page,size,id(topicId),id(nodeId),id(unitId),id(indicatorDefinitionId),status,pendingForMe);
    }
    @GetMapping("/{achievementId}") @PreAuthorize("isAuthenticated()") @Operation(operationId="getAchievement")
    public AchievementView get(@PathVariable String achievementId){return service.get(TopicService.id(achievementId));}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('achievement.submit')") @Operation(operationId="createAchievement")
    public AchievementView create(@Valid @RequestBody WriteRequest request){var result=service.create(request);audit.success("achievement.create","ACHIEVEMENT",result.id());return result;}
    @PutMapping("/{achievementId}") @PreAuthorize("hasAuthority('achievement.submit')") @Operation(operationId="updateAchievement")
    public AchievementView update(@PathVariable String achievementId,@Valid @RequestBody WriteRequest request){var result=service.update(TopicService.id(achievementId),request);audit.success("achievement.update","ACHIEVEMENT",result.id());return result;}
    private Long id(String value){return value==null?null:TopicService.id(value);}
}
