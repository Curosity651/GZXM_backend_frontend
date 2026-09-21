package com.gzxm.server.modules.archive.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.modules.archive.api.ArchiveDtos.*;
import com.gzxm.server.modules.file.api.FileDtos.FileView;
import com.gzxm.server.modules.file.application.FileService;
import com.gzxm.server.modules.topic.application.TopicQueryService;
import com.gzxm.server.modules.topic.application.TopicService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.*;

@Service
public class ArchiveService {
    private final JdbcTemplate db;
    private final TopicQueryService topics;
    private final TopicService topicService;
    private final SecurityContextFacade security;
    private final FileService fileService;
    private record Requirement(String category, String name, boolean required) {}
    private static Requirement required(String category, String name) { return new Requirement(category, name, true); }
    private static Requirement ifPresent(String category, String name) { return new Requirement(category, name, false); }
    private static final Map<String, List<Requirement>> TEMPLATES = archiveTemplates();

    private static Map<String, List<Requirement>> archiveTemplates() {
        Map<String, List<Requirement>> templates = new HashMap<>();
        templates.put("NATIONAL", nationalTemplate());
        templates.put("RENOVATION", renovationTemplate());
        templates.put("TECHNOLOGY", technologyTemplate());
        templates.put("INFRASTRUCTURE", infrastructureTemplate());
        return Map.copyOf(templates);
    }

    private static List<Requirement> nationalTemplate() {
        List<Requirement> rows = new ArrayList<>();
        String category = "项目申报立项";
        rows.addAll(List.of(
                ifPresent(category, "年度指南、申报书"), ifPresent(category, "申报立项评审材料及视频资料"),
                ifPresent(category, "预算评审报告"), ifPresent(category, "申报立项评审过程材料及项目申报单位投诉材料"),
                ifPresent(category, "立项批复（含预算）"), ifPresent(category, "保密协议"),
                ifPresent(category, "任务合同书（含预算书）"),
                ifPresent(category, "上级部门批示指示文件、重要往来函件、会议纪要、突发情况过程文件等")));
        category = "项目过程管理";
        rows.addAll(List.of(
                ifPresent(category, "实验任务书、实验大纲"), ifPresent(category, "实验、探测、测试、观测、调查、考察等原始记录及综合分析报告"),
                ifPresent(category, "各类协议、合同及样机、样品、标本等实物目录"), ifPresent(category, "设计文件和图纸"),
                ifPresent(category, "计算文件、数据处理文件及声像文件"), ifPresent(category, "项目调整、变更材料"),
                ifPresent(category, "变更批复及调整过程材料"), ifPresent(category, "监督评估报告"),
                ifPresent(category, "年度执行情况报告、检查报告（含经费使用报告）"), ifPresent(category, "产业化年度报告"),
                ifPresent(category, "与其他单位的协作协议、合同等相关文件"),
                ifPresent(category, "阶段执行情况报告、检查报告、总结报告及处理结果"),
                ifPresent(category, "专项管理会议纪要、会议记录、备忘录等")));
        category = "项目综合绩效评价";
        rows.addAll(List.of(
                ifPresent(category, "综合绩效评价申请书、承诺书、年度计划及总体实施绩效报告"),
                ifPresent(category, "综合绩效评价通知"),
                ifPresent(category, "上级部门批示指示文件、重要往来函件、会议纪要、突发情况过程文件等"),
                ifPresent(category, "自评价报告及相关材料"), ifPresent(category, "科技报告"),
                required(category, "知识产权报告及知识产权证明材料"),
                ifPresent(category, "重要成果关键指标或重大效益第三方检测、测试、评估报告"),
                ifPresent(category, "现场测试报告"), ifPresent(category, "用户使用报告及成果产业化证明材料"),
                ifPresent(category, "专家打分表、专家意见、专家签到表、专家承诺书等评审材料"),
                ifPresent(category, "综合绩效评价结论书及过程材料"), ifPresent(category, "任务评价报告、技术报告"),
                ifPresent(category, "整改评价会形成材料"), ifPresent(category, "财务收支执行情况报告及附表"),
                ifPresent(category, "预算调整申请报告及相关批复"), ifPresent(category, "财务抽查报告及整改报告"),
                ifPresent(category, "审计报告及审计底稿"), ifPresent(category, "财务综合绩效评价报告"),
                ifPresent(category, "项目（课题）年度财务决算报告"), ifPresent(category, "资金落实和拨付证明"),
                ifPresent(category, "账户对账单"), ifPresent(category, "中央、地方、自筹及其他渠道资金核算明细账"),
                ifPresent(category, "资金归垫申请及附件"), ifPresent(category, "财务专家打分表、专家意见等"),
                ifPresent(category, "设备台账及设备盘点表"), ifPresent(category, "正式评价整改情况报告及附件"),
                ifPresent(category, "后续支出情况报告及附件"), ifPresent(category, "产业化年度报告")));
        category = "项目成果管理";
        rows.addAll(List.of(
                ifPresent(category, "成果统计分析文件"), ifPresent(category, "科技报告"),
                ifPresent(category, "知识产权清单"), ifPresent(category, "科学数据汇交及情况说明文件"),
                ifPresent(category, "样机、样品、标本等实物汇总目录及图片"),
                ifPresent(category, "科研成果介绍及批准的宣传文件"), ifPresent(category, "奖牌、奖杯、奖状等实物或影印件")));
        rows.add(ifPresent("其他重要材料", "领导视察材料、违规违纪材料、投诉举报及处理材料等"));
        return List.copyOf(rows);
    }

    private static List<Requirement> renovationTemplate() {
        List<Requirement> rows = new ArrayList<>();
        String category = "项目前期";
        rows.addAll(List.of(ifPresent(category, "立项评审会议纪要（如有）"), required(category, "立项申请书"),
                ifPresent(category, "可研报告"), ifPresent(category, "可研估算"), required(category, "项目可研批复文件"),
                ifPresent(category, "可研变更申请表（如有）"), ifPresent(category, "变更后的可研报告（如有）"),
                ifPresent(category, "三重一大决策流程")));
        rows.add(required("项目出库与下达", "项目批文"));
        category = "项目设计";
        rows.addAll(List.of(ifPresent(category, "初步设计文件"), ifPresent(category, "初步设计审查会签到表"),
                ifPresent(category, "设计审查纪要或批复文件"), ifPresent(category, "审定概算书"),
                required(category, "技术规范书"), required(category, "技术规范书审查纪要")));
        rows.addAll(List.of(ifPresent("项目采购", "招投标、谈判文件"), required("项目采购", "中标（成交）通知书")));
        category = "合同签订";
        rows.addAll(List.of(required(category, "合同会签审批表"), required(category, "合同及附件"),
                ifPresent(category, "非法定代表人授权委托书（如有）"), ifPresent(category, "合同补充协议或变更材料（如有）")));
        category = "项目实施";
        rows.addAll(List.of(ifPresent(category, "乙方实施人员资格证明"), required(category, "乙方实施人员社保证明"),
                ifPresent(category, "实施过程往来函件"), ifPresent(category, "项目调整文件（如有）"),
                ifPresent(category, "设备到货验收记录（如有）"), required(category, "项目实施方案"),
                ifPresent(category, "开工报告（如有）"), required(category, "需求规格说明书"),
                ifPresent(category, "概要设计说明书（如有）"), ifPresent(category, "详细设计说明书（如有）"),
                ifPresent(category, "第三方测试技术服务计划书（如有）"), required(category, "功能测试报告"),
                required(category, "性能测试报告"), required(category, "安全评估报告或渗透测试报告"),
                ifPresent(category, "系统安装部署方案（如有）"), ifPresent(category, "施工方案及施工图（如有）"),
                ifPresent(category, "初始化方案（如有）"), ifPresent(category, "系统集成调试方案、调试报告、竣工图（如有）"),
                ifPresent(category, "系统联通性及上线切换测试材料（如有）"), ifPresent(category, "数据清理方案（如有）"),
                ifPresent(category, "数据字典、用户手册及审核材料（如有）"), ifPresent(category, "培训相关材料（如有）")));
        rows.add(ifPresent("上线试运行", "试运行报告及试运行支持记录（如有）"));
        category = "项目验收";
        rows.addAll(List.of(ifPresent(category, "验收延期申请材料（如有）"), required(category, "初验申请表"),
                required(category, "竣工验收（终验）申请"), required(category, "验收会签到表"),
                required(category, "验收证书"), required(category, "工作报告"), required(category, "技术报告"),
                required(category, "用户使用报告"), ifPresent(category, "服务评价（如有）"),
                ifPresent(category, "验收活动中产生的其他材料（如有）")));
        rows.addAll(List.of(required("项目结算", "结算表"), required("项目结算", "结算会签表"),
                ifPresent("项目结算", "项目费用结算核减材料（如有）"), ifPresent("转固", "设备移交清单（如有）")));
        return List.copyOf(rows);
    }

    private static List<Requirement> technologyTemplate() {
        List<Requirement> rows = new ArrayList<>();
        String category = "项目前期";
        rows.addAll(List.of(ifPresent(category, "立项评审及办公会审查会议纪要"), required(category, "项目可研报告"),
                required(category, "可研估算"), required(category, "可研批复文件"), ifPresent(category, "三重一大决策流程"),
                required(category, "可研经费审查报告"), ifPresent(category, "可研变更申请表（如有）"),
                ifPresent(category, "变更后的可研报告（如有）")));
        rows.add(required("项目出库与下达", "项目批文"));
        rows.addAll(List.of(required("项目设计", "计划任务书"), required("项目设计", "技术规范书"),
                required("项目设计", "技术规范书评审记录")));
        rows.addAll(List.of(ifPresent("项目采购", "招投标、谈判文件"), required("项目采购", "中标（成交）通知书")));
        category = "合同签订";
        rows.addAll(List.of(required(category, "合同会签审批表"), required(category, "合同及附件"),
                ifPresent(category, "非法定代表人授权委托书（如有）"), ifPresent(category, "合同补充协议或变更材料（如有）")));
        category = "项目实施";
        rows.addAll(List.of(ifPresent(category, "项目调整文件（如有）"), ifPresent(category, "项目启动相关材料（如有）"),
                ifPresent(category, "实施过程往来函件"), required(category, "实施方案"), required(category, "中期验收材料"),
                ifPresent(category, "第三方测试技术服务计划书（如有）"), required(category, "功能测试报告"),
                required(category, "性能测试报告"), required(category, "安全评估报告或渗透测试报告"),
                ifPresent(category, "系统安装部署方案（如有）"), required(category, "系统试运行报告"),
                required(category, "用户报告"), required(category, "专利、论文、软件著作权证明材料"),
                required(category, "技术开发（服务）合同验收评审记录表"), ifPresent(category, "设备到货验收记录（如有）")));
        category = "项目验收";
        rows.addAll(List.of(required(category, "验收评审会通知"), required(category, "竣工验收申请书"),
                required(category, "验收会及专家签到表"), required(category, "验收专家组意见表"),
                required(category, "验收综合绩效评价打分表"), required(category, "验收证书"),
                required(category, "终验工作报告"), required(category, "终验技术报告"),
                ifPresent(category, "验收活动中产生的其他材料（如有）")));
        rows.addAll(List.of(required("项目结算", "第三方技术服务项目委托函"), required("项目结算", "第三方经费审查报告"),
                required("项目结算", "科技项目结算表"), required("项目结算", "结算会签表"),
                ifPresent("项目结算", "项目费用结算核减材料（如有）"), required("转固", "无形资产增加申请单"),
                required("后评价", "项目后评估文件")));
        return List.copyOf(rows);
    }

    private static List<Requirement> infrastructureTemplate() {
        List<Requirement> rows = new ArrayList<>();
        String category = "项目前期";
        rows.addAll(List.of(ifPresent(category, "立项评审会议纪要（如有）"), required(category, "立项申请书"),
                ifPresent(category, "项目可研批复文件"), ifPresent(category, "可研变更申请表（如有）"),
                ifPresent(category, "变更后的可研报告（如有）"), ifPresent(category, "三重一大决策流程")));
        rows.add(required("项目出库与下达", "项目批文"));
        rows.addAll(List.of(ifPresent("项目设计", "技术规范书"), ifPresent("项目设计", "技术规范书审查记录")));
        rows.addAll(List.of(ifPresent("项目采购", "招投标、谈判文件"), required("项目采购", "中标（成交）通知书或其他合同签订依据")));
        category = "合同签订";
        rows.addAll(List.of(required(category, "合同会签审批表"), required(category, "合同及附件"),
                ifPresent(category, "非法定代表人授权委托书（如有）"), ifPresent(category, "合同补充协议或变更材料（如有）")));
        category = "项目实施";
        rows.addAll(List.of(ifPresent(category, "实施过程往来函件"), ifPresent(category, "项目调整文件（如有）"),
                ifPresent(category, "实施方案（如有）"), required(category, "设备到货验收记录"),
                ifPresent(category, "竣工图、设备安装测试及安全等级保护材料（如有）"),
                ifPresent(category, "产品说明书、合格证及设备技术资料（如有）"), ifPresent(category, "设备清册（如有）"),
                ifPresent(category, "培训相关材料（如有）"), ifPresent(category, "实施活动中产生的其他材料（如有）")));
        category = "项目验收";
        rows.addAll(List.of(ifPresent(category, "验收会签到表"), ifPresent(category, "验收证书"),
                ifPresent(category, "工作报告（如有）"), ifPresent(category, "设备安装测试报告（如有）"),
                ifPresent(category, "用户使用报告"), ifPresent(category, "验收活动中产生的其他材料（如有）")));
        rows.addAll(List.of(ifPresent("项目结算", "结算书"), ifPresent("项目结算", "结算会签表"),
                ifPresent("项目结算", "项目费用结算核减材料（如有）"), ifPresent("转固", "设备移交清单（如有）")));
        return List.copyOf(rows);
    }

    public ArchiveService(JdbcTemplate db, TopicQueryService topics, TopicService topicService,
                          SecurityContextFacade security, FileService fileService) {
        this.db = db; this.topics = topics; this.topicService = topicService;
        this.security = security; this.fileService = fileService;
    }

    @Transactional
    public List<Directory> directories(Long topicFilter, Long unitFilter) {
        if (topicFilter != null) topics.getTopic(topicFilter);
        List<Directory> result = new ArrayList<>();
        long page = 1;
        while (true) {
            var slice = topicService.list(page++, 200, null, null, null);
            for (var topic : slice.items()) {
                if ("DRAFT".equals(topic.status())) continue;
                long topicId = Long.parseLong(topic.id());
                if (topicFilter != null && topicFilter != topicId) continue;
                for (var member : topics.listMembers(topicId, false)) {
                    if (unitFilter != null && unitFilter != member.unitId()) continue;
                    if (!canSee(topicId, member.unitId(), "TOPIC_NATIONAL")) continue;
                    var folders = nationalFolders(topicId, member.unitId());
                    int required = (int) folders.stream().filter(Folder::required).count();
                    int completed = (int) folders.stream().filter(f -> f.required() && f.completed()).count();
                    String unitName = topic.members().stream().filter(m -> m.unitId().equals(String.valueOf(member.unitId())))
                            .map(m -> m.unitName()).findFirst().orElse(String.valueOf(member.unitId()));
                    result.add(new Directory(topic.id(), topic.name(), String.valueOf(member.unitId()), unitName,
                            folders.size(), required, completed, rate(required, completed)));
                }
            }
            if (slice.items().size() < 200) break;
        }
        return result;
    }

    @Transactional
    public List<Folder> nationalFolders(long topicId, long unitId) {
        requireFolderScope(topicId, unitId, "TOPIC_NATIONAL", false);
        ensureNationalFolders(topicId, unitId);
        return queryFolders("owner_type='TOPIC_NATIONAL' AND owner_id=? AND unit_id=?", topicId, unitId);
    }

    @Transactional
    public Folder addNationalFolder(long topicId, long unitId, String categoryName, String name, Boolean required) {
        requireNationalFolderManagement(topicId, unitId);
        categoryName = normalizedCategory(categoryName); name = name.trim();
        if (name.isEmpty()) throw BusinessException.validation("ARCHIVE_FOLDER_NAME_REQUIRED", "文件夹名称不能为空");
        ensureNationalFolders(topicId, unitId);
        if (db.queryForObject("SELECT COUNT(*) FROM archive_folder WHERE owner_type='TOPIC_NATIONAL' AND owner_id=? " +
                "AND unit_id=? AND name=? AND deleted_at IS NULL", Integer.class, topicId, unitId, name) > 0)
            throw BusinessException.conflict("ARCHIVE_FOLDER_EXISTS", "文件夹名称已存在");
        try {
            db.update("INSERT INTO archive_folder(owner_type,owner_id,topic_id,unit_id,category_name,name,required_flag,required_quantity," +
                    "custom_flag,created_by) VALUES('TOPIC_NATIONAL',?,?,?,?,?,?,1,1,?)", topicId, topicId, unitId,
                    categoryName, name, required == null || required, security.requireCurrentUser().id());
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("ARCHIVE_FOLDER_EXISTS", "文件夹名称已存在");
        }
        return folder(lastId());
    }

    @Transactional
    public void deleteFolder(long folderId) {
        var folder = folder(folderId, true);
        if ("TOPIC_NATIONAL".equals(folder.ownerType())) {
            requireNationalFolderManagement(Long.parseLong(folder.topicId()), Long.parseLong(folder.unitId()));
        } else if ("SELF_FUNDED".equals(folder.ownerType())) {
            project(Long.parseLong(folder.ownerId()));
            requireFolderScope(Long.parseLong(folder.topicId()), Long.parseLong(folder.unitId()), "SELF_FUNDED", true);
        } else {
            throw BusinessException.forbidden("ARCHIVE_FOLDER_DELETE_DENIED", "只能删除自定义归档文件夹");
        }
        if (!folder.canDelete()) throw BusinessException.forbidden("ARCHIVE_FOLDER_DELETE_DENIED", "无权删除该文件夹");
        if (folder.fileCount() > 0) throw BusinessException.conflict("ARCHIVE_FOLDER_NOT_EMPTY", "请先移除文件");
        db.update("UPDATE archive_folder SET deleted_at=NOW(3) WHERE id=? AND deleted_at IS NULL", folderId);
    }

    public List<FileView> files(long folderId) {
        var folder = folder(folderId, true);
        requireFolderScope(Long.parseLong(folder.topicId()), Long.parseLong(folder.unitId()), folder.ownerType(), false);
        return db.queryForList("SELECT file_id FROM archive_folder_file WHERE folder_id=? AND deleted_at IS NULL ORDER BY id DESC",
                Long.class, folderId).stream().map(fileService::readMetadata).toList();
    }

    @Transactional
    public FileView attach(long folderId, long fileId) {
        var folder = folder(folderId, true);
        requireFolderScope(Long.parseLong(folder.topicId()), Long.parseLong(folder.unitId()), folder.ownerType(), true);
        FileView file = fileService.requireArchiveOwnedReady(fileId);
        int changed = db.update("UPDATE archive_folder_file SET deleted_at=NULL,uploader_id=?,created_at=NOW(3) " +
                "WHERE folder_id=? AND file_id=? AND deleted_at IS NOT NULL",
                security.requireCurrentUser().id(), folderId, fileId);
        if (changed == 0) {
            try {
                db.update("INSERT INTO archive_folder_file(folder_id,file_id,uploader_id) VALUES(?,?,?)",
                        folderId, fileId, security.requireCurrentUser().id());
            } catch (DuplicateKeyException ex) {
                throw BusinessException.conflict("ARCHIVE_FILE_ATTACHED", "文件已经关联到该文件夹");
            }
        }
        return file;
    }

    @Transactional
    public void remove(long folderId, long fileId) {
        var folder = folder(folderId);
        requireFolderScope(Long.parseLong(folder.topicId()), Long.parseLong(folder.unitId()), folder.ownerType(), true);
        int changed = db.update("UPDATE archive_folder_file SET deleted_at=NOW(3) WHERE folder_id=? AND file_id=? AND deleted_at IS NULL",
                folderId, fileId);
        if (changed == 0) throw BusinessException.notFound("ARCHIVE_FILE_NOT_FOUND", "文件关联不存在");
        // The object is retained: it may be linked elsewhere and is not owned by this folder.
    }

    public List<Project> projects(Long topicFilter, Long unitFilter) {
        if (topicFilter != null) topics.getTopic(topicFilter);
        return db.query("SELECT * FROM self_funded_project ORDER BY id DESC", (rs, n) -> project(rs)).stream()
                .filter(p -> (topicFilter == null || p.topicId().equals(String.valueOf(topicFilter)))
                        && (unitFilter == null || p.ownerUnitId().equals(String.valueOf(unitFilter))))
                .filter(p -> canSee(Long.parseLong(p.topicId()), Long.parseLong(p.ownerUnitId()), "SELF_FUNDED"))
                .toList();
    }

    @Transactional
    public Project createProject(ProjectWrite request) {
        long topicId = TopicService.id(request.topicId());
        CurrentUser user = security.requireCurrentUser();
        if (!user.isInternalUnit() || user.unitId() == null)
            throw BusinessException.forbidden("SELF_FUNDED_INTERNAL_ONLY", "只有内部课题单位可创建自筹项目");
        requireFolderScope(topicId, user.unitId(), "SELF_FUNDED", true);
        validateProject(request);
        try {
            db.update("INSERT INTO self_funded_project(topic_id,owner_unit_id,code,name,project_type,principal_name," +
                            "start_date,end_date,budget,status,template_code,template_version,created_by,updated_by) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,1,?,?)", topicId, user.unitId(), request.code().trim(),
                    request.name().trim(), request.projectType(), request.principalName().trim(), request.startDate(),
                    request.endDate(), request.budget(), request.status() == null ? "筹备中" : request.status(),
                    request.projectType(), user.id(), user.id());
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("SELF_FUNDED_CODE_EXISTS", "本单位该课题项目编号已存在");
        }
        long projectId = lastId();
        createTemplateFolders("SELF_FUNDED", projectId, topicId, user.unitId(), request.projectType());
        return project(projectId);
    }

    public Project project(long projectId) {
        var rows = db.query("SELECT * FROM self_funded_project WHERE id=?", (rs, n) -> project(rs), projectId);
        if (rows.isEmpty()) throw BusinessException.notFound("SELF_FUNDED_NOT_FOUND", "自筹项目不存在");
        Project project = rows.getFirst();
        requireFolderScope(Long.parseLong(project.topicId()), Long.parseLong(project.ownerUnitId()), "SELF_FUNDED", false);
        return project;
    }

    @Transactional
    public Project updateProject(long projectId, ProjectWrite request) {
        Project old = project(projectId);
        requireFolderScope(Long.parseLong(old.topicId()), Long.parseLong(old.ownerUnitId()), "SELF_FUNDED", true);
        validateProject(request);
        if (!old.topicId().equals(request.topicId()) || !old.projectType().equals(request.projectType()))
            throw BusinessException.validation("SELF_FUNDED_IMMUTABLE_SCOPE", "课题与项目类型不能修改");
        if (request.recordVersion() == null || request.recordVersion() != old.recordVersion())
            throw BusinessException.conflict("SELF_FUNDED_VERSION_CONFLICT", "项目已被修改，请刷新");
        try {
            int changed = db.update("UPDATE self_funded_project SET code=?,name=?,principal_name=?,start_date=?,end_date=?,budget=?," +
                            "status=?,record_version=record_version+1,updated_by=? WHERE id=? AND record_version=?", request.code().trim(),
                    request.name().trim(), request.principalName().trim(), request.startDate(), request.endDate(),
                    request.budget(), request.status() == null ? old.status() : request.status(),
                    security.requireCurrentUser().id(), projectId, old.recordVersion());
            if (changed != 1)
                throw BusinessException.conflict("SELF_FUNDED_VERSION_CONFLICT", "项目已被修改，请刷新");
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("SELF_FUNDED_CODE_EXISTS", "本单位该课题项目编号已存在");
        }
        return project(projectId);
    }

    public List<Folder> projectFolders(long projectId) {
        project(projectId);
        return queryFolders("owner_type='SELF_FUNDED' AND owner_id=?", projectId);
    }

    @Transactional
    public Folder addProjectFolder(long projectId, String categoryName, String name, Boolean required) {
        Project project = project(projectId);
        long topicId = Long.parseLong(project.topicId()), unitId = Long.parseLong(project.ownerUnitId());
        requireFolderScope(topicId, unitId, "SELF_FUNDED", true);
        categoryName = normalizedCategory(categoryName); name = name.trim();
        if (name.isEmpty()) throw BusinessException.validation("ARCHIVE_FOLDER_NAME_REQUIRED", "文件夹名称不能为空");
        if (db.queryForObject("SELECT COUNT(*) FROM archive_folder WHERE owner_type='SELF_FUNDED' AND owner_id=? " +
                "AND unit_id=? AND name=? AND deleted_at IS NULL", Integer.class, projectId, unitId, name) > 0)
            throw BusinessException.conflict("ARCHIVE_FOLDER_EXISTS", "文件夹名称已存在");
        try {
            db.update("INSERT INTO archive_folder(owner_type,owner_id,topic_id,unit_id,category_name,name,required_flag,required_quantity," +
                            "custom_flag,created_by) VALUES('SELF_FUNDED',?,?,?,?,?,?,1,1,?)",
                    projectId, topicId, unitId, categoryName, name, required == null || required, security.requireCurrentUser().id());
        } catch (DuplicateKeyException ex) {
            throw BusinessException.conflict("ARCHIVE_FOLDER_EXISTS", "文件夹名称已存在");
        }
        return folder(lastId());
    }

    public List<Progress> progress(Long topicFilter, Long unitFilter, String ownerType) {
        if (topicFilter != null) topics.getTopic(topicFilter);
        if (ownerType != null && !Set.of("TOPIC_NATIONAL", "SELF_FUNDED").contains(ownerType))
            throw BusinessException.validation("INVALID_ARCHIVE_OWNER", "归档类型不正确");
        var folders = db.query("SELECT f.*, (SELECT COUNT(*) FROM archive_folder_file x WHERE x.folder_id=f.id AND x.deleted_at IS NULL) " +
                "file_count, (SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id " +
                "WHERE ur.user_id=f.created_by) creator_role FROM archive_folder f WHERE f.deleted_at IS NULL",
                (rs, n) -> folder(rs));
        Map<String, int[]> grouped = new LinkedHashMap<>();
        for (var folder : folders) {
            long topicId = Long.parseLong(folder.topicId()), unitId = Long.parseLong(folder.unitId());
            if (topicFilter != null && topicFilter != topicId || unitFilter != null && unitFilter != unitId ||
                    ownerType != null && !ownerType.equals(folder.ownerType()) || !canSee(topicId, unitId, folder.ownerType())) continue;
            if (!folder.required()) continue;
            String key = folder.topicId() + ":" + folder.unitId() + ":" + folder.ownerType() + ":" + folder.ownerId();
            int[] counts = grouped.computeIfAbsent(key, unused -> new int[2]);
            counts[0]++;
            if (folder.completed()) counts[1]++;
        }
        return grouped.entrySet().stream().map(entry -> {
            String[] parts = entry.getKey().split(":"); int[] counts = entry.getValue();
            return new Progress(parts[0], parts[1], parts[2], parts[3], counts[0], counts[1], rate(counts[0], counts[1]));
        }).toList();
    }

    private void ensureNationalFolders(long topicId, long unitId) {
        createTemplateFolders("TOPIC_NATIONAL", topicId, topicId, unitId, "NATIONAL");
    }
    private void createTemplateFolders(String ownerType, long ownerId, long topicId, long unitId, String template) {
        List<Requirement> requirements = TEMPLATES.get(template);
        if (requirements == null) throw BusinessException.validation("ARCHIVE_TEMPLATE_NOT_FOUND", "材料模板不存在");
        int index = 0;
        for (var item : requirements) {
            index++;
            db.update("INSERT IGNORE INTO archive_folder(owner_type,owner_id,topic_id,unit_id,category_name,name,required_flag,required_quantity," +
                    "custom_flag,created_by) VALUES(?,?,?,?,?,?,?,1,0,?)", ownerType, ownerId, topicId, unitId,
                    item.category(), item.name(), item.required(), security.requireCurrentUser().id());
        }
    }
    private List<Folder> queryFolders(String clause, Object... args) {
        return queryFolders(false, clause, args);
    }
    private List<Folder> queryFolders(boolean lock, String clause, Object... args) {
        return db.query("SELECT f.*, (SELECT COUNT(*) FROM archive_folder_file x WHERE x.folder_id=f.id AND x.deleted_at IS NULL) " +
                "file_count, (SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id " +
                "WHERE ur.user_id=f.created_by) creator_role FROM archive_folder f WHERE f.deleted_at IS NULL AND " +
                clause + " ORDER BY f.category_name,f.id" + (lock ? " FOR UPDATE" : ""), (rs, n) -> folder(rs), args);
    }
    private Folder folder(long id) { return folder(id, false); }
    private Folder folder(long id, boolean lock) {
        var rows = queryFolders(lock, "f.id=?", id);
        if (rows.isEmpty()) throw BusinessException.notFound("ARCHIVE_FOLDER_NOT_FOUND", "归档文件夹不存在");
        return rows.getFirst();
    }
    private Folder folder(ResultSet rs) throws SQLException {
        int fileCount = rs.getInt("file_count"), quantity = rs.getInt("required_quantity");
        CurrentUser user = security.requireCurrentUser();
        boolean canDelete = ArchiveFolderPolicy.canDelete(user, rs.getLong("unit_id"), rs.getString("owner_type"), rs.getBoolean("custom_flag"),
                rs.getLong("created_by"), rs.getString("creator_role"));
        return new Folder(String.valueOf(rs.getLong("id")), String.valueOf(rs.getLong("topic_id")),
                String.valueOf(rs.getLong("unit_id")), rs.getString("owner_type"), String.valueOf(rs.getLong("owner_id")),
                rs.getString("category_name"), rs.getString("name"), rs.getBoolean("required_flag"), quantity, rs.getBoolean("custom_flag"),
                fileCount, fileCount >= quantity, canDelete);
    }
    private static String normalizedCategory(String categoryName) {
        String value = categoryName == null ? "" : categoryName.trim();
        if (value.isEmpty()) throw BusinessException.validation("ARCHIVE_CATEGORY_REQUIRED", "请选择或输入管理阶段");
        return value;
    }
    private Project project(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        var folders = queryFolders("f.owner_type='SELF_FUNDED' AND f.owner_id=?", id);
        int required = (int) folders.stream().filter(Folder::required).count();
        int complete = (int) folders.stream().filter(f -> f.required() && f.completed()).count();
        return new Project(String.valueOf(id), String.valueOf(rs.getLong("topic_id")),
                String.valueOf(rs.getLong("owner_unit_id")), rs.getString("code"), rs.getString("name"),
                rs.getString("project_type"), rs.getString("principal_name"), date(rs, "start_date"), date(rs, "end_date"),
                rs.getBigDecimal("budget"), rs.getString("status"), rs.getInt("record_version"),
                rs.getString("template_code") + "-v" + rs.getInt("template_version"), rate(required, complete));
    }
    private LocalDate date(ResultSet rs, String field) throws SQLException {
        var value = rs.getDate(field); return value == null ? null : value.toLocalDate();
    }
    private void validateProject(ProjectWrite request) {
        if (!TEMPLATES.containsKey(request.projectType()) || "NATIONAL".equals(request.projectType()))
            throw BusinessException.validation("INVALID_SELF_FUNDED_TYPE", "自筹项目类型不正确");
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate()))
            throw BusinessException.validation("INVALID_PROJECT_DATES", "结束日期不能早于开始日期");
    }
    private void requireNationalFolderManagement(long topicId, long unitId) {
        requireAuthority("archive.topic.submit", "没有维护国家材料的权限");
        requireFolderScope(topicId, unitId, "TOPIC_NATIONAL", false);
        if (!ArchiveFolderPolicy.canManage(security.requireCurrentUser(), unitId, "TOPIC_NATIONAL"))
            throw BusinessException.forbidden("ARCHIVE_FOLDER_SCOPE_DENIED", "只能维护有权访问的单位目录");
        var topic = topics.lockTopic(topicId);
        if (!topic.enabled() || !"ACTIVE".equals(topic.status()))
            throw BusinessException.conflict("TOPIC_NOT_OPERATIONAL", "课题当前不能办理业务");
    }
    private void requireFolderScope(long topicId, long unitId, String ownerType, boolean write) {
        if (!canSee(topicId, unitId, ownerType))
            throw BusinessException.forbidden("ARCHIVE_SCOPE_DENIED", "没有访问该单位材料的权限");
        if (topics.listMembers(topicId, false).stream().noneMatch(m -> m.unitId() == unitId))
            throw BusinessException.notFound("TOPIC_UNIT_NOT_FOUND", "课题单位不存在");
        if (write) {
            var user = security.requireCurrentUser();
            requireAuthority("SELF_FUNDED".equals(ownerType) ? "self-funded.manage" : "archive.topic.submit",
                    "没有维护该类归档材料的权限");
            if (!user.isGlobalRole() && (user.unitId() == null || user.unitId() != unitId ||
                    (!user.isInternalUnit() && !user.isExternalUnit()) ||
                    "SELF_FUNDED".equals(ownerType) && !user.isInternalUnit()))
                throw BusinessException.forbidden("ARCHIVE_OWNER_REQUIRED", "只能维护本单位材料");
            var topic = topics.lockTopic(topicId);
            if (!topic.enabled() || !"ACTIVE".equals(topic.status()))
                throw BusinessException.conflict("TOPIC_NOT_OPERATIONAL", "课题当前不能办理业务");
        }
    }
    private void requireAuthority(String authority, String message) {
        if (!security.requireCurrentUser().authorities().contains(authority))
            throw BusinessException.forbidden("ARCHIVE_ACTION_DENIED", message);
    }
    boolean canSee(long topicId, long unitId, String ownerType) {
        var user = security.requireCurrentUser();
        if ("SELF_FUNDED".equals(ownerType) && user.isExternalUnit()) return false;
        if (!topics.canReadTopic(topicId)) return false;
        if (!topics.isBusinessVisibleTopic(topicId)) return false;
        if (user.isGlobalRole()) return true;
        if (user.unitId() == null) return false;
        return user.unitId() == unitId || topics.isLeadUnit(topicId, user.unitId());
    }
    private long lastId() { return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class); }
    private double rate(int required, int complete) { return required == 0 ? 100 : Math.round(complete * 10000.0 / required) / 100.0; }
}
