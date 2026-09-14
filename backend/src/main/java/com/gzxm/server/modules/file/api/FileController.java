package com.gzxm.server.modules.file.api;

import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.file.api.FileDtos.*;
import com.gzxm.server.modules.file.application.FileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService service;
    private final AuditService audit;
    public FileController(FileService service, AuditService audit) { this.service = service; this.audit = audit; }

    @PostMapping("/upload-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('file.upload')")
    UploadTicket createTicket(@Valid @RequestBody UploadTicketRequest request) {
        UploadTicket result = service.createTicket(request); audit.success("file.ticket.create", "FILE", result.fileId()); return result;
    }

    @PostMapping("/{fileId}:complete")
    @PreAuthorize("hasAuthority('file.upload')")
    FileView complete(@PathVariable long fileId) {
        FileView result = service.complete(fileId); audit.success("file.upload.complete", "FILE", String.valueOf(fileId)); return result;
    }

    @GetMapping("/{fileId}/download-url")
    @PreAuthorize("hasAuthority('file.download')")
    SignedUrl download(@PathVariable long fileId) { return service.signedUrl(fileId, false); }

    @GetMapping("/{fileId}/preview-url")
    @PreAuthorize("hasAuthority('file.download')")
    SignedUrl preview(@PathVariable long fileId) { return service.signedUrl(fileId, true); }
}
