package com.gzxm.server.modules.indicator.application;

import java.util.List;

/** Read-only, current-user-scoped contract for B/C consumers. No Mapper or mutable entity is exposed. */
public interface IndicatorProgressQuery {
    Node node(long nodeId);
    Context targets(long topicId,long nodeId,Long unitId);
    record Node(long id,long projectId,int sortOrder,boolean enabled) {}
    record Definition(long id,String code,String name,String achievementType,String category,boolean enabled,String matchField) {}
    record Target(long definitionId,long quantity,int version) {}
    record Allocation(long unitId,long definitionId,long quantity,int version) {}
    record Unit(long id,boolean historical) {}
    record Context(long topicId,Node node,List<Long> cumulativeNodeIds,List<Definition> definitions,List<Target> topicTargets,
                   List<Allocation> unitTargets,List<Unit> units,boolean topicAggregateVisible) {
        public Context {
            cumulativeNodeIds=List.copyOf(cumulativeNodeIds);definitions=List.copyOf(definitions);topicTargets=List.copyOf(topicTargets);
            unitTargets=List.copyOf(unitTargets);units=List.copyOf(units);
        }
    }
}
