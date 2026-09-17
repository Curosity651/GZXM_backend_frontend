package com.gzxm.server.modules.archive.api;

import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.archive.application.ArchiveService;
import com.gzxm.server.modules.archive.api.ArchiveDtos.*;
import com.gzxm.server.modules.file.api.FileDtos.FileView;
import com.gzxm.server.modules.topic.application.TopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Archives")
public class ArchiveController {
    private final ArchiveService service;
    private final AuditService audit;
    public ArchiveController(ArchiveService service, AuditService audit) { this.service = service; this.audit = audit; }
    private Long id(String value) { return value == null ? null : TopicService.id(value); }

    @GetMapping("/archive/national") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listNationalArchiveDirectories")
    public List<Directory> directories(@RequestParam(required = false) String topicId,
                                       @RequestParam(required = false) String unitId) {
        return service.directories(id(topicId), id(unitId));
    }
    @GetMapping("/archive/national/topics/{topicId}/units/{unitId}/folders") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listNationalArchiveFolders")
    public List<Folder> nationalFolders(@PathVariable String topicId, @PathVariable String unitId) {
        return service.nationalFolders(TopicService.id(topicId), TopicService.id(unitId));
    }
    @PostMapping("/archive/national/topics/{topicId}/units/{unitId}/folders")
    @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "createNationalCustomFolder")
    public Folder addFolder(@PathVariable String topicId, @PathVariable String unitId, @Valid @RequestBody FolderCreate request) {
        var result = service.addNationalFolder(TopicService.id(topicId), TopicService.id(unitId), request.name(), request.required());
        audit.success("archive.folder.create", "ARCHIVE_FOLDER", result.id()); return result;
    }
    @DeleteMapping("/archive/folders/{folderId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()") @Operation(operationId = "deleteCustomArchiveFolder")
    public void deleteFolder(@PathVariable String folderId) {
        service.deleteFolder(TopicService.id(folderId)); audit.success("archive.folder.delete", "ARCHIVE_FOLDER", folderId);
    }
    @GetMapping("/archive/folders/{folderId}/files") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listArchiveFolderFiles")
    public List<FileView> files(@PathVariable String folderId) { return service.files(TopicService.id(folderId)); }
    @PostMapping("/archive/folders/{folderId}/files") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('archive.topic.submit') or hasAuthority('self-funded.manage')")
    @Operation(operationId = "attachFileToArchiveFolder")
    public FileView attach(@PathVariable String folderId, @Valid @RequestBody FileLink request) {
        var result = service.attach(TopicService.id(folderId), TopicService.id(request.fileId()));
        audit.success("archive.file.attach", "ARCHIVE_FOLDER", folderId); return result;
    }
    @DeleteMapping("/archive/folders/{folderId}/files/{fileId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('archive.topic.submit') or hasAuthority('self-funded.manage')")
    @Operation(operationId = "removeArchiveFolderFile")
    public void remove(@PathVariable String folderId, @PathVariable String fileId) {
        service.remove(TopicService.id(folderId), TopicService.id(fileId));
        audit.success("archive.file.remove", "ARCHIVE_FOLDER", folderId);
    }
    @GetMapping("/self-funded-projects") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listSelfFundedProjects")
    public List<Project> projects(@RequestParam(required = false) String topicId,
                                  @RequestParam(required = false) String unitId) {
        return service.projects(id(topicId), id(unitId));
    }
    @PostMapping("/self-funded-projects") @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('self-funded.manage')") @Operation(operationId = "createSelfFundedProject")
    public Project create(@Valid @RequestBody ProjectWrite request) {
        var result = service.createProject(request); audit.success("self-funded.create", "SELF_FUNDED", result.id()); return result;
    }
    @GetMapping("/self-funded-projects/{projectId}") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getSelfFundedProject")
    public Project project(@PathVariable String projectId) { return service.project(TopicService.id(projectId)); }
    @PutMapping("/self-funded-projects/{projectId}") @PreAuthorize("hasAuthority('self-funded.manage')")
    @Operation(operationId = "updateSelfFundedProject")
    public Project update(@PathVariable String projectId, @Valid @RequestBody ProjectWrite request) {
        var result = service.updateProject(TopicService.id(projectId), request);
        audit.success("self-funded.update", "SELF_FUNDED", projectId); return result;
    }
    @GetMapping("/self-funded-projects/{projectId}/folders") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "listSelfFundedProjectFolders")
    public List<Folder> projectFolders(@PathVariable String projectId) { return service.projectFolders(TopicService.id(projectId)); }
    @GetMapping("/archive-progress") @PreAuthorize("isAuthenticated()")
    @Operation(operationId = "getArchiveProgress")
    public List<Progress> progress(@RequestParam(required = false) String topicId,
                                   @RequestParam(required = false) String unitId,
                                   @RequestParam(required = false) String ownerType) {
        return service.progress(id(topicId), id(unitId), ownerType);
    }
}
