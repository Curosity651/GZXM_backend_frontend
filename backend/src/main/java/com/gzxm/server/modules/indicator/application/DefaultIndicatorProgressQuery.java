package com.gzxm.server.modules.indicator.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.*;
import com.gzxm.server.modules.indicator.repository.IndicatorProgressMapper;
import com.gzxm.server.modules.system.application.SystemService;
import com.gzxm.server.modules.topic.application.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true)
public class DefaultIndicatorProgressQuery implements IndicatorProgressQuery {
    private static final Map<String,String> MATCH_FIELDS=Map.of("isChineseCoreJournal","PAPER","isPowerGridFirstAuthor","PAPER",
            "isPowerGridFirstApplicant","PATENT","isPowerGridFirstCopyrightOwner","COPYRIGHT");
    private final IndicatorProgressMapper mapper;
    private final TopicQueryService topics;
    private final SecurityContextFacade security;
    private final SystemService system;
    private final ObjectMapper json;
    public DefaultIndicatorProgressQuery(IndicatorProgressMapper mapper,TopicQueryService topics,SecurityContextFacade security,SystemService system,ObjectMapper json) {
        this.mapper=mapper;this.topics=topics;this.security=security;this.system=system;this.json=json;
    }
    @Override public Node node(long nodeId) {
        reader();TopicService.id(Long.toString(nodeId));
        var node=mapper.node(nodeId);
        if(node==null) throw BusinessException.notFound("TIME_NODE_NOT_FOUND","统计节点不存在");
        if(node.projectId()!=topics.currentProjectId()) throw BusinessException.validation("INVALID_TIME_NODE","节点不属于当前配置项目");
        return node;
    }
    @Override public Context targets(long topicId,long nodeId,Long unitId) {
        var user=reader();if(unitId!=null) TopicService.id(unitId.toString());
        var topic=topics.getTopic(topicId);var node=node(nodeId);
        if(topic.projectId()!=node.projectId()) throw BusinessException.validation("INVALID_TIME_NODE","课题和统计节点不属于同一项目");
        boolean all=user.isGlobalRole() || topics.isLeadUnit(topicId,user.unitId());
        if(unitId!=null && !all && !unitId.equals(user.unitId())) throw BusinessException.forbidden("PROGRESS_UNIT_SCOPE_DENIED","不能查询其他单位统计");
        var enabledUnits=system.listUnits(null,null).stream().filter(unit->unit.enabled()).map(unit->Long.parseLong(unit.id())).collect(Collectors.toSet());
        if(!user.isGlobalRole() && !enabledUnits.contains(user.unitId())) throw BusinessException.forbidden("PROGRESS_UNIT_DISABLED","停用单位无统计读取权限");
        var scopes=topics.listMembers(topicId,true).stream()
                .filter(member->(all || member.unitId()==user.unitId()) && (unitId==null || member.unitId()==unitId))
                .map(member->new Unit(member.unitId(),!member.enabled() || !enabledUnits.contains(member.unitId())))
                .filter(unit->user.isGlobalRole() || !unit.historical()).sorted(Comparator.comparingLong(Unit::id)).toList();
        if(unitId!=null && scopes.isEmpty()) throw BusinessException.forbidden("PROGRESS_UNIT_SCOPE_DENIED","该单位不在可见统计范围");
        var ids=scopes.stream().map(Unit::id).collect(Collectors.toSet());
        var targets=mapper.targets(topicId,nodeId);
        var allocations=mapper.allocations(topicId,nodeId).stream().filter(row->ids.contains(row.unitId())).toList();
        var used=new HashSet<Long>();targets.forEach(row->used.add(row.definitionId()));allocations.forEach(row->used.add(row.definitionId()));
        // Disabled historical definitions remain resolvable when a selected-node target refers to them.
        var definitions=mapper.definitions().stream().filter(row->row.enabled() || used.contains(row.id()) || "BASE".equals(row.category()))
                .map(row->new Definition(row.id(),row.code(),row.name(),row.achievementType(),row.category(),row.enabled(),matchField(row))).toList();
        return new Context(topicId,node,mapper.nodes(node.projectId(),node.sortOrder()),definitions,
                all && unitId==null?targets:List.of(),allocations,scopes,all && unitId==null);
    }
    private String matchField(IndicatorProgressMapper.DefinitionRow row) {
        if(!Set.of("PAPER","PATENT","COPYRIGHT","STANDARD","TALENT").contains(row.achievementType())) throw configuration(row.id());
        if("BASE".equals(row.category())) return null;
        try {
            var rule=row.matchRule()==null?null:json.readTree(row.matchRule());
            if(!"SPECIAL".equals(row.category()) || rule==null || !rule.isObject() || rule.size()!=2 || !rule.path("field").isTextual()
                    || !rule.path("equals").isBoolean() || !rule.path("equals").asBoolean()
                    || !row.achievementType().equals(MATCH_FIELDS.get(rule.path("field").asText()))) throw configuration(row.id());
            return rule.path("field").asText();
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw configuration(row.id());}
    }
    private BusinessException configuration(long id) {
        return BusinessException.validation("INVALID_SPECIAL_INDICATOR_RULE","指标"+id+"的类型或专项field/equals规则未正确配置");
    }
    private CurrentUser reader() {
        var user=security.requireCurrentUser();
        if(!user.isGlobalRole() && (!(user.isInternalUnit() || user.isExternalUnit()) || user.unitId()==null))
            throw BusinessException.forbidden("PROGRESS_SCOPE_DENIED","当前账号无成果统计范围");
        if(!user.isGlobalRole() && system.listUnits(null,null).stream().noneMatch(unit->unit.enabled() && unit.id().equals(user.unitId().toString())))
            throw BusinessException.forbidden("PROGRESS_UNIT_DISABLED","停用单位无统计读取权限");
        return user;
    }
}
