package com.gzxm.server.modules.file.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.file.api.FileDtos.*;
import com.gzxm.server.modules.file.domain.FileObjectEntity;
import com.gzxm.server.modules.file.repository.FileObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
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
    private final List<FileReadPolicy> readPolicies;

    @Autowired
    public FileService(FileObjectMapper files, FileStorage storage, SecurityContextFacade security,
                       AppProperties properties, List<FileReadPolicy> readPolicies) {
        this.files = files;
        this.storage = storage;
        this.security = security;
        this.properties = properties;
        this.readPolicies = readPolicies;
    }

    public FileService(FileObjectMapper files, FileStorage storage, SecurityContextFacade security,
                       AppProperties properties) {
        this(files, storage, security, properties, List.of());
    }

    @Transactional
    public UploadTicket createTicket(UploadTicketRequest request) {
        if (!BUSINESS_TYPES.contains(request.businessType()))
            throw BusinessException.validation("INVALID_FILE_BUSINESS_TYPE", "文件业务类型不正确");
        if (request.size() > 100L * 1024 * 1024)
            throw BusinessException.validation("FILE_TOO_LARGE", "文件不能超过100MB");
        if (request.sha256() != null && !request.sha256().matches("(?i)[0-9a-f]{64}"))
            throw BusinessException.validation("INVALID_SHA256", "文件校验值不正确");
        CurrentUser user = security.requireCurrentUser();
        String objectKey = request.businessType().toLowerCase() + "/" + user.id() + "/" + UUID.randomUUID();
        FileObjectEntity file = new FileObjectEntity();
        file.setStorageProvider(storage.provider());
        file.setBucketName("gzxm");
        file.setObjectKey(objectKey);
        file.setOriginalName(request.fileName());
        file.setContentType(request.contentType());
        file.setSizeBytes(request.size());
        file.setSha256(request.sha256());
        file.setStatus("PENDING");
        file.setUploaderId(user.id());
        file.setCreatedAt(LocalDateTime.now());
        files.insert(file);
        Instant expires = Instant.now().plus(properties.file().uploadTicketTtl());
        String url = signed(storage.createUploadUrl(file.getId(), objectKey, properties.file().uploadTicketTtl()),
                file.getId(), expires, "UPLOAD", objectKey);
        return new UploadTicket(String.valueOf(file.getId()), url, "PUT",
                Map.of("Content-Type", request.contentType()), expires);
    }

    public void uploadContent(long fileId, long expires, String signature, byte[] content, String contentType) {
        FileObjectEntity file = requireOwned(fileId);
        verify(file, expires, signature, "UPLOAD");
        if (!"PENDING".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_ALREADY_COMPLETED", "文件已经完成或删除");
        if (content.length != file.getSizeBytes())
            throw BusinessException.validation("FILE_SIZE_MISMATCH", "文件大小与上传票据不一致");
        if (contentType == null || !contentType.equalsIgnoreCase(file.getContentType()))
            throw BusinessException.validation("FILE_CONTENT_TYPE_MISMATCH", "文件类型与上传票据不一致");
        if (file.getSha256() != null && !file.getSha256().equalsIgnoreCase(sha256(content)))
            throw BusinessException.validation("FILE_HASH_MISMATCH", "文件校验值不一致");
        storage.write(file.getObjectKey(), content);
    }

    @Transactional
    public FileView complete(long fileId) {
        FileObjectEntity file = requireOwned(fileId);
        if (!"PENDING".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_ALREADY_COMPLETED", "文件已经完成或删除");
        if (!storage.exists(file.getObjectKey()))
            throw BusinessException.validation("FILE_OBJECT_MISSING", "存储中未找到上传对象");
        byte[] content = storage.read(file.getObjectKey());
        if (content.length != file.getSizeBytes())
            throw BusinessException.validation("FILE_SIZE_MISMATCH", "存储中的文件大小与票据不一致");
        if (file.getSha256() != null && !file.getSha256().equalsIgnoreCase(sha256(content)))
            throw BusinessException.validation("FILE_HASH_MISMATCH", "存储中的文件校验值不一致");
        file.setStatus("READY");
        file.setCompletedAt(LocalDateTime.now());
        files.updateById(file);
        return toView(file);
    }

    public SignedUrl signedUrl(long fileId, boolean preview) {
        FileObjectEntity file = requireReadable(fileId);
        Instant expires = Instant.now().plus(properties.file().signedUrlTtl());
        String url = signed(storage.createDownloadUrl(fileId, file.getObjectKey(),
                properties.file().signedUrlTtl(), preview), fileId, expires, "READ", file.getObjectKey());
        return new SignedUrl(url, expires);
    }

    public FileContent readContent(long fileId, long expires, String signature) {
        FileObjectEntity file = requireReadable(fileId);
        verify(file, expires, signature, "READ");
        return new FileContent(toView(file), storage.read(file.getObjectKey()));
    }

    public FileView requireOwnedReady(long fileId) {
        FileObjectEntity file = requireOwned(fileId);
        if (!"READY".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_NOT_READY", "文件尚未上传完成");
        return toView(file);
    }

    public FileView requireArchiveOwnedReady(long fileId) {
        FileObjectEntity file = requireOwned(fileId);
        if (!file.getObjectKey().startsWith("archive/"))
            throw BusinessException.validation("FILE_BUSINESS_TYPE_MISMATCH", "文件不是归档材料");
        if (!"READY".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_NOT_READY", "文件尚未上传完成");
        return toView(file);
    }

    public FileView readMetadata(long fileId) { return toView(requireReadable(fileId)); }

    private FileObjectEntity requireOwned(long id) {
        FileObjectEntity file = requireExisting(id);
        if (file.getUploaderId() != security.requireCurrentUser().id())
            throw BusinessException.forbidden("FILE_OWNER_REQUIRED", "只能操作自己上传的文件");
        return file;
    }

    private FileObjectEntity requireReadable(long id) {
        FileObjectEntity file = requireExisting(id);
        CurrentUser user = security.requireCurrentUser();
        if (!user.isGlobalRole() && file.getUploaderId() != user.id()
                && readPolicies.stream().noneMatch(policy -> policy.canRead(id, user)))
            throw BusinessException.forbidden("FILE_SCOPE_DENIED", "没有查看该文件的权限");
        if (!"READY".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_NOT_READY", "文件尚未上传完成");
        return file;
    }

    private FileObjectEntity requireExisting(long id) {
        FileObjectEntity file = files.selectById(id);
        if (file == null || file.getDeletedAt() != null || "DELETED".equals(file.getStatus()))
            throw BusinessException.notFound("FILE_NOT_FOUND", "文件不存在");
        if (!storage.supportsProvider(file.getStorageProvider()))
            throw BusinessException.conflict("FILE_PROVIDER_UNAVAILABLE", "文件存储服务暂不可用");
        return file;
    }

    private String signed(String base, long fileId, Instant expires, String action, String objectKey) {
        return base + (base.contains("?") ? "&" : "?") + "expires=" + expires.getEpochSecond() + "&signature="
                + signature(fileId, expires.getEpochSecond(), action, objectKey);
    }

    private void verify(FileObjectEntity file, long expires, String supplied, String action) {
        if (expires < Instant.now().getEpochSecond() || supplied == null || !MessageDigest.isEqual(
                signature(file.getId(), expires, action, file.getObjectKey()).getBytes(StandardCharsets.US_ASCII),
                supplied.getBytes(StandardCharsets.US_ASCII)))
            throw BusinessException.forbidden("FILE_TICKET_INVALID", "文件地址已失效");
    }

    private String signature(long fileId, long expires, String action, String objectKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((fileId + ":" + expires + ":" + action + ":" + objectKey)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("文件签名初始化失败", ex);
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private FileView toView(FileObjectEntity file) {
        return new FileView(String.valueOf(file.getId()), file.getOriginalName(), file.getSizeBytes(),
                file.getContentType(), file.getSha256(), file.getStatus(), String.valueOf(file.getUploaderId()), file.getCreatedAt());
    }

    public record FileContent(FileView metadata, byte[] bytes) {}
}
