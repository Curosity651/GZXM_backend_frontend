package com.gzxm.server.modules.indicator.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.*;
import com.gzxm.server.modules.indicator.api.IndicatorDtos.*;
import com.gzxm.server.modules.indicator.repository.TopicIndicatorMapper;
import com.gzxm.server.modules.indicator.repository.TopicIndicatorMapper.*;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import com.gzxm.server.modules.topic.application.TopicService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class TopicIndicatorService {
    private final TopicIndicatorMapper mapper;
    private final TopicQueryService topics;
    private final SecurityContextFacade security;
    private final ObjectMapper json;

    public TopicIndicatorService(TopicIndicatorMapper mapper,TopicQueryService topics,SecurityContextFacade security,ObjectMapper json) {
        this.mapper=mapper; this.topics=topics; this.security=security; this.json=json;
    }

    @Transactional(readOnly=true)
    public DraftResult list(long topicId,long nodeId,String view) {
        var topic=topics.getTopic(topicId);
        node(nodeId,topic.projectId(),false);
        if ("effective".equals(view)) return new DraftResult(0,mapper.effective(topicId,nodeId));
        if (!"draft".equals(view)) throw invalid("INVALID_INDICATOR_VIEW","view必须为effective或draft");
        assistant("indicator.manage");
        var draft=mapper.draft(topicId,nodeId);
        return new DraftResult(draft==null?0:draft.draftVersion(),mapper.draftTargets(topicId,nodeId));
    }

    @Transactional
    public DraftResult save(long topicId,TargetBatch request) {
        var actor=assistant("indicator.manage");
        var topic=topics.lockTopic(topicId);
        writable(topic);
        long nodeId=TopicService.id(request.nodeId());
        var node=node(nodeId,topic.projectId(),true);
        var quantities=validateInput(request.targets());
        var draft=mapper.draft(topicId,nodeId);
        int revision=draft==null?0:draft.draftVersion();
        if ((request.draftVersion()==null?0:request.draftVersion())!=revision)
            throw conflict("INDICATOR_DRAFT_VERSION_CONFLICT","草稿已修改，请重新读取草稿版本");
        if (revision==Integer.MAX_VALUE) throw conflict("INDICATOR_VERSION_EXHAUSTED","草稿版本已达上限");
        validateQuantities(topicId,node,quantities,true);
        if (draft==null) { mapper.createDraft(topicId,nodeId,actor.id()); draft=mapper.draft(topicId,nodeId); }
        mapper.clearDraft(draft.id());
        for (var target:quantities.entrySet()) mapper.insertDraftTarget(draft.id(),target.getKey(),target.getValue());
        mapper.bumpDraft(draft.id(),actor.id());
        return new DraftResult(revision+1,mapper.draftTargets(topicId,nodeId));
    }

    /** Returns false for a durable replay, so callers do not duplicate success audit. */
    @Transactional
    public boolean publish(long topicId,PublishRequest request,String key) {
        var actor=assistant("topic-indicator.publish");
        if (key==null || !key.matches("[!-~]{8,100}")) throw invalid("INVALID_IDEMPOTENCY_KEY","Idempotency-Key必须是8到100位可见ASCII字符");
        long nodeId=TopicService.id(request.nodeId());
        if (request.draftVersion()==null || request.draftVersion()<1) throw invalid("INVALID_DRAFT_VERSION","发布必须指定正整数draftVersion");
        var topic=topics.lockTopic(topicId);
        var previous=mapper.publication(actor.id(),key);
        if (previous!=null) {
            if (previous.topicId()!=topicId || previous.nodeId()!=nodeId || previous.draftVersion()!=request.draftVersion())
                throw conflict("IDEMPOTENCY_KEY_CONFLICT","相同发布键不能用于不同课题、节点或草稿版本");
            return false;
        }
        writable(topic);
        var node=node(nodeId,topic.projectId(),true);
        var draft=mapper.draft(topicId,nodeId);
        if (draft==null || draft.draftVersion()!=request.draftVersion())
            throw conflict("INDICATOR_DRAFT_VERSION_CONFLICT","请读取最新草稿版本后发布");
        if (draft.draftVersion()==draft.publishedDraftVersion())
            throw conflict("INDICATOR_NO_PENDING_DRAFT","当前草稿已发布，请使用原发布键重试");
        var rows=mapper.draftTargets(topicId,nodeId);
        if (rows.isEmpty()) throw invalid("INDICATOR_EMPTY_PUBLICATION","空草稿不能下发");
        var quantities=new TreeMap<Long,Integer>();
        rows.forEach(row->quantities.put(TopicService.id(row.indicatorDefinitionId()),row.targetQuantity()));
        validateQuantities(topicId,node,quantities,false);
        if (draft.publishVersion()==Integer.MAX_VALUE) throw conflict("INDICATOR_VERSION_EXHAUSTED","发布版本已达上限");
        int version=draft.publishVersion()+1;
        try {
            // History and effective rows commit together. This is business publication deduplication,
            // not a replacement for A's general-purpose idempotency infrastructure.
            mapper.recordPublication(topicId,nodeId,draft.draftVersion(),version,actor.id(),key,json.writeValueAsString(quantities));
            for (var target:quantities.entrySet())
                mapper.publishTarget(topic.projectId(),topicId,nodeId,target.getKey(),target.getValue(),version,actor.id());
            mapper.markPublished(draft.id(),version,actor.id());
        } catch (JsonProcessingException ex) { throw new IllegalStateException("Cannot serialize indicator publication",ex); }
        catch (DuplicateKeyException ex) { throw conflict("IDEMPOTENCY_KEY_CONFLICT","发布键已被其他请求使用"); }
        return true;
    }

    private Map<Long,Integer> validateInput(List<TargetInput> targets) {
        if (targets==null || targets.size()>500) throw invalid("INVALID_INDICATOR_TARGETS","targets不能为空且最多500项");
        var result=new TreeMap<Long,Integer>();
        for (var target:targets) {
            if (target==null || target.targetQuantity()==null || target.targetQuantity()<0)
                throw invalid("INVALID_INDICATOR_QUANTITY","指标数量必须为非负整数");
            long id=TopicService.id(target.indicatorDefinitionId());
            if (result.putIfAbsent(id,target.targetQuantity())!=null)
                throw invalid("DUPLICATE_INDICATOR_DEFINITION","同一批次不能重复指标维度");
        }
        return result;
    }

    private void validateQuantities(long topic,Node node,Map<Long,Integer> values,boolean includeDrafts) {
        var definitions=new HashMap<Long,Definition>();
        mapper.definitions().forEach(definition->definitions.put(definition.id(),definition));
        for (var entry:values.entrySet()) {
            var definition=definitions.get(entry.getKey());
            if (definition==null || !definition.enabled() || !Set.of("PAPER","PATENT","COPYRIGHT","STANDARD","TALENT").contains(definition.achievementType())
                    || !Set.of("BASE","SPECIAL").contains(definition.category()))
                throw invalid("INVALID_INDICATOR_DEFINITION","指标不存在、停用或类型不受支持");
            if ("SPECIAL".equals(definition.category())) {
                var base=values.entrySet().stream().filter(candidate->{
                    var d=definitions.get(candidate.getKey());
                    return d!=null && d.enabled() && "BASE".equals(d.category()) && d.achievementType().equals(definition.achievementType());
                }).toList();
                if (base.size()!=1)
                    throw invalid("SPECIAL_TARGET_EXCEEDS_BASE",node.name()+"的“"+definition.name()+"”缺少唯一对应的成果总数");
                if (base.getFirst().getValue()<entry.getValue()) {
                    var baseDefinition=definitions.get(base.getFirst().getKey());
                    throw invalid("SPECIAL_TARGET_EXCEEDS_BASE",node.name()+"的“"+definition.name()+"”为"+entry.getValue()+
                            "，不能超过“"+baseDefinition.name()+"总数”的"+base.getFirst().getValue());
                }
            }
        }
        var effective=mapper.effectiveQuantities(topic);
        // Until completed-achievement adjustment contracts exist, do not invalidate published obligations.
        for (var old:effective) if (!includeDrafts && old.nodeId()==node.id() && (!values.containsKey(old.definitionId()) || values.get(old.definitionId())<old.quantity())) {
            int next=values.getOrDefault(old.definitionId(),0);
            throw conflict("PUBLISHED_TARGET_REDUCTION_UNSUPPORTED",node.name()+"的“"+old.definitionName()+"”已下发"+old.quantity()+
                    "，当前填写"+next+"，不能删除或降低已下发要求");
        }
        checkCumulative(effective,node,values);
        // Other node drafts may still contain their old values while the browser saves a complete
        // multi-node form one request at a time. Cross-node draft validation would reject that
        // harmless intermediate state; the final relation is enforced again during publication.
    }

    private void checkCumulative(List<Quantity> existing,Node node,Map<Long,Integer> values) {
        for (var row:existing) {
            var value=values.get(row.definitionId());
            if (value==null || row.nodeId()==node.id()) continue;
            if (row.sortOrder()<node.sortOrder() && row.quantity()>value)
                throw invalid("INDICATOR_CUMULATIVE_INVALID",node.name()+"的“"+row.definitionName()+"”为"+value+
                        "，不能低于前序节点“"+row.nodeName()+"”的"+row.quantity());
            if (row.sortOrder()>node.sortOrder() && row.quantity()<value)
                throw invalid("INDICATOR_CUMULATIVE_INVALID",node.name()+"的“"+row.definitionName()+"”为"+value+
                        "，不能高于后续节点“"+row.nodeName()+"”的"+row.quantity());
        }
    }

    private Node node(long id,long project,boolean writing) {
        TopicService.id(Long.toString(id));
        var node=mapper.node(id);
        if (node==null || node.projectId()!=project || (writing && !node.enabled()))
            throw invalid("INVALID_TIME_NODE","节点不存在、不属于课题项目或已停用");
        return node;
    }
    private CurrentUser assistant(String authority) {
        var user=security.requireCurrentUser();
        if (!"RESEARCH_ASSISTANT".equals(user.roleCode()) || !user.authorities().contains(authority))
            throw BusinessException.forbidden("INDICATOR_MANAGER_REQUIRED","只有具有对应指标操作权限的科研助理可以执行");
        return user;
    }
    private void writable(TopicQueryService.TopicSummary topic) {
        if (!topic.enabled() || Set.of("PAUSED","CLOSED").contains(topic.status()))
            throw conflict("TOPIC_NOT_OPERATIONAL","课题当前只读");
    }
    private static BusinessException invalid(String code,String message) { return BusinessException.validation(code,message); }
    private static BusinessException conflict(String code,String message) { return BusinessException.conflict(code,message); }
}
