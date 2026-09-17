package com.gzxm.server.modules.achievement.api;

import com.gzxm.server.modules.achievement.application.AchievementProgressQuery;
import com.gzxm.server.modules.achievement.api.AchievementProgressDtos.ProgressView;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/achievement-progress")
@Tag(name="Achievements")
public class AchievementProgressController {
    private final AchievementProgressQuery query;
    public AchievementProgressController(AchievementProgressQuery query){this.query=query;}
    @GetMapping @PreAuthorize("isAuthenticated()") @Operation(operationId="getAchievementProgress")
    public ProgressView progress(@RequestParam(required=false) String topicId,@RequestParam(required=false) String nodeId,@RequestParam(required=false) String unitId) {
        return query.progress(topicId==null?null:TopicService.id(topicId),TopicService.id(nodeId),unitId==null?null:TopicService.id(unitId));
    }
}
