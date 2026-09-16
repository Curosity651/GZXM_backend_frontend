package com.gzxm.server.modules.achievement.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.*;
import com.gzxm.server.modules.achievement.api.AchievementDtos.AchievementView;
import com.gzxm.server.modules.achievement.api.AchievementHistoryDtos.ApprovalView;
import com.gzxm.server.modules.achievement.api.AchievementWorkflowDtos.*;
import com.gzxm.server.modules.achievement.domain.*;
import com.gzxm.server.modules.achievement.repository.*;
import com.gzxm.server.modules.indicator.application.AchievementAssignmentQuery;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Objects;

@Service
public class AchievementWorkflowService {
    private final AchievementService achievements;
    private final AchievementMapper records;
    private final AchievementHistoryMapper history;
    private final AchievementOperationMapper operations;
    private final TopicQueryService topics;
    private final AchievementAssignmentQuery assignments;
    private final AchievementSubmissionRules rules;
    private final AchievementDetailValidator details;
    private final SecurityContextFacade security;
    private final ObjectMapper json;
    private final AchievementMaterialMapper materials;

    public AchievementWorkflowService(AchievementService achievements,AchievementMapper records,AchievementHistoryMapper history,
            AchievementOperationMapper operations,TopicQueryService topics,AchievementAssignmentQuery assignments,
            AchievementSubmissionRules rules,AchievementDetailValidator details,SecurityContextFacade security,ObjectMapper json,AchievementMaterialMapper materials) {
        this.achievements=achievements;this.records=records;this.history=history;this.operations=operations;this.topics=topics;
        this.assignments=assignments;this.rules=rules;this.details=details;this.security=security;this.json=json;this.materials=materials;
    }

    @Transactional
    public Outcome<AchievementView> action(long id,String key,ActionRequest request) {
        var user=achievements.writer();validateKey(key);
        var original=achievements.require(id);var topic=topics.lockTopic(original.getTopicId());var row=records.lock(id);
        achievements.readable(row);
        if(!Objects.equals(row.getUnitId(),user.unitId())) throw BusinessException.forbidden("ACHIEVEMENT_OWNER_REQUIRED","只能提交本单位成果");
        var replay=replay(id,user.id(),key,"ACTION",request,AchievementView.class);
        if(replay!=null) return new Outcome<>(replay,true);
        achievements.writable(topic);version(row,request.recordVersion());
        var assignment=assignments.requireAssigned(row.getTopicId(),row.getNodeId(),row.getIndicatorDefinitionId());
        if(!row.getAchievementType().equals(assignment.achievementType())) throw BusinessException.conflict("ACHIEVEMENT_TYPE_CHANGED","指标类型已变更");
        String next=AchievementWorkflow.action(row.getStatus(),request.action(),row.getAchievementType());
        boolean submitting=request.action().startsWith("SUBMIT_");
        if("REGISTER_EXTERNAL_SUBMISSION".equals(request.action())) register(row,request);
        else if(request.externalSubmissionDate()!=null || request.externalSubmissionNumber()!=null)
            throw BusinessException.validation("UNEXPECTED_EXTERNAL_FIELDS","仅投稿/申请登记动作接受外部投递字段");
        if(submitting) {
            if(row.getSubmittedVersion()==Integer.MAX_VALUE) throw BusinessException.conflict("SUBMISSION_VERSION_EXHAUSTED","提交版本已达上限");
            rules.validate(row,AchievementWorkflow.stage(next));
            row.setSubmittedVersion(row.getSubmittedVersion()+1);
            materials.markCurrent(id,"SUBMITTED");
        }
        row.setStatus(next);row.setUpdatedBy(user.id());row.setCountsToIndicator(false);persist(row);
        var result=achievements.view(records.find(id));
        if(submitting) {
            ObjectNode payload=json.valueToTree(result);
            payload.remove("approvals"); // Reviews are separate history, not part of submitted business content.
            payload.set("materialLinks",json.valueToTree(result.materialLinks().stream().filter(link->link.active()).toList()));
            history.snapshot(id,AchievementWorkflow.stage(next),row.getSubmittedVersion(),user.id(),payload.toString());
        }
        remember(id,user.id(),key,"ACTION",request,result);
        return new Outcome<>(result,false);
    }

    @Transactional
    public Outcome<ApprovalView> review(long id,String key,ReviewRequest request) {
        var user=security.requireCurrentUser();validateKey(key);String level=reviewer(user);
        var original=achievements.require(id);var topic=topics.lockTopic(original.getTopicId());var row=records.lock(id);
        achievements.readable(row);
        var replay=replay(id,user.id(),key,"REVIEW",request,ApprovalView.class);
        if(replay!=null) return new Outcome<>(replay,true);
        achievements.writable(topic);version(row,request.recordVersion());
        if(request.submittedVersion()==null || request.submittedVersion()!=row.getSubmittedVersion())
            throw BusinessException.conflict("SUBMITTED_VERSION_CONFLICT","审批提交版本已变化");
        if(("INITIAL".equals(level) && !AchievementWorkflow.INITIAL.contains(row.getStatus()))
                || ("FINAL".equals(level) && !AchievementWorkflow.FINAL.contains(row.getStatus())))
            throw BusinessException.conflict("ACHIEVEMENT_REVIEW_STAGE_CONFLICT","当前状态与审批级别不匹配");
        if("RETURN".equals(request.decision()) && (request.opinion()==null || request.opinion().isBlank()))
            throw BusinessException.validation("RETURN_OPINION_REQUIRED","退回意见必填");
        String stage=AchievementWorkflow.stage(row.getStatus());
        if(history.snapshots(id).stream().noneMatch(snapshot->snapshot.submittedVersion()==row.getSubmittedVersion() && stage.equals(snapshot.stage())))
            throw BusinessException.conflict("SUBMISSION_SNAPSHOT_REQUIRED","缺少当前阶段提交快照，不能审批");
        String next=AchievementWorkflow.review(row.getStatus(),request.decision(),row.getAchievementType());
        if("APPROVE".equals(request.decision())) rules.validate(row,stage);
        history.approve(id,stage,level,"APPROVE".equals(request.decision())?"APPROVED":"RETURNED",request.opinion(),user.id(),row.getSubmittedVersion());
        if("RETURN".equals(request.decision())) materials.markCurrent(id,"RETURNED");
        else if("FINAL".equals(level)) materials.markCurrent(id,"APPROVED");
        row.setStatus(next);row.setCountsToIndicator("EFFECTIVE".equals(next));row.setUpdatedBy(user.id());persist(row);
        var result=achievements.approvalView(history.approvals(id).getLast());
        remember(id,user.id(),key,"REVIEW",request,result);
        return new Outcome<>(result,false);
    }

    private String reviewer(CurrentUser user) {
        if("RESEARCH_ASSISTANT".equals(user.roleCode()) && user.authorities().contains("achievement.initial.approve")) return "INITIAL";
        if("PROJECT_TECH_LEADER".equals(user.roleCode()) && user.authorities().contains("achievement.final.approve")) return "FINAL";
        throw BusinessException.forbidden("ACHIEVEMENT_REVIEWER_REQUIRED","仅具备对应权限的科研助理或项目技术负责人可以审批");
    }
    private void register(AchievementEntity row,ActionRequest request) {
        try {
            if(request.externalSubmissionDate()==null || !request.externalSubmissionDate().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException();
            LocalDate.parse(request.externalSubmissionDate());
        } catch(RuntimeException ex) {throw BusinessException.validation("EXTERNAL_SUBMISSION_DATE_REQUIRED","登记必须提供有效投递日期");}
        try {
            var detail=(ObjectNode)json.readTree(row.getDetailJson());boolean paper="PAPER".equals(row.getAchievementType());
            detail.put(paper?"submissionDate":"applicationDate",request.externalSubmissionDate());
            if(request.externalSubmissionNumber()!=null && !request.externalSubmissionNumber().isBlank())
                detail.put(paper?"externalSubmissionNumber":"applicationNumber",request.externalSubmissionNumber().trim());
            row.setDetailJson(details.validate(row.getAchievementType(),detail).toString());
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid achievement detail",ex);}
    }
    private void version(AchievementEntity row,Integer version) {
        if(version==null || version!=row.getRecordVersion()) throw BusinessException.conflict("ACHIEVEMENT_VERSION_CONFLICT","请携带最新recordVersion");
        if(row.getRecordVersion()==Integer.MAX_VALUE) throw BusinessException.conflict("ACHIEVEMENT_VERSION_EXHAUSTED","记录版本已达上限");
    }
    private void persist(AchievementEntity row) {
        if(records.transition(row)!=1) throw BusinessException.conflict("ACHIEVEMENT_VERSION_CONFLICT","成果已被修改");
    }
    private void validateKey(String key) {
        if(key==null || !key.matches("[!-~]{8,100}")) throw BusinessException.validation("INVALID_IDEMPOTENCY_KEY","Idempotency-Key需为8至100个可见ASCII字符");
    }
    private <T> T replay(long id,long actor,String key,String kind,Object request,Class<T> type) {
        var operation=operations.find(actor,key);
        if(operation==null) return null;
        try {
            if(operation.achievementId()!=id || !operation.operationKind().equals(kind) || !json.readTree(operation.requestJson()).equals(json.valueToTree(request)))
                throw BusinessException.conflict("IDEMPOTENCY_KEY_CONFLICT","同一幂等键不能用于不同请求");
            return json.readerFor(type).without(com.fasterxml.jackson.databind.DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                    .readValue(operation.responseJson());
        } catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Invalid operation record",ex);}
    }
    private void remember(long id,long actor,String key,String kind,Object request,Object response) {
        try {operations.insert(id,actor,key,kind,json.writeValueAsString(request),json.writeValueAsString(response));}
        catch(DuplicateKeyException ex){throw BusinessException.conflict("IDEMPOTENCY_KEY_CONFLICT","幂等键已被另一请求使用");}
        catch(com.fasterxml.jackson.core.JsonProcessingException ex){throw new IllegalStateException("Cannot serialize operation",ex);}
    }
}
