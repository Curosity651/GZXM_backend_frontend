package com.gzxm.server.modules.report.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.report.api.ReportDtos.*;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import com.gzxm.server.modules.topic.application.TopicService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

@Service
public class ReportService {
    private static final Set<String> SUBMITTED_STATES = Set.of("INITIAL_REVIEW", "FINAL_REVIEW", "APPROVED");
    private static final Set<String> ASSISTANT_VISIBLE_STATES = SUBMITTED_STATES;
    private static final Set<String> LEADER_VISIBLE_STATES = Set.of("FINAL_REVIEW", "APPROVED");
    private static final String COLUMNS = "r.id,r.topic_id,r.report_type,t.report_year,t.period_no,t.open_date,t.deadline," +
            "r.basic_information,r.milestone_progress,r.overall_progress,r.research_achievements," +
            "r.demonstration_progress,r.fund_usage,r.next_plan," +
            "r.problems_and_measures,r.status,r.overdue,r.record_version,r.submitted_version,r.submitted_at";
    private static final String FROM = " FROM progress_report r JOIN report_task t ON t.id=r.task_id " +
            "JOIN biz_topic visible_topic ON visible_topic.id=r.topic_id AND visible_topic.status<>'DRAFT' ";
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private final TopicQueryService topics;
    private final SecurityContextFacade security;
    private final RowMapper<View> viewMapper = (rs, n) -> view(rs);

    public ReportService(JdbcTemplate db, ObjectMapper json, TopicQueryService topics, SecurityContextFacade security) {
        this.db = db; this.json = json; this.topics = topics; this.security = security;
    }

    public Rule rule(long topicId, Integer effectiveYear) {
        topics.getTopic(topicId);
        return effectiveYear == null ? latestRule(topicId) : exactRule(topicId, effectiveYear);
    }

    @Transactional
    public Rule saveRule(long topicId, Rule request) {
        var user = security.requireCurrentUser();
        if (!"RESEARCH_ASSISTANT".equals(user.roleCode()) && !"SYSTEM_ADMIN".equals(user.roleCode()))
            throw BusinessException.forbidden("REPORT_RULE_ROLE_REQUIRED", "只有科研助理可配置报告规则");
        topics.lockTopic(topicId);
        validateRule(request);
        validateRuleOverlap(topicId, request);
        var old = db.queryForList("SELECT record_version FROM topic_report_rule WHERE topic_id=? AND effective_year=? FOR UPDATE",
                topicId, request.effectiveYear());
        if (old.isEmpty()) {
            if (request.recordVersion() != null && request.recordVersion() != 0)
                throw BusinessException.conflict("REPORT_RULE_VERSION_CONFLICT", "规则版本不一致");
            db.update("INSERT INTO topic_report_rule(topic_id,effective_year,monthly_enabled,monthly_start_year,monthly_start_period," +
                    "monthly_end_year,monthly_end_period,monthly_open_day,monthly_deadline_day,quarterly_enabled,quarterly_start_year," +
                    "quarterly_start_period,quarterly_end_year,quarterly_end_period,quarterly_open_day,quarterly_deadline_day,quarterly_months) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    topicId, request.effectiveYear(), request.monthlyEnabled(), request.monthlyStartYear(), request.monthlyStartPeriod(),
                    request.monthlyEndYear(), request.monthlyEndPeriod(), request.monthlyOpenDay(), request.monthlyDeadlineDay(),
                    request.quarterlyEnabled(), request.quarterlyStartYear(), request.quarterlyStartPeriod(), request.quarterlyEndYear(),
                    request.quarterlyEndPeriod(), request.quarterlyOpenDay(), request.quarterlyDeadlineDay(), encode(request.quarterlyMonths()));
        } else {
            int version = ((Number) old.getFirst().get("record_version")).intValue();
            if (request.recordVersion() == null || request.recordVersion() != version)
                throw BusinessException.conflict("REPORT_RULE_VERSION_CONFLICT", "规则已被修改，请刷新");
            db.update("UPDATE topic_report_rule SET monthly_enabled=?,monthly_start_year=?,monthly_start_period=?,monthly_end_year=?," +
                    "monthly_end_period=?,monthly_open_day=?,monthly_deadline_day=?,quarterly_enabled=?,quarterly_start_year=?," +
                    "quarterly_start_period=?,quarterly_end_year=?,quarterly_end_period=?,quarterly_open_day=?,quarterly_deadline_day=?," +
                    "quarterly_months=?,record_version=record_version+1 WHERE topic_id=? AND effective_year=?",
                    request.monthlyEnabled(), request.monthlyStartYear(), request.monthlyStartPeriod(), request.monthlyEndYear(),
                    request.monthlyEndPeriod(), request.monthlyOpenDay(), request.monthlyDeadlineDay(), request.quarterlyEnabled(),
                    request.quarterlyStartYear(), request.quarterlyStartPeriod(), request.quarterlyEndYear(), request.quarterlyEndPeriod(),
                    request.quarterlyOpenDay(), request.quarterlyDeadlineDay(), encode(request.quarterlyMonths()), topicId, request.effectiveYear());
        }
        return exactRule(topicId, request.effectiveYear());
    }

    @Transactional
    public View create(Create request) {
        long topicId = TopicService.id(request.topicId());
        var topic = topics.lockTopic(topicId);
        requireLead(topicId);
        requireOperational(topic);
        Rule rule = ruleForPeriod(topicId, request.reportType(), request.year(), request.period());
        LocalDate[] window = window(rule, request.reportType(), request.year(), request.period());
        if (LocalDate.now().isBefore(window[0]))
            throw BusinessException.validation("REPORT_NOT_OPEN", "报告尚未开放填报");
        try {
            db.update("INSERT INTO report_task(topic_id,report_type,report_year,period_no,open_date,deadline,created_by) " +
                            "VALUES(?,?,?,?,?,?,?)", topicId, request.reportType(), request.year(), request.period(),
                    window[0], window[1], security.requireCurrentUser().id());
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("REPORT_PERIOD_EXISTS", "该期报告已存在");
        }
        long taskId = lastId();
        db.update("INSERT INTO progress_report(task_id,topic_id,report_type,basic_information,milestone_progress,overall_progress," +
                        "research_achievements,demonstration_progress,fund_usage,next_plan,problems_and_measures,created_by,updated_by) " +
                        "VALUES(?,?,?,'','','','','','','','',?,?)", taskId, topicId, request.reportType(),
                security.requireCurrentUser().id(), security.requireCurrentUser().id());
        return load(lastId(), false);
    }

    public View get(long reportId) {
        View result = load(reportId, false);
        if (!canRead(result)) throw BusinessException.forbidden("REPORT_SCOPE_DENIED", "当前账号无权查看该报告");
        return result;
    }

    public PageResult<View> list(int page, int size, Long topicId, String type, Integer year, Integer period,
                                 String status, boolean pendingForMe) {
        if (page < 1 || size < 1 || size > 200) throw BusinessException.validation("INVALID_PAGE", "分页参数不正确");
        if (topicId != null) topics.getTopic(topicId);
        var user = security.requireCurrentUser();
        var all = db.query("SELECT " + COLUMNS + FROM + "ORDER BY r.id DESC", viewMapper).stream()
                .filter(this::canRead)
                .filter(v -> topicId == null || v.topicId().equals(String.valueOf(topicId)))
                .filter(v -> type == null || type.equals(v.reportType()))
                .filter(v -> year == null || year == v.year())
                .filter(v -> period == null || period == v.period())
                .filter(v -> status == null || status.equals(v.status()))
                .filter(v -> !pendingForMe || ("INITIAL_REVIEW".equals(v.status()) && "RESEARCH_ASSISTANT".equals(user.roleCode()))
                        || ("FINAL_REVIEW".equals(v.status()) && "PROJECT_TECH_LEADER".equals(user.roleCode())))
                .toList();
        long from = ((long) page - 1) * size;
        return PageResult.of(from >= all.size() ? List.of() : all.subList((int) from, (int) Math.min(from + size, all.size())),
                page, size, all.size());
    }

    @Transactional
    public View update(long reportId, Content content) {
        View old = load(reportId, true);
        long topicId = Long.parseLong(old.topicId());
        requireLead(topicId);
        requireOperational(topics.lockTopic(topicId));
        if (!Set.of("DRAFT", "RETURNED").contains(old.status()))
            throw BusinessException.conflict("REPORT_NOT_EDITABLE", "当前状态不能编辑");
        if (old.recordVersion() != content.recordVersion())
            throw BusinessException.conflict("REPORT_VERSION_CONFLICT", "报告已被修改，请刷新");
        db.update("UPDATE progress_report SET basic_information=?,milestone_progress=?,overall_progress=?,research_achievements=?,demonstration_progress=?,fund_usage=?," +
                        "next_plan=?,problems_and_measures=?,record_version=record_version+1,updated_by=? WHERE id=?",
                content.basicInformation(), content.milestoneProgress(), content.overallProgress(), content.researchAchievements(),
                content.demonstrationProgress(), content.fundUsage(),
                content.nextPlan(), content.problemsAndMeasures(), security.requireCurrentUser().id(), reportId);
        return load(reportId, false);
    }

    @Transactional
    public View submit(long reportId, String key) {
        requireKey(key);
        View old = load(reportId, true);
        requireLead(Long.parseLong(old.topicId()));
        requireOperational(topics.lockTopic(Long.parseLong(old.topicId())));
        String hash = hash("REPORT_SUBMIT:" + reportId);
        Map<String, Object> replay = keyRecord("REPORT_SUBMIT", key, reportId, hash);
        if (replay != null) return json.convertValue(replay.get("value"), View.class);
        if (!Set.of("DRAFT", "RETURNED").contains(old.status()))
            throw BusinessException.conflict("REPORT_NOT_SUBMITTABLE", "当前状态不能提交");
        if (LocalDate.now().isBefore(old.openDate()))
            throw BusinessException.validation("REPORT_NOT_OPEN", "报告尚未开放填报");
        if (List.of(old.basicInformation(), old.milestoneProgress(), old.overallProgress(), old.researchAchievements(), old.demonstrationProgress(),
                old.fundUsage(), old.nextPlan(), old.problemsAndMeasures()).stream().anyMatch(String::isBlank))
            throw BusinessException.validation("REPORT_CONTENT_REQUIRED", "请填写完整报告内容");
        ReportSubmissionPolicy.requireDemonstrationProgress(old.demonstrationProgress());
        int version = old.submittedVersion() + 1;
        var now = LocalDateTime.now();
        db.update("UPDATE progress_report SET status='INITIAL_REVIEW',submitted_version=?,submitted_at=?,overdue=?," +
                "record_version=record_version+1,updated_by=? WHERE id=?", version, now,
                LocalDate.now().isAfter(old.deadline()), security.requireCurrentUser().id(), reportId);
        View updated = load(reportId, false);
        db.update("INSERT INTO submission_snapshot(business_type,business_id,stage,submitted_version,submitter_id,payload_json) " +
                "VALUES('REPORT',?,?,?,?,?)", reportId, "INITIAL_REVIEW", version, security.requireCurrentUser().id(), encode(updated));
        saveKey("REPORT_SUBMIT", key, reportId, hash, updated);
        return updated;
    }

    @Transactional
    public Approval review(long reportId, String key, Review request) {
        requireKey(key);
        View old = load(reportId, true);
        topics.getTopic(Long.parseLong(old.topicId()));
        var user = security.requireCurrentUser();
        String hash = hash("REPORT_REVIEW:" + reportId + ":" + request.decision() + ":" +
                Objects.toString(request.opinion(), "") + ":" + request.submittedVersion());
        Map<String, Object> replay = keyRecord("REPORT_REVIEW", key, reportId, hash);
        if (replay != null) return json.convertValue(replay.get("value"), Approval.class);
        if (old.submittedVersion() != request.submittedVersion())
            throw BusinessException.conflict("REPORT_SUBMITTED_VERSION_CONFLICT", "提交版本已变化");
        boolean initial = "INITIAL_REVIEW".equals(old.status());
        if (!initial && !"FINAL_REVIEW".equals(old.status()))
            throw BusinessException.conflict("REPORT_NOT_REVIEWABLE", "当前状态不能审批");
        String role = initial ? "RESEARCH_ASSISTANT" : "PROJECT_TECH_LEADER";
        String authority = initial ? "report.initial.approve" : "report.final.approve";
        if (!role.equals(user.roleCode()) || !user.authorities().contains(authority))
            throw BusinessException.forbidden("REPORT_REVIEW_ROLE_REQUIRED", "当前角色不能审批此环节");
        if (!Set.of("APPROVE", "RETURN").contains(request.decision()))
            throw BusinessException.validation("INVALID_REVIEW_DECISION", "审批动作不正确");
        if ("RETURN".equals(request.decision()) && (request.opinion() == null || request.opinion().isBlank()))
            throw BusinessException.validation("REVIEW_OPINION_REQUIRED", "退回时必须填写意见");
        if (request.opinion() != null && request.opinion().length() > 1000)
            throw BusinessException.validation("REVIEW_OPINION_TOO_LONG", "审批意见不能超过1000字");
        String next = "RETURN".equals(request.decision()) ? "RETURNED" : initial ? "FINAL_REVIEW" : "APPROVED";
        db.update("UPDATE progress_report SET status=?,record_version=record_version+1,updated_by=? WHERE id=?",
                next, user.id(), reportId);
        db.update("INSERT INTO approval_record(business_type,business_id,stage,approval_level,decision,opinion,operator_id,submitted_version) " +
                "VALUES('REPORT',?,?,?,?,?,?,?)", reportId, old.status(), initial ? "INITIAL" : "FINAL",
                "RETURN".equals(request.decision()) ? "RETURNED" : "APPROVED", request.opinion(), user.id(), old.submittedVersion());
        long approvalId = lastId();
        Approval value = db.queryForObject("SELECT * FROM approval_record WHERE id=?", (rs, n) -> approval(rs), approvalId);
        saveKey("REPORT_REVIEW", key, reportId, hash, value);
        return value;
    }

    public List<Snapshot> snapshots(long reportId) {
        get(reportId);
        return db.query("SELECT * FROM submission_snapshot WHERE business_type='REPORT' AND business_id=? ORDER BY submitted_version DESC",
                (rs, n) -> new Snapshot(String.valueOf(rs.getLong("id")), "REPORT", String.valueOf(reportId),
                        rs.getString("stage"), rs.getInt("submitted_version"), rs.getTimestamp("submitted_at").toLocalDateTime(),
                        String.valueOf(rs.getLong("submitter_id")), decode(rs.getString("payload_json"))), reportId);
    }

    public List<Approval> approvals(long reportId) {
        get(reportId);
        return db.query("SELECT * FROM approval_record WHERE business_type='REPORT' AND business_id=? ORDER BY id",
                (rs, n) -> approval(rs), reportId);
    }

    @Transactional(readOnly = true)
    public Progress progress(Long topicId, Integer requestedYear, String reportType) {
        int year = requestedYear == null ? LocalDate.now().getYear() : requestedYear;
        if (year < 2000 || year > 2100)
            throw BusinessException.validation("INVALID_REPORT_YEAR", "统计年度必须在2000至2100之间");
        if (reportType != null && !Set.of("MONTHLY", "QUARTERLY").contains(reportType))
            throw BusinessException.validation("INVALID_REPORT_TYPE", "报告类型不正确");
        CurrentUser user = security.requireCurrentUser();
        var visibleTopics = topics.listReadableTopics(topics.currentProjectId()).stream()
                .filter(topic -> topicId == null || topic.id() == topicId)
                .filter(topic -> user.isGlobalRole() || user.unitId() != null && topic.leadUnitId() == user.unitId())
                .toList();
        if (topicId != null && visibleTopics.isEmpty())
            throw BusinessException.forbidden("REPORT_SCOPE_DENIED", "当前账号无权查看该课题月季报进度");
        List<View> reports = db.query("SELECT " + COLUMNS + FROM + " WHERE t.report_year=? ORDER BY r.id", viewMapper, year).stream()
                .filter(this::canReadProgress)
                .filter(report -> reportType == null || reportType.equals(report.reportType()))
                .toList();
        Map<String, View> actual = new HashMap<>();
        reports.forEach(report -> actual.put(periodKey(Long.parseLong(report.topicId()), report.reportType(), report.year(), report.period()), report));
        List<ProgressTopic> result = new ArrayList<>();
        for (var topic : visibleTopics) {
            Map<String, ProgressPeriod> periods = new LinkedHashMap<>();
            var rules = db.query("SELECT * FROM topic_report_rule WHERE topic_id=? ORDER BY effective_year", (rs, n) -> rule(rs), topic.id());
            for (Rule rule : rules) {
                if (reportType == null || "MONTHLY".equals(reportType)) addExpectedPeriods(periods, actual, topic.id(), rule, "MONTHLY", year);
                if (reportType == null || "QUARTERLY".equals(reportType)) addExpectedPeriods(periods, actual, topic.id(), rule, "QUARTERLY", year);
            }
            reports.stream().filter(report -> report.topicId().equals(String.valueOf(topic.id()))).forEach(report -> {
                String key = periodKey(topic.id(), report.reportType(), report.year(), report.period());
                periods.putIfAbsent(key, progressPeriod(report, report.reportType(), report.year(), report.period(), report.openDate(), report.deadline()));
            });
            List<ProgressPeriod> ordered = periods.values().stream()
                    .sorted(Comparator.comparing(ProgressPeriod::reportType).thenComparingInt(ProgressPeriod::year).thenComparingInt(ProgressPeriod::period))
                    .toList();
            int submitted = (int) ordered.stream().filter(period -> period.submittedVersion() > 0).count();
            int approved = (int) ordered.stream().filter(period -> "APPROVED".equals(period.status())).count();
            int missing = (int) ordered.stream().filter(period -> period.submittedVersion() == 0 && !"NOT_OPEN".equals(period.status())).count();
            int overdue = (int) ordered.stream().filter(period -> "OVERDUE".equals(period.timing())).count();
            result.add(new ProgressTopic(String.valueOf(topic.id()), topic.code(), topic.name(), ordered.size(), submitted,
                    approved, missing, overdue, ordered));
        }
        return new Progress(year, result);
    }

    private void addExpectedPeriods(Map<String, ProgressPeriod> periods, Map<String, View> actual, long topicId,
                                    Rule rule, String type, int year) {
        boolean monthly = "MONTHLY".equals(type);
        if (monthly && !rule.monthlyEnabled() || !monthly && !rule.quarterlyEnabled()) return;
        int startYear = monthly ? rule.monthlyStartYear() : rule.quarterlyStartYear();
        int endYear = monthly ? rule.monthlyEndYear() : rule.quarterlyEndYear();
        if (year < startYear || year > endYear) return;
        int maximum = monthly ? 12 : 4;
        int start = year == startYear ? (monthly ? rule.monthlyStartPeriod() : rule.quarterlyStartPeriod()) : 1;
        int end = year == endYear ? (monthly ? rule.monthlyEndPeriod() : rule.quarterlyEndPeriod()) : maximum;
        for (int period = start; period <= end; period++) {
            String key = periodKey(topicId, type, year, period);
            View report = actual.get(key);
            LocalDate[] window = window(rule, type, year, period);
            periods.putIfAbsent(key, progressPeriod(report, type, year, period, window[0], window[1]));
        }
    }

    private ProgressPeriod progressPeriod(View report, String reportType, int year, int period,
                                          LocalDate openDate, LocalDate deadline) {
        LocalDate today = LocalDate.now();
        String status = report == null ? today.isBefore(openDate) ? "NOT_OPEN" : "NOT_CREATED" : report.status();
        boolean overdue = report == null
                ? !today.isBefore(openDate) && today.isAfter(deadline)
                : report.overdue() || Set.of("DRAFT", "RETURNED").contains(report.status()) && today.isAfter(deadline);
        String timing = today.isBefore(openDate) ? "UPCOMING" : overdue ? "OVERDUE" : "NORMAL";
        return new ProgressPeriod(report == null ? null : report.id(), reportType, year, period,
                openDate, deadline, status, timing, report == null ? 0 : report.submittedVersion());
    }

    private String periodKey(long topicId, String type, int year, int period) {
        return topicId + ":" + type + ":" + year + ":" + period;
    }

    private void requireLead(long topicId) {
        CurrentUser user = security.requireCurrentUser();
        if ((!user.isInternalUnit() && !user.isExternalUnit()) || user.unitId() == null ||
                !topics.isLeadUnit(topicId, user.unitId()))
            throw BusinessException.forbidden("REPORT_LEAD_UNIT_REQUIRED", "只有课题牵头单位可填报");
    }
    private void requireOperational(TopicQueryService.TopicSummary topic) {
        if (!topic.enabled() || !"ACTIVE".equals(topic.status()))
            throw BusinessException.conflict("TOPIC_NOT_OPERATIONAL", "课题当前不能办理业务");
    }
    private boolean canRead(View report) {
        long topicId = Long.parseLong(report.topicId());
        if (!topics.canReadTopic(topicId)) return false;
        CurrentUser user = security.requireCurrentUser();
        return switch (user.roleCode()) {
            case "SYSTEM_ADMIN" -> true;
            case "RESEARCH_ASSISTANT" -> ASSISTANT_VISIBLE_STATES.contains(report.status());
            case "PROJECT_TECH_LEADER" -> LEADER_VISIBLE_STATES.contains(report.status());
            case "INTERNAL_TOPIC_UNIT", "EXTERNAL_TOPIC_UNIT" -> user.unitId() != null
                    && topics.isLeadUnit(topicId, user.unitId());
            default -> false;
        };
    }
    private boolean canReadProgress(View report) {
        CurrentUser user = security.requireCurrentUser();
        if (user.isGlobalRole()) return true;
        long topicId = Long.parseLong(report.topicId());
        return (user.isInternalUnit() || user.isExternalUnit()) && user.unitId() != null
                && topics.canReadTopic(topicId) && topics.isLeadUnit(topicId, user.unitId());
    }
    private Rule latestRule(long topicId) {
        var rows = db.query("SELECT * FROM topic_report_rule WHERE topic_id=? ORDER BY effective_year DESC LIMIT 1",
                (rs, n) -> rule(rs), topicId);
        if (rows.isEmpty()) throw BusinessException.notFound("REPORT_RULE_NOT_FOUND", "课题尚未配置报告规则");
        return rows.getFirst();
    }
    private Rule ruleForPeriod(long topicId, String type, int year, int period) {
        var rows = db.query("SELECT * FROM topic_report_rule WHERE topic_id=? AND effective_year<=? ORDER BY effective_year DESC",
                (rs, n) -> rule(rs), topicId, year).stream().filter(rule -> inRange(rule, type, year, period)).toList();
        if (rows.isEmpty()) throw BusinessException.validation("REPORT_RULE_NOT_FOUND", "该年度尚无有效报告规则");
        return rows.getFirst();
    }
    private Rule exactRule(long topicId, int year) {
        var rows = db.query("SELECT * FROM topic_report_rule WHERE topic_id=? AND effective_year=?",
                (rs, n) -> rule(rs), topicId, year);
        if (rows.isEmpty()) throw BusinessException.notFound("REPORT_RULE_NOT_FOUND", "该年度尚未配置报告规则");
        return rows.getFirst();
    }
    private Rule rule(ResultSet rs) throws SQLException {
        try {
            List<Integer> months = json.readValue(rs.getString("quarterly_months"), new TypeReference<>() {});
            return new Rule(rs.getInt("effective_year"), rs.getBoolean("monthly_enabled"),
                    integer(rs,"monthly_start_year"), integer(rs,"monthly_start_period"),
                    integer(rs,"monthly_end_year"), integer(rs,"monthly_end_period"),
                    rs.getInt("monthly_open_day"), rs.getInt("monthly_deadline_day"), rs.getBoolean("quarterly_enabled"),
                    integer(rs,"quarterly_start_year"), integer(rs,"quarterly_start_period"),
                    integer(rs,"quarterly_end_year"), integer(rs,"quarterly_end_period"),
                    rs.getInt("quarterly_open_day"), rs.getInt("quarterly_deadline_day"), months, rs.getInt("record_version"));
        } catch (Exception ex) { throw new SQLException("报告规则格式错误", ex); }
    }
    private Integer integer(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column); return value == null ? null : ((Number) value).intValue();
    }
    private void validateRule(Rule rule) {
        if (!rule.monthlyEnabled() && !rule.quarterlyEnabled())
            throw BusinessException.validation("REPORT_RULE_EMPTY", "月报和季报至少启用一种");
        if (rule.monthlyEnabled()) validateRange("月报", rule.monthlyStartYear(), rule.monthlyStartPeriod(),
                rule.monthlyEndYear(), rule.monthlyEndPeriod(), 12);
        if (rule.quarterlyEnabled()) validateRange("季报", rule.quarterlyStartYear(), rule.quarterlyStartPeriod(),
                rule.quarterlyEndYear(), rule.quarterlyEndPeriod(), 4);
        int firstYear = Math.min(rule.monthlyEnabled() ? rule.monthlyStartYear() : Integer.MAX_VALUE,
                rule.quarterlyEnabled() ? rule.quarterlyStartYear() : Integer.MAX_VALUE);
        if (rule.effectiveYear() != firstYear)
            throw BusinessException.validation("REPORT_RULE_EFFECTIVE_YEAR", "规则年度必须等于最早启用范围的开始年度");
        if (rule.quarterlyMonths().size() != 4 || rule.quarterlyMonths().stream().anyMatch(m -> m == null || m < 1 || m > 12)
                || !(rule.quarterlyMonths().get(0) < rule.quarterlyMonths().get(1)
                && rule.quarterlyMonths().get(1) < rule.quarterlyMonths().get(2)
                && rule.quarterlyMonths().get(2) < rule.quarterlyMonths().get(3)))
            throw BusinessException.validation("INVALID_QUARTERLY_MONTHS", "季度月份需为4个递增的有效月份");
        if (rule.monthlyOpenDay() > rule.monthlyDeadlineDay() || rule.quarterlyOpenDay() > rule.quarterlyDeadlineDay())
            throw BusinessException.validation("INVALID_REPORT_WINDOW", "开放日不能晚于截止日");
    }
    private void validateRange(String label, Integer startYear, Integer startPeriod, Integer endYear, Integer endPeriod, int maximum) {
        if (startYear == null || startPeriod == null || endYear == null || endPeriod == null)
            throw BusinessException.validation("REPORT_RANGE_REQUIRED", label + "启用时必须填写开始和结束范围");
        if (startYear < 2000 || startYear > 2100 || endYear < 2000 || endYear > 2100
                || startPeriod < 1 || startPeriod > maximum || endPeriod < 1 || endPeriod > maximum
                || rangeValue(startYear,startPeriod,maximum) > rangeValue(endYear,endPeriod,maximum))
            throw BusinessException.validation("INVALID_REPORT_RANGE", label + "结束范围不能早于开始范围");
    }
    private void validateRuleOverlap(long topicId, Rule request) {
        var others = db.query("SELECT * FROM topic_report_rule WHERE topic_id=? AND effective_year<>? FOR UPDATE",
                (rs,n)->rule(rs), topicId, request.effectiveYear());
        for (Rule other : others) {
            if (request.monthlyEnabled() && other.monthlyEnabled() && overlaps(request.monthlyStartYear(), request.monthlyStartPeriod(),
                    request.monthlyEndYear(), request.monthlyEndPeriod(), other.monthlyStartYear(), other.monthlyStartPeriod(),
                    other.monthlyEndYear(), other.monthlyEndPeriod(), 12))
                throw BusinessException.conflict("REPORT_RULE_RANGE_OVERLAP", "月报填报范围与"+other.effectiveYear()+"年度规则重叠");
            if (request.quarterlyEnabled() && other.quarterlyEnabled() && overlaps(request.quarterlyStartYear(), request.quarterlyStartPeriod(),
                    request.quarterlyEndYear(), request.quarterlyEndPeriod(), other.quarterlyStartYear(), other.quarterlyStartPeriod(),
                    other.quarterlyEndYear(), other.quarterlyEndPeriod(), 4))
                throw BusinessException.conflict("REPORT_RULE_RANGE_OVERLAP", "季报填报范围与"+other.effectiveYear()+"年度规则重叠");
        }
    }
    private boolean overlaps(int aStartYear,int aStartPeriod,int aEndYear,int aEndPeriod,
                             int bStartYear,int bStartPeriod,int bEndYear,int bEndPeriod,int maximum) {
        int aStart=rangeValue(aStartYear,aStartPeriod,maximum), aEnd=rangeValue(aEndYear,aEndPeriod,maximum);
        int bStart=rangeValue(bStartYear,bStartPeriod,maximum), bEnd=rangeValue(bEndYear,bEndPeriod,maximum);
        return aStart<=bEnd && bStart<=aEnd;
    }
    private int rangeValue(int year,int period,int maximum) { return year*maximum+period; }
    private boolean inRange(Rule rule,String type,int year,int period) {
        boolean monthly="MONTHLY".equals(type);
        if (!monthly && !"QUARTERLY".equals(type)) throw BusinessException.validation("INVALID_REPORT_TYPE", "报告类型不正确");
        if (monthly && !rule.monthlyEnabled() || !monthly && !rule.quarterlyEnabled()) return false;
        int maximum=monthly?12:4;
        int start=rangeValue(monthly?rule.monthlyStartYear():rule.quarterlyStartYear(), monthly?rule.monthlyStartPeriod():rule.quarterlyStartPeriod(), maximum);
        int end=rangeValue(monthly?rule.monthlyEndYear():rule.quarterlyEndYear(), monthly?rule.monthlyEndPeriod():rule.quarterlyEndPeriod(), maximum);
        int value=rangeValue(year,period,maximum);
        return value>=start && value<=end;
    }
    private LocalDate[] window(Rule rule, String type, int year, int period) {
        boolean monthly = "MONTHLY".equals(type);
        if (!monthly && !"QUARTERLY".equals(type)) throw BusinessException.validation("INVALID_REPORT_TYPE", "报告类型不正确");
        if (period < 1 || monthly && period > 12 || !monthly && period > 4 || !inRange(rule,type,year,period))
            throw BusinessException.validation("REPORT_PERIOD_DISABLED", "该期报告未启用");
        int month = monthly ? period : rule.quarterlyMonths().get(period - 1);
        YearMonth ym = YearMonth.of(year, month);
        return new LocalDate[] { ym.atDay(Math.min(monthly ? rule.monthlyOpenDay() : rule.quarterlyOpenDay(), ym.lengthOfMonth())),
                ym.atDay(Math.min(monthly ? rule.monthlyDeadlineDay() : rule.quarterlyDeadlineDay(), ym.lengthOfMonth())) };
    }
    private View load(long id, boolean lock) {
        var rows = db.query("SELECT " + COLUMNS + FROM + " WHERE r.id=?" + (lock ? " FOR UPDATE" : ""), viewMapper, id);
        if (rows.isEmpty()) throw BusinessException.notFound("REPORT_NOT_FOUND", "报告不存在");
        return rows.getFirst();
    }
    private View view(ResultSet rs) throws SQLException {
        var submitted = rs.getTimestamp("submitted_at");
        var deadline = rs.getDate("deadline").toLocalDate();
        var status = rs.getString("status");
        boolean overdue = rs.getBoolean("overdue") || (Set.of("DRAFT", "RETURNED").contains(status)
                && LocalDate.now().isAfter(deadline));
        return new View(String.valueOf(rs.getLong("id")), String.valueOf(rs.getLong("topic_id")), rs.getString("report_type"),
                rs.getInt("report_year"), rs.getInt("period_no"), rs.getDate("open_date").toLocalDate(),
                deadline, rs.getString("basic_information"), rs.getString("milestone_progress"), rs.getString("overall_progress"),
                rs.getString("research_achievements"), rs.getString("demonstration_progress"), rs.getString("fund_usage"), rs.getString("next_plan"),
                rs.getString("problems_and_measures"), status, overdue,
                rs.getInt("record_version"), rs.getInt("submitted_version"), submitted == null ? null : submitted.toLocalDateTime());
    }
    private Approval approval(ResultSet rs) throws SQLException {
        return new Approval(String.valueOf(rs.getLong("id")), "REPORT", String.valueOf(rs.getLong("business_id")),
                rs.getString("stage"), rs.getString("approval_level"), rs.getString("decision"), rs.getString("opinion"),
                String.valueOf(rs.getLong("operator_id")), rs.getTimestamp("operated_at").toLocalDateTime(),
                rs.getInt("submitted_version"));
    }
    private long lastId() { return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private Map<String, Object> decode(String value) {
        try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private void requireKey(String key) {
        if (key == null || !key.matches("[!-~]{8,100}"))
            throw BusinessException.validation("INVALID_IDEMPOTENCY_KEY", "Idempotency-Key需为8至100个可见ASCII字符");
    }
    private Map<String, Object> keyRecord(String operation, String key, long reportId, String hash) {
        var rows = db.query("SELECT request_hash,response_body FROM api_idempotency WHERE user_id=? AND operation_code=? " +
                "AND idempotency_key=?", (rs, n) -> Map.of("hash", rs.getString("request_hash"),
                "body", rs.getString("response_body")), security.requireCurrentUser().id(), operation, key);
        if (rows.isEmpty()) return null;
        Map<String, Object> body = decode(rows.getFirst().get("body"));
        if (!Objects.equals(rows.getFirst().get("hash"), hash) ||
                !Objects.equals(((Number) body.get("businessId")).longValue(), reportId))
            throw BusinessException.conflict("IDEMPOTENCY_KEY_CONFLICT", "幂等键已用于不同请求");
        return body;
    }
    private void saveKey(String operation, String key, long reportId, String hash, Object value) {
        db.update("INSERT INTO api_idempotency(user_id,idempotency_key,operation_code,request_hash,response_status,response_body,expires_at) " +
                        "VALUES(?,?,?,?,?,?,DATE_ADD(NOW(3),INTERVAL 1 DAY))", security.requireCurrentUser().id(), key,
                operation, hash, 200, encode(Map.of("businessId", reportId, "value", value)));
    }
    private String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
}
