package com.gzxm.server.modules.file.api;

import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.file.api.FileDtos.*;
import com.gzxm.server.modules.file.application.FileService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.io.InputStreamResource;

@RestController
@RequestMapping("/api/v1/files")
@Tag(name = "Files")
public class FileController {
    private static final Set<String> INLINE_TYPES = Set.of(
            "application/pdf", "image/png", "image/jpeg", "image/gif", "image/webp");
    private final FileService service;
    private final AuditService audit;
    public FileController(FileService service, AuditService audit) { this.service = service; this.audit = audit; }

    @PostMapping("/upload-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('file.upload')")
    @Operation(operationId = "createUploadTicket")
    UploadTicket createTicket(@Valid @RequestBody UploadTicketRequest request) {
        UploadTicket result = service.createTicket(request); audit.success("file.ticket.create", "FILE", result.fileId()); return result;
    }

    @PostMapping("/{fileId}:complete")
    @PreAuthorize("hasAuthority('file.upload')")
    @Operation(operationId = "completeUpload")
    FileView complete(@PathVariable long fileId) {
        FileView result = service.complete(fileId); audit.success("file.upload.complete", "FILE", String.valueOf(fileId)); return result;
    }

    @GetMapping("/{fileId}/download-url")
    @PreAuthorize("hasAuthority('file.download')")
    @Operation(operationId = "getDownloadUrl")
    SignedUrl download(@PathVariable long fileId) { return service.signedUrl(fileId, false); }

    @GetMapping("/{fileId}/preview-url")
    @PreAuthorize("hasAuthority('file.download')")
    @Operation(operationId = "getPreviewUrl")
    SignedUrl preview(@PathVariable long fileId) { return service.signedUrl(fileId, true); }

    @PutMapping("/{fileId}/content")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('file.upload')")
    @Operation(operationId = "uploadFileContent")
    void uploadContent(@PathVariable long fileId, @RequestParam long expires, @RequestParam String signature,
                       @RequestHeader("Content-Type") String contentType, InputStream content) {
        service.uploadContent(fileId, expires, signature, content, contentType);
    }

    @GetMapping("/{fileId}/content")
    @PreAuthorize("hasAuthority('file.download')")
    @Operation(operationId = "readFileContent")
    ResponseEntity<InputStreamResource> readContent(@PathVariable long fileId, @RequestParam long expires,
                                       @RequestParam String signature, @RequestParam(defaultValue = "false") boolean preview) {
        var content = service.readContent(fileId, expires, signature);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.metadata().contentType());
        } catch (IllegalArgumentException ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String mime = mediaType.getType() + "/" + mediaType.getSubtype();
        var disposition = preview && INLINE_TYPES.contains(mime)
                ? ContentDisposition.inline() : ContentDisposition.attachment();
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.metadata().size())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Disposition", disposition.filename(content.metadata().originalName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(content.stream()));
    }
}
