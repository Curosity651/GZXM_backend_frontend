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
    private record Requirement(String name, boolean required) {}
    private static final Map<String, List<Requirement>> TEMPLATES = Map.of(
            "NATIONAL", List.of(new Requirement("申报评审过程材料", false), new Requirement("保密协议", false),
                    new Requirement("实验任务书及实验记录", false), new Requirement("知识产权证明材料", true),
                    new Requirement("项目成果统计分析文件", false), new Requirement("经费执行情况报告", false)),
            "TECHNOLOGY", List.of(new Requirement("项目立项文件", true), new Requirement("合同及技术协议", true),
                    new Requirement("实施方案及过程报告", true), new Requirement("成果证明材料", true),
                    new Requirement("验收证书及验收报告", true)),
            "RENOVATION", List.of(new Requirement("项目建议书及批复", true), new Requirement("招投标及合同材料", true),
                    new Requirement("施工及设备调试记录", false), new Requirement("竣工验收及结算材料", true)),
            "INFRASTRUCTURE", List.of(new Requirement("立项及可研材料", true), new Requirement("招投标及合同材料", true),
                    new Requirement("设备到货与安装调试记录", false), new Requirement("竣工验收材料", true)));

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
    public Folder addNationalFolder(long topicId, long unitId, String name, Boolean required) {
        requireNationalFolderManagement(topicId, unitId);
        name = name.trim();
        if (name.isEmpty()) throw BusinessException.validation("ARCHIVE_FOLDER_NAME_REQUIRED", "文件夹名称不能为空");
        ensureNationalFolders(topicId, unitId);
        if (db.queryForObject("SELECT COUNT(*) FROM archive_folder WHERE owner_type='TOPIC_NATIONAL' AND owner_id=? " +
                "AND unit_id=? AND name=? AND deleted_at IS NULL", Integer.class, topicId, unitId, name) > 0)
            throw BusinessException.conflict("ARCHIVE_FOLDER_EXISTS", "文件夹名称已存在");
        db.update("INSERT INTO archive_folder(owner_type,owner_id,topic_id,unit_id,name,required_flag,required_quantity," +
                "custom_flag,created_by) VALUES('TOPIC_NATIONAL',?,?,?,?,?,1,1,?)", topicId, topicId, unitId,
                name, required == null || required, security.requireCurrentUser().id());
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
    public Folder addProjectFolder(long projectId, String name, Boolean required) {
        Project project = project(projectId);
        long topicId = Long.parseLong(project.topicId()), unitId = Long.parseLong(project.ownerUnitId());
        requireFolderScope(topicId, unitId, "SELF_FUNDED", true);
        name = name.trim();
        if (name.isEmpty()) throw BusinessException.validation("ARCHIVE_FOLDER_NAME_REQUIRED", "文件夹名称不能为空");
        if (db.queryForObject("SELECT COUNT(*) FROM archive_folder WHERE owner_type='SELF_FUNDED' AND owner_id=? " +
                "AND unit_id=? AND name=? AND deleted_at IS NULL", Integer.class, projectId, unitId, name) > 0)
            throw BusinessException.conflict("ARCHIVE_FOLDER_EXISTS", "文件夹名称已存在");
        db.update("INSERT INTO archive_folder(owner_type,owner_id,topic_id,unit_id,name,required_flag,required_quantity," +
                        "custom_flag,created_by) VALUES('SELF_FUNDED',?,?,?,?,?,1,1,?)",
                projectId, topicId, unitId, name, required == null || required, security.requireCurrentUser().id());
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
        if (db.queryForObject("SELECT COUNT(*) FROM archive_folder WHERE owner_type='TOPIC_NATIONAL' AND owner_id=? AND unit_id=?",
                Integer.class, topicId, unitId) > 0) return;
        createTemplateFolders("TOPIC_NATIONAL", topicId, topicId, unitId, "NATIONAL");
    }
    private void createTemplateFolders(String ownerType, long ownerId, long topicId, long unitId, String template) {
        List<Requirement> requirements = TEMPLATES.get(template);
        if (requirements == null) throw BusinessException.validation("ARCHIVE_TEMPLATE_NOT_FOUND", "材料模板不存在");
        int index = 0;
        for (var item : requirements) {
            index++;
            db.update("INSERT INTO archive_folder(owner_type,owner_id,topic_id,unit_id,name,required_flag,required_quantity," +
                    "custom_flag,created_by) VALUES(?,?,?,?,?,?,1,0,?)", ownerType, ownerId, topicId, unitId,
                    item.name(), item.required(), security.requireCurrentUser().id());
        }
    }
    private List<Folder> queryFolders(String clause, Object... args) {
        return queryFolders(false, clause, args);
    }
    private List<Folder> queryFolders(boolean lock, String clause, Object... args) {
        return db.query("SELECT f.*, (SELECT COUNT(*) FROM archive_folder_file x WHERE x.folder_id=f.id AND x.deleted_at IS NULL) " +
                "file_count, (SELECT r.code FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id " +
                "WHERE ur.user_id=f.created_by) creator_role FROM archive_folder f WHERE f.deleted_at IS NULL AND " +
                clause + " ORDER BY f.id" + (lock ? " FOR UPDATE" : ""), (rs, n) -> folder(rs), args);
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
                rs.getString("name"), rs.getBoolean("required_flag"), quantity, rs.getBoolean("custom_flag"),
                fileCount, fileCount >= quantity, canDelete);
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
