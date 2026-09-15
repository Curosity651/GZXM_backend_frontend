package com.gzxm.server.modules.topic.application;

import com.gzxm.server.modules.system.api.SystemDtos.UnitView;
import com.gzxm.server.modules.system.application.SystemService;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Use A's public application service; no cross-module Mapper/table access. */
@Component
public class TopicUnitDirectory {
    private final SystemService system;
    public TopicUnitDirectory(SystemService system) { this.system = system; }
    public Map<Long, UnitView> snapshot() {
        return system.listUnits(null, null).stream()
                .collect(Collectors.toMap(unit -> Long.parseLong(unit.id()), Function.identity()));
    }
}
