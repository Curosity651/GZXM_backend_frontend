package com.gzxm.server.modules.file.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.file.api.FileDtos.*;
import com.gzxm.server.modules.file.domain.FileObjectEntity;
import com.gzxm.server.modules.file.repository.FileObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {
    private static final Set<String> BUSINESS_TYPES = Set.of("ACHIEVEMENT", "ARCHIVE");
    private final FileObjectMapper files;
    private final FileStorage storage;
    private final SecurityContextFacade security;
    private final AppProperties properties;

    public FileService(FileObjectMapper files, FileStorage storage, SecurityContextFacade security, AppProperties properties) {
        this.files = files;
        this.storage = storage;
        this.security = security;
        this.properties = properties;
    }

    @Transactional
    public UploadTicket createTicket(UploadTicketRequest request) {
        if (!BUSINESS_TYPES.contains(request.businessType())) {
            throw BusinessException.validation("INVALID_FILE_BUSINESS_TYPE", "文件业务类型不正确");
        }
        CurrentUser user = security.requireCurrentUser();
        String objectKey = request.businessType().toLowerCase() + "/" + user.id() + "/" + UUID.randomUUID();
        FileObjectEntity file = new FileObjectEntity();
        file.setStorageProvider(storage.provider()); file.setBucketName("gzxm"); file.setObjectKey(objectKey);
        file.setOriginalName(request.fileName()); file.setContentType(request.contentType()); file.setSizeBytes(request.size());
        file.setSha256(request.sha256()); file.setStatus("PENDING"); file.setUploaderId(user.id()); file.setCreatedAt(LocalDateTime.now());
        files.insert(file);
        return new UploadTicket(String.valueOf(file.getId()), storage.createUploadUrl(objectKey, properties.file().uploadTicketTtl()),
                "PUT", Map.of("Content-Type", request.contentType()), Instant.now().plus(properties.file().uploadTicketTtl()));
    }

    @Transactional
    public FileView complete(long fileId) {
        FileObjectEntity file = requireOwned(fileId);
        if (!"PENDING".equals(file.getStatus())) throw BusinessException.conflict("FILE_ALREADY_COMPLETED", "文件已经完成或删除");
        if (!storage.exists(file.getObjectKey())) throw BusinessException.validation("FILE_OBJECT_MISSING", "存储中未找到上传对象");
        file.setStatus("READY"); file.setCompletedAt(LocalDateTime.now()); files.updateById(file);
        return toView(file);
    }

    public SignedUrl signedUrl(long fileId, boolean preview) {
        FileObjectEntity file = requireReadable(fileId);
        var ttl = properties.file().signedUrlTtl();
        return new SignedUrl(storage.createDownloadUrl(file.getObjectKey(), ttl, preview), Instant.now().plus(ttl));
    }

    private FileObjectEntity requireOwned(long id) {
        FileObjectEntity file = requireReadyOrPending(id);
        if (file.getUploaderId() != security.requireCurrentUser().id()) {
            throw BusinessException.forbidden("FILE_OWNER_REQUIRED", "只能完成自己上传的文件");
        }
        return file;
    }

    private FileObjectEntity requireReadable(long id) {
        FileObjectEntity file = requireReadyOrPending(id);
        CurrentUser user = security.requireCurrentUser();
        if (!user.isGlobalRole() && file.getUploaderId() != user.id()) {
            throw BusinessException.forbidden("FILE_SCOPE_DENIED", "没有查看该文件的权限");
        }
        if (!"READY".equals(file.getStatus())) throw BusinessException.conflict("FILE_NOT_READY", "文件尚未上传完成");
        return file;
    }

    private FileObjectEntity requireReadyOrPending(long id) {
        FileObjectEntity file = files.selectById(id);
        if (file == null || file.getDeletedAt() != null || "DELETED".equals(file.getStatus())) {
            throw BusinessException.notFound("FILE_NOT_FOUND", "文件不存在");
        }
        return file;
    }

    private FileView toView(FileObjectEntity file) {
        return new FileView(String.valueOf(file.getId()), file.getOriginalName(), file.getSizeBytes(), file.getContentType(),
                file.getSha256(), file.getStatus(), String.valueOf(file.getUploaderId()), file.getCreatedAt());
    }
}
