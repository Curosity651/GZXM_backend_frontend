package com.gzxm.server.modules.indicator.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import com.gzxm.server.modules.indicator.repository.IndicatorCatalogMapper;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IndicatorCatalogService {
    private final IndicatorCatalogMapper catalog;
    private final TopicQueryService topics;
    private final SecurityContextFacade security;

    public IndicatorCatalogService(IndicatorCatalogMapper catalog, TopicQueryService topics, SecurityContextFacade security) {
        this.catalog = catalog; this.topics = topics; this.security = security;
    }

    public List<TimeNodeView> nodes(boolean includeDisabled) {
        requirePage();
        if (includeDisabled) requireManager();
        return catalog.nodes(topics.currentProjectId(), includeDisabled);
    }

    @Transactional
    public TimeNodeView createNode(TimeNodeWrite request) {
        requireManager();
        long projectId = topics.currentProjectId();
        requireSortAvailable(projectId, request.sortOrder(), 0);
        String code = "NODE_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        catalog.insertNode(projectId, code, request.name().trim(), request.deadline(), request.sortOrder());
        return catalog.node(catalog.lastId());
    }

    @Transactional
    public TimeNodeView updateNode(long id, TimeNodeWrite request) {
        requireManager();
        long projectId = topics.currentProjectId();
        TimeNodeView current = requireNode(id, projectId);
        if (current.sortOrder() != request.sortOrder() && catalog.nodeUsage(id) > 0)
            throw BusinessException.conflict("TIME_NODE_ORDER_LOCKED", "该时间节点已经产生指标数据，不能调整顺序");
        requireSortAvailable(projectId, request.sortOrder(), id);
        catalog.updateNode(id, request.name().trim(), request.deadline(), request.sortOrder());
        return catalog.node(id);
    }

    @Transactional
    public TimeNodeView setNodeStatus(long id, boolean enabled) {
        requireManager();
        long projectId = topics.currentProjectId();
        requireNode(id, projectId);
        catalog.setNodeStatus(id, enabled);
        return catalog.node(id);
    }

    public List<DefinitionView> definitions() {
        requirePage();
        return catalog.definitions();
    }

    private void requirePage() {
        var user = security.requireCurrentUser();
        if (!user.authorities().contains("page:topic-indicator"))
            throw BusinessException.forbidden("INDICATOR_PAGE_REQUIRED", "需要科研指标页面权限");
    }

    private void requireManager() {
        var user = security.requireCurrentUser();
        if (!"RESEARCH_ASSISTANT".equals(user.roleCode()) || !user.authorities().contains("indicator.manage"))
            throw BusinessException.forbidden("INDICATOR_MANAGER_REQUIRED", "只有科研助理可以配置时间节点");
    }

    private TimeNodeView requireNode(long id, long projectId) {
        TimeNodeView node = catalog.node(id);
        if (node == null || catalog.nodes(projectId, true).stream().noneMatch(item -> item.id().equals(Long.toString(id))))
            throw BusinessException.notFound("TIME_NODE_NOT_FOUND", "时间节点不存在");
        return node;
    }

    private void requireSortAvailable(long projectId, int sortOrder, long excludeId) {
        if (catalog.countSortOrder(projectId, sortOrder, excludeId) > 0)
            throw BusinessException.conflict("TIME_NODE_SORT_EXISTS", "排序序号已经被其他时间节点使用");
    }
}
