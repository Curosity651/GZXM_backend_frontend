package com.gzxm.server.modules.topic.application;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.*;
import com.gzxm.server.modules.system.api.SystemDtos.UnitView;
import com.gzxm.server.modules.topic.api.TopicDtos.*;
import com.gzxm.server.modules.topic.domain.*;
import com.gzxm.server.modules.topic.repository.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class TopicService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "ACTIVE", "PAUSED", "CLOSED");
    private final TopicMapper topics;
    private final TopicMembershipMapper members;
    private final TopicUnitDirectory units;
    private final SecurityContextFacade security;
    private final TopicAccessService access;

    public TopicService(TopicMapper topics, TopicMembershipMapper members, TopicUnitDirectory units,
                        SecurityContextFacade security, TopicAccessService access) {
        this.topics = topics; this.members = members; this.units = units; this.security = security; this.access = access;
    }

    @Transactional(readOnly = true)
    public PageResult<TopicView> list(long page, long size, String keyword, String status, Boolean enabled) {
        CurrentUser user = reader();
        if (page < 1 || size < 1 || size > 200 || page - 1 > Long.MAX_VALUE / size)
            throw BusinessException.validation("INVALID_PAGINATION", "page必须大于等于1，size必须在1到200之间");
        if (status != null) validateStatus(status);
        Long unitId = user.isGlobalRole() ? null : user.unitId();
        String query = keyword == null || keyword.isBlank() ? null : keyword.trim();
        var directory = units.snapshot();
        return PageResult.of(topics.search(unitId, query, status, enabled, size, (page - 1) * size)
                .stream().map(topic -> view(topic, directory)).toList(), page, size,
                topics.count(unitId, query, status, enabled));
    }

    @Transactional(readOnly = true)
    public TopicView get(long topicId) {
        requireReadable(topicId);
        return view(requireTopic(topicId, false), units.snapshot());
    }

    @Transactional(readOnly = true)
    public List<MembershipView> listMembers(long topicId) {
        requireReadable(topicId);
        requireTopic(topicId, false);
        var directory = units.snapshot();
        return members.list(topicId).stream().map(member -> view(member, directory)).toList();
    }

    @Transactional
    public TopicView create(TopicWriteRequest request) {
        CurrentUser user = assistant();
        validateWrite(request);
        long leadId = id(request.leadUnitId());
        List<Long> participants = participantIds(request.participantUnitIds(), leadId);
        var directory = units.snapshot();
        requireEnabledUnit(leadId, directory);
        participants.forEach(unit -> requireEnabledUnit(unit, directory));
        var projects = topics.activeProjects();
        if (projects.size() != 1)
            throw BusinessException.conflict("PROJECT_CONFIGURATION_REQUIRED", "必须先配置且仅配置一个有效重点项目");
        TopicEntity topic = new TopicEntity();
        topic.setProjectId(projects.getFirst()); topic.setLeadUnitId(leadId);
        topic.setStatus("ACTIVE"); topic.setEnabled(true); topic.setRecordVersion(1);
        topic.setCreatedBy(user.id()); apply(request, topic, user);
        try {
            topics.insert(topic);
            insertMember(topic.getId(), leadId, "LEAD", user.id());
            for (long unit : participants) insertMember(topic.getId(), unit, "PARTICIPANT", user.id());
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("TOPIC_UNIQUE_CONFLICT", "课题编号或成员关系重复");
        }
        return view(topic, directory);
    }

    @Transactional
    public TopicView update(long topicId, TopicWriteRequest request) {
        CurrentUser user = assistant();
        validateWrite(request);
        TopicEntity topic = requireTopic(topicId, true);
        requireWritable(topic);
        if (request.recordVersion() == null || request.recordVersion() != topic.getRecordVersion())
            throw BusinessException.conflict("TOPIC_VERSION_CONFLICT", "请携带最新recordVersion后重试");
        long leadId = id(request.leadUnitId());
        List<Long> participants = participantIds(request.participantUnitIds(), leadId);
        var directory = units.snapshot();
        requireEnabledUnit(leadId, directory);
        participants.forEach(unit -> requireEnabledUnit(unit, directory));
        try {
            if (topic.getLeadUnitId() != leadId) changeLead(topic, leadId, user.id());
            for (long unitId : participants) {
                // Explicit additive semantics: existing and disabled relationships are preserved.
                if (members.findUnit(topicId, unitId) == null) insertMember(topicId, unitId, "PARTICIPANT", user.id());
            }
            apply(request, topic, user);
            persist(topic);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("TOPIC_UNIQUE_CONFLICT", "课题编号或成员关系重复");
        }
        return view(topic, directory);
    }

    @Transactional
    public TopicView setStatus(long topicId, TopicStatusRequest request) {
        CurrentUser user = assistant();
        if (request.enabled() == null) throw BusinessException.validation("VALIDATION_FAILED", "enabled不能为空");
        if (request.status() != null) validateStatus(request.status());
        TopicEntity topic = requireTopic(topicId, true);
        // Status administration remains available to restore a read-only topic.
        topic.setEnabled(request.enabled());
        if (request.status() != null) topic.setStatus(request.status());
        topic.setUpdatedBy(user.id());
        persist(topic);
        return view(topic, units.snapshot());
    }

    @Transactional
    public MembershipView addParticipant(long topicId, ParticipantRequest request) {
        CurrentUser user = unitWriter();
        TopicEntity topic = requireTopic(topicId, true);
        requireLead(topic, user);
        long unitId = id(request.unitId());
        var directory = units.snapshot();
        requireEnabledUnit(user.unitId(), directory);
        requireEnabledUnit(unitId, directory);
        if (members.findUnit(topicId, unitId) != null)
            throw BusinessException.conflict("TOPIC_MEMBER_CONFLICT", "该单位已存在成员关系，停用关系请使用启用接口恢复");
        try {
            var member = insertMember(topicId, unitId, "PARTICIPANT", user.id());
            topic.setUpdatedBy(user.id()); persist(topic);
            return view(member, directory);
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("TOPIC_MEMBER_CONFLICT", "该单位已存在成员关系");
        }
    }

    @Transactional
    public MembershipView setMembershipStatus(long topicId, long membershipId, MembershipStatusRequest request) {
        CurrentUser user = unitWriter();
        TopicEntity topic = requireTopic(topicId, true);
        requireLead(topic, user);
        if (request.enabled() == null) throw BusinessException.validation("VALIDATION_FAILED", "enabled不能为空");
        TopicMembershipEntity member = members.find(topicId, positive(membershipId));
        if (member == null) throw BusinessException.notFound("TOPIC_MEMBER_NOT_FOUND", "该课题中不存在指定成员关系");
        if (!"PARTICIPANT".equals(member.getMembershipType()))
            throw BusinessException.conflict("TOPIC_LEAD_IMMUTABLE", "此接口只能启停承担关系，不能改变牵头关系");
        var directory = units.snapshot();
        requireEnabledUnit(user.unitId(), directory);
        if (request.enabled()) requireEnabledUnit(member.getUnitId(), directory);
        member.setEnabled(request.enabled()); member.setUpdatedBy(user.id()); members.update(member);
        topic.setUpdatedBy(user.id()); persist(topic);
        return view(member, directory);
    }

    private void changeLead(TopicEntity topic, long leadId, long actor) {
        var previous = members.findUnit(topic.getId(), topic.getLeadUnitId());
        if (previous == null || !previous.isEnabled() || !"LEAD".equals(previous.getMembershipType()))
            throw BusinessException.conflict("TOPIC_LEAD_INCONSISTENT", "课题牵头关系不一致，请联系管理员检查");
        // Parent row lock serializes topic/member writes; the unique lead key requires demotion first.
        previous.setMembershipType("PARTICIPANT"); previous.setUpdatedBy(actor); members.update(previous);
        var next = members.findUnit(topic.getId(), leadId);
        if (next == null) insertMember(topic.getId(), leadId, "LEAD", actor);
        else {
            next.setMembershipType("LEAD"); next.setEnabled(true); next.setUpdatedBy(actor); members.update(next);
        }
        topic.setLeadUnitId(leadId);
    }

    private TopicMembershipEntity insertMember(long topicId, long unitId, String type, long actor) {
        var member = new TopicMembershipEntity();
        member.setTopicId(topicId); member.setUnitId(unitId); member.setMembershipType(type);
        member.setEnabled(true); member.setCreatedBy(actor); member.setUpdatedBy(actor);
        members.insert(member);
        return member;
    }

    private void persist(TopicEntity topic) {
        if (topics.update(topic) != 1)
            throw BusinessException.conflict("TOPIC_VERSION_CONFLICT", "课题已被修改，请刷新后重试");
        topic.setRecordVersion(topic.getRecordVersion() + 1);
    }

    private CurrentUser reader() {
        CurrentUser user = security.requireCurrentUser();
        if (!user.isGlobalRole() && (!(user.isInternalUnit() || user.isExternalUnit()) || user.unitId() == null))
            throw BusinessException.forbidden("TOPIC_SCOPE_DENIED", "当前账号没有课题数据范围");
        return user;
    }

    private CurrentUser assistant() {
        CurrentUser user = security.requireCurrentUser();
        if (!"RESEARCH_ASSISTANT".equals(user.roleCode()) || !user.authorities().contains("topic.manage"))
            throw BusinessException.forbidden("TOPIC_MANAGER_REQUIRED", "只有具有课题维护权限的科研助理可以执行此操作");
        return user;
    }

    private CurrentUser unitWriter() {
        CurrentUser user = reader();
        if (!(user.isInternalUnit() || user.isExternalUnit()) || !user.authorities().contains("topic-unit.manage"))
            throw BusinessException.forbidden("TOPIC_LEAD_REQUIRED", "只有具有成员维护权限的当前课题牵头单位可以执行此操作");
        return user;
    }

    @Transactional(readOnly = true)
    public boolean canRead(long topicId) {
        positive(topicId);
        CurrentUser user = reader();
        if (topics.find(topicId) == null) return false;
        if (user.isGlobalRole()) return true;
        if (user.memberships().stream().noneMatch(member -> member.topicId() == topicId && member.enabled())) return false;
        var member = members.findUnit(topicId, user.unitId());
        return member != null && member.isEnabled();
    }

    private void requireReadable(long topicId) {
        positive(topicId);
        CurrentUser user = reader();
        access.requireMember(topicId);
        if (!user.isGlobalRole()) {
            var member = members.findUnit(topicId, user.unitId());
            if (member == null || !member.isEnabled())
                throw BusinessException.forbidden("TOPIC_SCOPE_DENIED", "当前单位不属于该课题的有效成员");
        }
    }

    private void requireLead(TopicEntity topic, CurrentUser user) {
        access.requireLead(topic.getId());
        var member = members.findUnit(topic.getId(), user.unitId());
        if (member == null || !member.isEnabled() || !"LEAD".equals(member.getMembershipType())
                || !Objects.equals(topic.getLeadUnitId(), user.unitId()))
            throw BusinessException.forbidden("TOPIC_LEAD_REQUIRED", "当前单位不是该课题的有效牵头单位");
        requireWritable(topic);
    }

    private TopicEntity requireTopic(long id, boolean lock) {
        positive(id);
        var topic = lock ? topics.lock(id) : topics.find(id);
        if (topic == null) throw BusinessException.notFound("TOPIC_NOT_FOUND", "课题不存在");
        return topic;
    }

    private void requireWritable(TopicEntity topic) {
        if (!topic.isEnabled() || "PAUSED".equals(topic.getStatus()) || "CLOSED".equals(topic.getStatus()))
            throw BusinessException.conflict("TOPIC_NOT_OPERATIONAL", "课题暂停、关闭或停用，当前仅可查看历史");
    }

    private void requireEnabledUnit(long id, Map<Long, UnitView> directory) {
        var unit = directory.get(id);
        if (unit == null || !unit.enabled()) throw BusinessException.validation("INVALID_UNIT", "单位不存在或已停用");
    }

    private void validateWrite(TopicWriteRequest request) {
        if (request.code() == null || request.code().isBlank() || request.code().length() > 64
                || request.name() == null || request.name().isBlank() || request.name().length() > 300)
            throw BusinessException.validation("VALIDATION_FAILED", "课题编号和名称不能为空且不能超出长度限制");
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate()))
            throw BusinessException.validation("TOPIC_DATE_RANGE_INVALID", "结束日期不能早于开始日期");
        if (request.summary() != null && request.summary().getBytes(StandardCharsets.UTF_8).length > 65535)
            throw BusinessException.validation("TOPIC_SUMMARY_TOO_LONG", "课题简介超过数据库TEXT长度限制");
    }

    private List<Long> participantIds(List<String> values, long leadId) {
        if (values == null) return List.of();
        var seen = new HashSet<Long>();
        var result = new ArrayList<Long>();
        for (String value : values) {
            long unit = id(value);
            if (unit == leadId || !seen.add(unit))
                throw BusinessException.validation("TOPIC_PARTICIPANTS_INVALID", "承担单位不能重复或包含牵头单位");
            result.add(unit);
        }
        return result;
    }

    private void apply(TopicWriteRequest request, TopicEntity topic, CurrentUser actor) {
        topic.setCode(request.code().trim()); topic.setName(request.name().trim()); topic.setSummary(request.summary());
        topic.setStartDate(request.startDate()); topic.setEndDate(request.endDate()); topic.setUpdatedBy(actor.id());
    }

    private void validateStatus(String status) {
        if (!STATUSES.contains(status)) throw BusinessException.validation("INVALID_TOPIC_STATUS", "课题状态不正确");
    }

    public static long id(String value) {
        try {
            if (value == null || !value.matches("[1-9][0-9]*")) throw new NumberFormatException();
            return positive(Long.parseLong(value));
        } catch (NumberFormatException ex) {
            throw BusinessException.validation("INVALID_ID", "ID必须为有效正整数且不能超过Java Long范围");
        }
    }

    private static long positive(long value) {
        if (value < 1) throw BusinessException.validation("INVALID_ID", "ID必须为正整数");
        return value;
    }

    private TopicView view(TopicEntity topic, Map<Long, UnitView> directory) {
        return new TopicView(topic.getId().toString(), topic.getCode(), topic.getName(), topic.getSummary(),
                topic.getLeadUnitId().toString(), topic.getStatus(), topic.isEnabled(), topic.getStartDate(),
                topic.getEndDate(), topic.getRecordVersion(), members.list(topic.getId()).stream()
                .map(member -> view(member, directory)).toList());
    }

    private MembershipView view(TopicMembershipEntity member, Map<Long, UnitView> directory) {
        var unit = directory.get(member.getUnitId());
        return new MembershipView(member.getId().toString(), member.getTopicId().toString(), member.getUnitId().toString(),
                unit == null ? null : unit.name(), member.getMembershipType(), member.isEnabled());
    }
}
