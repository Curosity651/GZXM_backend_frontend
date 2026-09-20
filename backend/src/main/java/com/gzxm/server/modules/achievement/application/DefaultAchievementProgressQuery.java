package com.gzxm.server.modules.achievement.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.achievement.api.AchievementProgressDtos.*;
import com.gzxm.server.modules.achievement.repository.AchievementProgressMapper;
import com.gzxm.server.modules.achievement.repository.AchievementProgressMapper.Fact;
import com.gzxm.server.modules.indicator.application.IndicatorProgressQuery;
import com.gzxm.server.modules.indicator.application.IndicatorProgressQuery.*;
import com.gzxm.server.modules.topic.application.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true)
public class DefaultAchievementProgressQuery implements AchievementProgressQuery {
    private static final List<String> TYPES=List.of("PAPER","PATENT","COPYRIGHT","STANDARD","TALENT");
    private static final Set<String> ASSISTANT_VISIBLE_STATES=Set.of(
            "PRE_INITIAL","PRE_FINAL","PRE_APPROVED","EXTERNAL_SUBMITTED","FORMAL_INITIAL","FORMAL_FINAL",
            "WAIT_PUBLICATION","WAIT_GRANT","SUPPLEMENT_INITIAL","SUPPLEMENT_FINAL","EFFECTIVE");
    private static final Set<String> LEADER_VISIBLE_STATES=Set.of(
            "PRE_FINAL","PRE_APPROVED","EXTERNAL_SUBMITTED","FORMAL_FINAL","WAIT_PUBLICATION","WAIT_GRANT","SUPPLEMENT_FINAL","EFFECTIVE");
    private static final Set<String> INDICATOR_COUNTABLE_STATES=Set.of(
            "WAIT_PUBLICATION","WAIT_GRANT","SUPPLEMENT_INITIAL","SUPPLEMENT_FINAL","SUPPLEMENT_RETURNED","EFFECTIVE");
    private final IndicatorProgressQuery indicators;
    private final TopicQueryService topics;
    private final AchievementProgressMapper mapper;
    private final SecurityContextFacade security;
    private final ObjectMapper json;
    public DefaultAchievementProgressQuery(IndicatorProgressQuery indicators,TopicQueryService topics,AchievementProgressMapper mapper,SecurityContextFacade security,ObjectMapper json) {
        this.indicators=indicators;this.topics=topics;this.mapper=mapper;this.security=security;this.json=json;
    }
    @Override public ProgressView progress(Long topicId,long nodeId,Long unitId) {
        var node=indicators.node(nodeId);var user=security.requireCurrentUser();
        if(unitId!=null) TopicService.id(unitId.toString());
        var selected=topicId==null?topics.listReadableTopics(node.projectId()):List.of(topics.getTopic(topicId));
        var rows=new ArrayList<Row>();var special=new ArrayList<Row>();var totalFacts=new ArrayList<Fact>();
        for(var topic:selected) {
            if(topicId==null && unitId!=null) {
                if(!user.isGlobalRole() && !unitId.equals(user.unitId()) && !topics.isLeadUnit(topic.id(),user.unitId())) continue;
                if(topics.listMembers(topic.id(),true).stream().noneMatch(member->member.unitId()==unitId)) continue;
            }
            var context=indicators.targets(topic.id(),nodeId,unitId);
            var facts=facts(context).stream().filter(fact->visibleTo(user,fact,topic.id())).toList();
            var historical=context.units().stream().filter(Unit::historical).map(Unit::id).collect(Collectors.toSet());
            var activeFacts=facts.stream().filter(fact->!historical.contains(fact.unitId())).toList();
            totalFacts.addAll(activeFacts);
            var detail=new HashMap<Long,JsonNode>();
            for(var fact:facts) {
                if(!TYPES.contains(fact.achievementType())) throw BusinessException.validation("INVALID_ACHIEVEMENT_STATISTICS_DATA","成果类型未正确配置");
                try {detail.put(fact.id(),json.readTree(fact.detailJson()));}
                catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid persisted achievement detail",ex);}
            }
            for(var definition:context.definitions()) {
                var matched=facts.stream().filter(fact->matches(fact,definition,detail.get(fact.id()))).toList();
                var output="SPECIAL".equals(definition.category())?special:rows;
                if(context.topicAggregateVisible()) {
                    var target=context.topicTargets().stream().filter(value->value.definitionId()==definition.id()).findFirst().orElse(null);
                    output.add(row("TOPIC",context,definition,null,false,target==null?null:target.quantity(),target==null?null:target.version(),
                            matched.stream().filter(fact->!historical.contains(fact.unitId())).toList()));
                }
                for(var unit:context.units()) {
                    var target=context.unitTargets().stream().filter(value->value.unitId()==unit.id() && value.definitionId()==definition.id()).findFirst().orElse(null);
                    output.add(row("UNIT",context,definition,unit.id(),unit.historical(),target==null?null:target.quantity(),target==null?null:target.version(),
                            matched.stream().filter(fact->fact.unitId()==unit.id()).toList()));
                }
            }
        }
        var totals=new LinkedHashMap<String,Long>();
        for(String type:TYPES) totals.put(type,totalFacts.stream().filter(fact->type.equals(fact.achievementType()) && effective(fact)).count());
        return new ProgressView(Long.toString(nodeId),"CUMULATIVE_NODE_CURRENT_FACTS",totals,stages(totalFacts),special,rows);
    }
    @Override public List<EffectiveAchievement> effectiveAchievements(long topicId,long nodeId,Long unitId) {
        var context=indicators.targets(topicId,nodeId,unitId);
        var historical=context.units().stream().filter(Unit::historical).map(Unit::id).collect(Collectors.toSet());
        return facts(context).stream().filter(DefaultAchievementProgressQuery::effective).map(fact->new EffectiveAchievement(Long.toString(fact.id()),Long.toString(topicId),
                Long.toString(fact.unitId()),Long.toString(fact.nodeId()),Long.toString(fact.definitionId()),fact.achievementType(),historical.contains(fact.unitId()))).toList();
    }
    private List<Fact> facts(Context context) {
        if(context.units().isEmpty()) return List.of();
        return mapper.facts(context.topicId(),context.cumulativeNodeIds(),context.units().stream().map(Unit::id).toList());
    }
    private boolean visibleTo(CurrentUser user,Fact fact,long topicId) {
        if("RESEARCH_ASSISTANT".equals(user.roleCode())) return ASSISTANT_VISIBLE_STATES.contains(fact.status());
        if("PROJECT_TECH_LEADER".equals(user.roleCode())) return LEADER_VISIBLE_STATES.contains(fact.status());
        if(user.isGlobalRole() || Objects.equals(user.unitId(),fact.unitId())) return true;
        return topics.isLeadUnit(topicId,user.unitId()) && !Set.of("DRAFT","FORMAL_DRAFT").contains(fact.status());
    }
    private boolean matches(Fact fact,Definition definition,JsonNode detail) {
        if(!definition.achievementType().equals(fact.achievementType())) return false;
        // A fact contributes once to its base total. Special rows are independent subsets, so the
        // same fact may match several true conditions (for example core journal and first author).
        if("BASE".equals(definition.category())) return fact.definitionId()==definition.id();
        return detail!=null && detail.path(definition.matchField()).isBoolean() && detail.path(definition.matchField()).booleanValue();
    }
    private Row row(String scope,Context context,Definition definition,Long unit,boolean historical,Long target,Integer version,List<Fact> facts) {
        var counts=stages(facts);boolean hasTarget=target!=null && target>0;
        BigDecimal rate=hasTarget?BigDecimal.valueOf(counts.effective()).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(target),2,RoundingMode.HALF_UP):null;
        return new Row(scope,Long.toString(context.topicId()),unit==null?null:unit.toString(),Long.toString(context.node().id()),Long.toString(definition.id()),definition.achievementType(),
                target,version,target!=null,hasTarget,rate,historical,counts);
    }
    private static boolean effective(Fact fact) {
        return fact.countsToIndicator() && INDICATOR_COUNTABLE_STATES.contains(fact.status());
    }
    private Stages stages(List<Fact> facts) {
        return new Stages(facts.size(),facts.stream().filter(fact->!"DRAFT".equals(fact.status())).count(),facts.stream().filter(Fact::preApproved).count(),
                facts.stream().filter(fact->Set.of("PAPER","PATENT").contains(fact.achievementType()) && fact.external()).count(),
                facts.stream().filter(Fact::formal).count(),facts.stream().filter(Fact::supplement).count(),facts.stream().filter(DefaultAchievementProgressQuery::effective).count());
    }
}
