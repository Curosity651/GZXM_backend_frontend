package com.gzxm.server.modules.indicator.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.*;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import com.gzxm.server.modules.indicator.repository.*;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import com.gzxm.server.modules.topic.application.TopicService;
import com.gzxm.server.modules.system.application.SystemService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UnitAllocationService {
    private record Dimension(long unit,long definition) {}
    private record Context(TopicIndicatorMapper.Node node,Map<Long,TargetView> targets,
                           Map<Long,TopicQueryService.Member> members,int targetVersion) {}
    private final UnitAllocationMapper mapper;
    private final TopicIndicatorMapper indicators;
    private final TopicQueryService topics;
    private final SecurityContextFacade security;
    private final SystemService system;
    private final ObjectMapper json;

    public UnitAllocationService(UnitAllocationMapper mapper,TopicIndicatorMapper indicators,TopicQueryService topics,
                                 SecurityContextFacade security,SystemService system,ObjectMapper json) {
        this.mapper=mapper; this.indicators=indicators; this.topics=topics; this.security=security; this.system=system; this.json=json;
    }

    @Transactional(readOnly=true)
    public AllocationDraftResult list(long topicId,long nodeId,String view) {
        var topic=topics.getTopic(topicId);
        node(nodeId,topic.projectId(),false);
        var user=security.requireCurrentUser();
        if ("effective".equals(view)) {
            Long unit=user.isGlobalRole() || topics.isLeadUnit(topicId,user.unitId())?null:user.unitId();
            return new AllocationDraftResult(0,0,mapper.effective(topicId,nodeId,unit));
        }
        if (!"draft".equals(view)) throw invalid("INVALID_ALLOCATION_VIEW","view必须为effective或draft");
        lead(topicId,"unit-allocation.manage");
        var draft=mapper.draft(topicId,nodeId);
        return new AllocationDraftResult(draft==null?0:draft.draftVersion(),draft==null?0:draft.topicIndicatorVersion(),mapper.draftItems(topicId,nodeId));
    }

    @Transactional
    public AllocationDraftResult save(long topicId,AllocationBatch request) {
        var user=writer("unit-allocation.manage");
        var topic=topics.lockTopic(topicId);
        lead(topicId,"unit-allocation.manage");
        writable(topic);
        long nodeId=TopicService.id(request.nodeId());
        var context=context(topic,nodeId);
        var values=input(request.allocations());
        var draft=mapper.draft(topicId,nodeId);
        int revision=draft==null?0:draft.draftVersion();
        if ((request.draftVersion()==null?0:request.draftVersion())!=revision)
            throw conflict("ALLOCATION_DRAFT_VERSION_CONFLICT","请读取最新分配草稿版本后重试");
        if (revision==Integer.MAX_VALUE) throw conflict("ALLOCATION_VERSION_EXHAUSTED","分配草稿版本已达上限");
        validate(topicId,context,values,false);
        if (draft==null) { mapper.createDraft(topicId,nodeId,context.targetVersion(),user.id()); draft=mapper.draft(topicId,nodeId); }
        mapper.clearDraft(draft.id());
        for(var row:values.entrySet()) mapper.insertDraft(draft.id(),row.getKey().unit(),row.getKey().definition(),row.getValue());
        mapper.bumpDraft(draft.id(),context.targetVersion(),user.id());
        return new AllocationDraftResult(revision+1,context.targetVersion(),mapper.draftItems(topicId,nodeId));
    }

    @Transactional
    public boolean publish(long topicId,PublishRequest request,String key) {
        var user=writer("unit-allocation.publish");
        if (key==null || !key.matches("[!-~]{8,100}")) throw invalid("INVALID_IDEMPOTENCY_KEY","Idempotency-Key必须是8到100位可见ASCII字符");
        long nodeId=TopicService.id(request.nodeId());
        if (request.draftVersion()==null || request.draftVersion()<1) throw invalid("INVALID_DRAFT_VERSION","必须指定正整数draftVersion");
        var topic=topics.lockTopic(topicId);
        // Revoke replay access from former leads too; a saved request key is not authority.
        lead(topicId,"unit-allocation.publish");
        var previous=mapper.publication(user.id(),key);
        if(previous!=null) {
            if(previous.topicId()!=topicId || previous.nodeId()!=nodeId || previous.draftVersion()!=request.draftVersion())
                throw conflict("IDEMPOTENCY_KEY_CONFLICT","该分配发布键已用于不同请求");
            return false;
        }
        writable(topic);
        var context=context(topic,nodeId);
        var draft=mapper.draft(topicId,nodeId);
        if(draft==null || draft.draftVersion()!=request.draftVersion()) throw conflict("ALLOCATION_DRAFT_VERSION_CONFLICT","分配草稿版本不匹配");
        if(draft.draftVersion()==draft.publishedDraftVersion()) throw conflict("ALLOCATION_NO_PENDING_DRAFT","草稿已下发，请使用原请求键重试");
        if(draft.topicIndicatorVersion()!=context.targetVersion()) throw conflict("TOPIC_INDICATOR_VERSION_CHANGED","课题目标已更新，请重新核对并保存分配草稿");
        var rows=mapper.draftItems(topicId,nodeId);
        var values=new LinkedHashMap<Dimension,Integer>();
        rows.forEach(row->values.put(new Dimension(TopicService.id(row.unitId()),TopicService.id(row.indicatorDefinitionId())),row.targetQuantity()));
        if(values.isEmpty()) throw invalid("ALLOCATION_EMPTY_PUBLICATION","空分配草稿不能下发");
        validate(topicId,context,values,true);
        if(draft.publishVersion()==Integer.MAX_VALUE) throw conflict("ALLOCATION_VERSION_EXHAUSTED","分配发布版本已达上限");
        int version=draft.publishVersion()+1;
        try {
            var snapshot=rows.stream().map(row->new AllocationInput(row.unitId(),row.indicatorDefinitionId(),row.targetQuantity())).toList();
            mapper.recordPublication(topicId,nodeId,draft.draftVersion(),version,context.targetVersion(),user.id(),key,json.writeValueAsString(snapshot));
            for(var entry:values.entrySet()) {
                var dimension=entry.getKey();
                mapper.publish(topic.projectId(),topicId,context.members().get(dimension.unit()).membershipId(),dimension.unit(),nodeId,
                        dimension.definition(),TopicService.id(context.targets().get(dimension.definition()).id()),entry.getValue(),version,user.id());
            }
            mapper.markPublished(draft.id(),version,user.id());
        } catch(JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize allocation publication",ex); }
        catch(DuplicateKeyException ex) { throw conflict("IDEMPOTENCY_KEY_CONFLICT","分配发布键已被使用"); }
        return true;
    }

    @Transactional
    public void confirm(long topicId, AllocationBatch request, String key) {
        AllocationDraftResult saved = save(topicId, request);
        publish(topicId, new PublishRequest(request.nodeId(), saved.draftVersion()), key);
    }

    @Transactional
    public void confirmPlan(long topicId, AllocationPlanBatch request, String key) {
        if (key == null || !key.matches("[!-~]{8,80}"))
            throw invalid("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key必须是8到80位可见ASCII字符");
        var topic = topics.lockTopic(topicId);
        lead(topicId, "unit-allocation.publish");
        writable(topic);
        if (request == null || request.stages() == null)
            throw invalid("ALLOCATION_PLAN_REQUIRED", "必须提交全部时间阶段的分配方案");
        var activeNodes = indicators.nodes(topic.projectId());
        var submitted = new LinkedHashMap<Long, AllocationBatch>();
        for (var stage : request.stages()) {
            long stageId = TopicService.id(stage.nodeId());
            if (submitted.putIfAbsent(stageId, stage) != null)
                throw invalid("DUPLICATE_ALLOCATION_STAGE", "同一时间阶段不能重复提交");
        }
        var missing = activeNodes.stream().filter(node -> !submitted.containsKey(node.id())).map(TopicIndicatorMapper.Node::name).toList();
        if (!missing.isEmpty() || submitted.size() != activeNodes.size())
            throw invalid("ALLOCATION_PLAN_INCOMPLETE", "必须一次提交全部时间阶段；缺少：" + String.join("、", missing));
        for (var node : activeNodes) {
            var stage = submitted.get(node.id());
            var saved = save(topicId, stage);
            publish(topicId, new PublishRequest(stage.nodeId(), saved.draftVersion()), key + "-" + node.id());
        }
    }

    private Context context(TopicQueryService.TopicSummary topic,long nodeId) {
        var node=node(nodeId,topic.projectId(),true);
        var targets=indicators.effective(topic.id(),nodeId).stream().collect(Collectors.toMap(row->TopicService.id(row.indicatorDefinitionId()),row->row));
        if(targets.isEmpty()) throw conflict("TOPIC_TARGETS_NOT_PUBLISHED","请先由科研助理下发该节点的课题指标");
        var enabledUnits=enabledUnits();
        var members=topics.listMembers(topic.id(),false).stream().filter(member->enabledUnits.contains(member.unitId()))
                .collect(Collectors.toMap(TopicQueryService.Member::unitId,member->member));
        int version=targets.values().stream().mapToInt(TargetView::version).max().orElse(0);
        return new Context(node,targets,members,version);
    }

    private Map<Dimension,Integer> input(List<AllocationInput> rows) {
        if(rows==null || rows.size()>5000) throw invalid("INVALID_ALLOCATIONS","allocations不能为空且最多5000行");
        var values=new LinkedHashMap<Dimension,Integer>();
        for(var row:rows) {
            if(row==null || row.targetQuantity()==null || row.targetQuantity()<0) throw invalid("INVALID_ALLOCATION_QUANTITY","分配数量必须为非负整数");
            var dimension=new Dimension(TopicService.id(row.unitId()),TopicService.id(row.indicatorDefinitionId()));
            if(values.putIfAbsent(dimension,row.targetQuantity())!=null) throw invalid("DUPLICATE_ALLOCATION_DIMENSION","同一单位指标不能重复");
        }
        return values;
    }

    private void validate(long topic,Context context,Map<Dimension,Integer> values,boolean publishing) {
        var definitions=indicators.definitions().stream().collect(Collectors.toMap(TopicIndicatorMapper.Definition::id,row->row));
        for(var row:values.entrySet()) {
            var dimension=row.getKey();
            if(!context.members().containsKey(dimension.unit())) throw invalid("INVALID_ALLOCATION_MEMBER","分配单位必须是启用的有效课题成员");
            var definition=definitions.get(dimension.definition());
            if(!context.targets().containsKey(dimension.definition()) || definition==null || !definition.enabled())
                throw invalid("INVALID_ALLOCATION_TARGET","只能分配该节点已下发且启用的课题指标");
            if("SPECIAL".equals(definition.category())) {
                var base=values.entrySet().stream().filter(candidate->{
                    var d=definitions.get(candidate.getKey().definition());
                    return candidate.getKey().unit()==dimension.unit() && d!=null && d.enabled() && "BASE".equals(d.category()) && d.achievementType().equals(definition.achievementType());
                }).toList();
                if(base.size()!=1 || base.getFirst().getValue()<row.getValue()) throw invalid("ALLOCATION_SPECIAL_EXCEEDS_BASE","每个单位的各专项分配必须分别不超过对应成果总数；专项之间允许重叠");
            }
        }
        if(publishing) {
            for(var member:context.members().keySet()) for(var target:context.targets().keySet())
                if(!values.containsKey(new Dimension(member,target))) throw invalid("ALLOCATION_COVERAGE_INCOMPLETE","发布须覆盖所有有效启用成员（含牵头）及生效指标，零目标请显式填0");
            for(var target:context.targets().entrySet()) {
                long sum=values.entrySet().stream().filter(row->row.getKey().definition()==target.getKey()).mapToLong(Map.Entry::getValue).sum();
                if(sum!=target.getValue().targetQuantity())
                    throw invalid("ALLOCATION_STAGE_TOTAL_MISMATCH", context.node().name()+"的指标“"+
                            definitions.get(target.getKey()).name()+"”要求分配"+target.getValue().targetQuantity()+"，当前已分配"+sum);
            }
        }
    }
    private TopicIndicatorMapper.Node node(long id,long project,boolean writing) {
        TopicService.id(Long.toString(id));
        var node=indicators.node(id);
        if(node==null || node.projectId()!=project || (writing && !node.enabled())) throw invalid("INVALID_TIME_NODE","节点不属于课题项目、不存在或已停用");
        return node;
    }
    private Set<Long> enabledUnits() {
        return system.listUnits(null,null).stream().filter(unit->unit.enabled()).map(unit->TopicService.id(unit.id())).collect(Collectors.toSet());
    }
    private CurrentUser writer(String authority) {
        var user=security.requireCurrentUser();
        if(!(user.isInternalUnit() || user.isExternalUnit()) || user.unitId()==null || !user.authorities().contains(authority))
            throw BusinessException.forbidden("ALLOCATION_LEAD_REQUIRED","仅有对应操作权限的当前牵头单位可以维护分配");
        return user;
    }
    private void lead(long topic,String authority) {
        var user=writer(authority);
        if(!topics.isLeadUnit(topic,user.unitId()) || !enabledUnits().contains(user.unitId()))
            throw BusinessException.forbidden("ALLOCATION_LEAD_REQUIRED","当前单位不是有效且启用的牵头单位");
    }
    private void writable(TopicQueryService.TopicSummary topic) {
        if(!topic.enabled() || Set.of("PAUSED","CLOSED").contains(topic.status())) throw conflict("TOPIC_NOT_OPERATIONAL","课题当前只读");
    }
    private static BusinessException invalid(String code,String message) { return BusinessException.validation(code,message); }
    private static BusinessException conflict(String code,String message) { return BusinessException.conflict(code,message); }
}
