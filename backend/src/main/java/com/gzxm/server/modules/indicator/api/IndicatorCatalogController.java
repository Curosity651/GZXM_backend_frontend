package com.gzxm.server.modules.indicator.api;

import com.gzxm.server.modules.indicator.application.IndicatorCatalogService;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Indicators")
@PreAuthorize("hasAuthority('page:topic-indicator')")
public class IndicatorCatalogController {
    private final IndicatorCatalogService service;
    public IndicatorCatalogController(IndicatorCatalogService service) { this.service = service; }

    @GetMapping("/time-nodes")
    @Operation(operationId = "listTimeNodes")
    public List<TimeNodeView> nodes() { return service.nodes(); }

    @GetMapping("/indicator-definitions")
    @Operation(operationId = "listIndicatorDefinitions")
    public List<DefinitionView> definitions() { return service.definitions(); }
}
