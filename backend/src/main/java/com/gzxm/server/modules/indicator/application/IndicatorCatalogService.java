package com.gzxm.server.modules.indicator.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import com.gzxm.server.modules.indicator.repository.IndicatorCatalogMapper;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class IndicatorCatalogService {
    private final IndicatorCatalogMapper catalog;
    private final TopicQueryService topics;
    private final SecurityContextFacade security;

    public IndicatorCatalogService(IndicatorCatalogMapper catalog, TopicQueryService topics, SecurityContextFacade security) {
        this.catalog = catalog; this.topics = topics; this.security = security;
    }

    public List<TimeNodeView> nodes() {
        requirePage();
        return catalog.nodes(topics.currentProjectId());
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
}
