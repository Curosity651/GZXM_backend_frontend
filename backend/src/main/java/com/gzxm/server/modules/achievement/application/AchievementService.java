package com.gzxm.server.modules.achievement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.*;
import com.gzxm.server.modules.achievement.api.AchievementDtos.*;
import com.gzxm.server.modules.achievement.domain.AchievementEntity;
import com.gzxm.server.modules.achievement.domain.AchievementWorkflow;
import com.gzxm.server.modules.achievement.api.AchievementHistoryDtos.*;
import com.gzxm.server.modules.achievement.repository.*;
import com.gzxm.server.modules.indicator.application.AchievementAssignmentQuery;
import com.gzxm.server.modules.system.application.SystemService;
import com.gzxm.server.modules.topic.application.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class AchievementService {
    private static final List<String> ASSISTANT_VISIBLE_STATES=List.of(
            "PRE_INITIAL","PRE_FINAL","PRE_APPROVED","EXTERNAL_SUBMITTED","FORMAL_INITIAL","FORMAL_FINAL",
            "WAIT_PUBLICATION","WAIT_GRANT","SUPPLEMENT_INITIAL","SUPPLEMENT_FINAL","EFFECTIVE");
    private static final List<String> LEADER_VISIBLE_STATES=List.of(
            "PRE_FINAL","PRE_APPROVED","EXTERNAL_SUBMITTED","FORMAL_FINAL","WAIT_PUBLICATION","WAIT_GRANT","SUPPLEMENT_FINAL","EFFECTIVE");
    private final AchievementMapper records;
    private final AchievementMaterialMapper materials;
    private final TopicQueryService topics;
    private final AchievementAssignmentQuery assignments;
    private final SecurityContextFacade security;
    private final SystemService system;
    private final AchievementDetailValidator details;
    private final ObjectProvider<AchievementFileGateway> files;
    private final ObjectMapper json;
    private final AchievementHistoryMapper history;
    public AchievementService(AchievementMapper records,AchievementMaterialMapper materials,TopicQueryService topics,
                              AchievementAssignmentQuery assignments,SecurityContextFacade security,SystemService system,
                              AchievementDetailValidator details,ObjectProvider<AchievementFileGateway> files,ObjectMapper json,AchievementHistoryMapper history) {
        this.records=records;this.materials=materials;this.topics=topics;this.assignments=assignments;this.security=security;
        this.system=system;this.details=details;this.files=files;this.json=json;this.history=history;
    }

    @Transactional(readOnly=true)
    public PageResult<AchievementView> list(long page,long size,Long topic,Long node,Long unit,Long definition,String status,boolean pending) {
        var user=reader();
        if(page<1 || size<1 || size>200 || page-1>Long.MAX_VALUE/size) throw invalid("INVALID_PAGINATION","分页参数不正确");
        for(Long id:Arrays.asList(topic,node,unit,definition)) if(id!=null) TopicService.id(id.toString());
        if(status!=null && status.length()>40) throw invalid("INVALID_ACHIEVEMENT_STATUS","状态超出长度限制");
        var memberTopics=new ArrayList<Long>();memberTopics.add(-1L);
        var leadTopics=new ArrayList<Long>();leadTopics.add(-1L);
        if(!user.isGlobalRole()) for(var member:user.memberships()) {
            if(!member.enabled()) continue;
            if(!topics.canReadTopic(member.topicId())) continue;
            memberTopics.add(member.topicId());
            if(topics.isLeadUnit(member.topicId(),user.unitId())) leadTopics.add(member.topicId());
        }
        List<String> pendingStates=null;
        if(pending) pendingStates="RESEARCH_ASSISTANT".equals(user.roleCode()) && user.authorities().contains("achievement.initial.approve")
                ?List.copyOf(AchievementWorkflow.INITIAL):"PROJECT_TECH_LEADER".equals(user.roleCode()) && user.authorities().contains("achievement.final.approve")
                ?List.copyOf(AchievementWorkflow.FINAL):List.of("__NONE__");
        var filter=new AchievementMapper.Filter(user.isGlobalRole()?null:user.unitId(),memberTopics,leadTopics,topic,node,unit,definition,status,
                visibleStates(user),pendingStates,(page-1)*size,size);
        return PageResult.of(records.list(filter).stream().map(this::view).toList(),page,size,records.count(filter));
    }

    @Transactional(readOnly=true)
    public AchievementView get(long id) { var row=require(id); readable(row);return view(row); }

    @Transactional
    public AchievementView create(WriteRequest request) {
        var user=writer();
        long topicId=TopicService.id(request.topicId()),nodeId=TopicService.id(request.nodeId()),definitionId=TopicService.id(request.indicatorDefinitionId());
        writable(topics.lockTopic(topicId));
        var assignment=assignments.requireAssigned(topicId,nodeId,definitionId);
        var row=new AchievementEntity();
        row.setProjectId(assignment.projectId());row.setTopicId(topicId);row.setNodeId(nodeId);row.setIndicatorDefinitionId(definitionId);
        row.setMembershipId(assignment.membershipId());row.setUnitId(assignment.unitId());row.setAchievementType(assignment.achievementType());
        row.setStatus("DRAFT");row.setRecordVersion(1);row.setCreatedBy(user.id());row.setUpdatedBy(user.id());
        apply(row,request);records.insert(row);replaceMaterials(row,request,user.id());
        return view(records.find(row.getId()));
    }

    @Transactional
    public AchievementView update(long id,WriteRequest request) {
        var user=writer();
        var original=require(id);
        writable(topics.lockTopic(original.getTopicId()));
        var row=records.lock(id);
        readable(row);
        if(!Objects.equals(row.getUnitId(),user.unitId())) throw BusinessException.forbidden("ACHIEVEMENT_OWNER_REQUIRED","只能维护本单位成果");
        if(!AchievementWorkflow.EDITABLE.contains(row.getStatus())) throw conflict("ACHIEVEMENT_READ_ONLY","只能编辑草稿、退回或待补充成果");
        if(request.recordVersion()==null || !Objects.equals(request.recordVersion(),row.getRecordVersion())) throw conflict("ACHIEVEMENT_VERSION_CONFLICT","请携带最新recordVersion");
        if(row.getTopicId()!=TopicService.id(request.topicId()) || row.getNodeId()!=TopicService.id(request.nodeId())
                || row.getIndicatorDefinitionId()!=TopicService.id(request.indicatorDefinitionId())) throw conflict("ACHIEVEMENT_OWNERSHIP_IMMUTABLE","创建后的课题、节点和指标归属不可更换");
        var assignment=assignments.requireAssigned(row.getTopicId(),row.getNodeId(),row.getIndicatorDefinitionId());
        if(!assignment.achievementType().equals(row.getAchievementType())) throw conflict("ACHIEVEMENT_TYPE_CHANGED","指标类型已变更，请先核对配置");
        if(row.getRecordVersion()==Integer.MAX_VALUE) throw conflict("ACHIEVEMENT_VERSION_EXHAUSTED","版本已达上限");
        apply(row,request);row.setUpdatedBy(user.id());
        replaceMaterials(row,request,user.id());
        if(records.update(row)!=1) throw conflict("ACHIEVEMENT_VERSION_CONFLICT","成果已被修改");
        return view(records.find(id));
    }

    /** Used only by the file module's trusted business-access callback. */
    @Transactional(readOnly=true)
    public boolean canReadFile(long fileId) {
        for(long id:materials.achievements(fileId)) {
            var row=records.find(id);
            if(row==null) continue;
            if(canRead(row)) return true;
        }
        return false;
    }

    private void apply(AchievementEntity row,WriteRequest request) {
        if(request.title()==null || request.title().isBlank() || request.title().length()>500 || request.responsiblePerson()==null
                || request.responsiblePerson().isBlank() || request.responsiblePerson().length()>100) throw invalid("INVALID_ACHIEVEMENT_TEXT","成果名称或负责人不正确");
        row.setTitle(request.title().trim());row.setResponsiblePerson(request.responsiblePerson().trim());
        row.setDetailJson(details.validate(row.getAchievementType(),request.detail()).toString());
    }
    private void replaceMaterials(AchievementEntity row,WriteRequest request,long actor) {
        if(request.materialFileIds()!=null && !request.materialFileIds().isEmpty()) throw invalid("MATERIAL_TYPE_REQUIRED","请使用materialAttachments同时提供文件ID与材料类别");
        var attachments=request.materialAttachments();
        if(attachments==null) return;
        if(attachments.size()>100) throw invalid("TOO_MANY_MATERIALS","最多100个材料文件");
        var desired=new TreeMap<Long,String>();
        for(var material:attachments) {
            if(material==null || material.materialType()==null || material.materialType().isBlank() || material.materialType().length()>100)
                throw invalid("INVALID_MATERIAL_TYPE","材料类别不能为空且最多100字符");
            long file=TopicService.id(material.fileId());
            if(desired.putIfAbsent(file,material.materialType().trim())!=null) throw invalid("DUPLICATE_MATERIAL_FILE","同一文件不能重复关联");
            gateway().requireOwnedReady(file);
        }
        var existing=materials.list(row.getId());var current=new TreeMap<Long,String>();
        existing.stream().filter(AchievementMaterialMapper.Material::active).forEach(item->current.put(item.fileId(),item.materialType()));
        if(current.equals(desired)) return;
        int previous=existing.stream().mapToInt(AchievementMaterialMapper.Material::fileVersion).max().orElse(0);
        if(previous==Integer.MAX_VALUE) throw conflict("MATERIAL_VERSION_EXHAUSTED","材料版本已达上限");
        materials.retire(row.getId());
        desired.forEach((file,type)->materials.insert(row.getId(),file,type,previous+1,actor));
    }
    AchievementView view(AchievementEntity row) {
        try {
            var links=materials.list(row.getId());
            var currentFiles=links.stream().filter(AchievementMaterialMapper.Material::active).map(item->gateway().readMetadata(item.fileId())).toList();
            return new AchievementView(row.getId().toString(),row.getTopicId().toString(),row.getUnitId().toString(),row.getNodeId().toString(),
                    row.getIndicatorDefinitionId().toString(),row.getAchievementType(),row.getTitle(),row.getResponsiblePerson(),row.getStatus(),row.isCountsToIndicator(),
                    row.getRecordVersion(),row.getSubmittedVersion(),json.readTree(row.getDetailJson()),currentFiles,
                    links.stream().map(item->new MaterialLink(Long.toString(item.id()),Long.toString(item.fileId()),item.materialType(),item.fileVersion(),item.active(),item.materialStatus())).toList(),
                    row.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),row.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),
                    history.approvals(row.getId()).stream().map(this::approvalView).toList());
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid persisted achievement detail",ex); }
    }
    AchievementFileGateway gateway() {
        var service=files.getIfAvailable();
        if(service==null) throw new BusinessException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"FILE_REFERENCE_CAPABILITY_UNAVAILABLE","文件公开关联能力尚未接入");
        return service;
    }
    private CurrentUser reader() {
        var user=security.requireCurrentUser();
        if(!user.isGlobalRole() && (!(user.isInternalUnit() || user.isExternalUnit()) || user.unitId()==null)) throw BusinessException.forbidden("ACHIEVEMENT_SCOPE_DENIED","没有成果数据范围");
        return user;
    }
    CurrentUser writer() {
        var user=reader();
        if(!(user.isInternalUnit() || user.isExternalUnit()) || !user.authorities().contains("achievement.submit"))
            throw BusinessException.forbidden("ACHIEVEMENT_UNIT_REQUIRED","只有具有填报权限的课题单位可以维护成果");
        if(system.listUnits(null,null).stream().noneMatch(unit->unit.enabled() && unit.id().equals(user.unitId().toString())))
            throw BusinessException.forbidden("ACHIEVEMENT_UNIT_DISABLED","所属单位已停用");
        return user;
    }
    void readable(AchievementEntity row) {
        if(!canRead(row))
            throw BusinessException.forbidden("ACHIEVEMENT_SCOPE_DENIED","无权查看其他单位成果");
    }
    private boolean canRead(AchievementEntity row) {
        var user=reader();
        var states=visibleStates(user);
        return (states==null || states.contains(row.getStatus())) && topics.canReadTopic(row.getTopicId()) && (user.isGlobalRole() || Objects.equals(row.getUnitId(),user.unitId())
                || topics.isLeadUnit(row.getTopicId(),user.unitId()));
    }
    private List<String> visibleStates(CurrentUser user) {
        return switch(user.roleCode()) {
            case "RESEARCH_ASSISTANT" -> ASSISTANT_VISIBLE_STATES;
            case "PROJECT_TECH_LEADER" -> LEADER_VISIBLE_STATES;
            default -> null;
        };
    }
    AchievementEntity require(long id) {
        TopicService.id(Long.toString(id));var row=records.find(id);
        if(row==null) throw BusinessException.notFound("ACHIEVEMENT_NOT_FOUND","成果不存在");return row;
    }
    void writable(TopicQueryService.TopicSummary topic) {
        if(!topic.enabled() || Set.of("PAUSED","CLOSED").contains(topic.status())) throw conflict("TOPIC_NOT_OPERATIONAL","课题当前只读");
    }
    @Transactional(readOnly=true)
    public List<SnapshotView> snapshots(long id) {
        readable(require(id));
        return history.snapshots(id).stream().map(row->{
            try { return new SnapshotView(Long.toString(row.id()),"ACHIEVEMENT",Long.toString(row.businessId()),row.stage(),row.submittedVersion(),
                    row.submittedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),Long.toString(row.submitterId()),json.readTree(row.payloadJson())); }
            catch(com.fasterxml.jackson.core.JsonProcessingException ex) {throw new IllegalStateException("Invalid submission snapshot",ex);}
        }).toList();
    }
    ApprovalView approvalView(AchievementHistoryMapper.Approval row) {
        return new ApprovalView(Long.toString(row.id()),"ACHIEVEMENT",Long.toString(row.businessId()),row.stage(),row.approvalLevel(),row.decision(),row.opinion(),
                Long.toString(row.operatorId()),row.operatedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime(),row.submittedVersion());
    }
    private static BusinessException invalid(String code,String message) {return BusinessException.validation(code,message);}
    private static BusinessException conflict(String code,String message) {return BusinessException.conflict(code,message);}
}
