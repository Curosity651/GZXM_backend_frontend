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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
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
    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final Set<String> BUSINESS_TYPES = Set.of("ACHIEVEMENT", "ARCHIVE");
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.ofEntries(
            Map.entry("pdf", Set.of("application/pdf")),
            Map.entry("doc", Set.of("application/msword")),
            Map.entry("docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document")),
            Map.entry("xls", Set.of("application/vnd.ms-excel")),
            Map.entry("xlsx", Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")),
            Map.entry("ppt", Set.of("application/vnd.ms-powerpoint")),
            Map.entry("pptx", Set.of("application/vnd.openxmlformats-officedocument.presentationml.presentation")),
            Map.entry("png", Set.of("image/png")), Map.entry("jpg", Set.of("image/jpeg")),
            Map.entry("jpeg", Set.of("image/jpeg")),
            Map.entry("zip", Set.of("application/zip", "application/x-zip-compressed")));
    private final FileObjectMapper files;
    private final List<FileStorage> storages;
    private final FileStorage active;
    private final SecurityContextFacade security;
    private final AppProperties properties;
    private final List<FileReadPolicy> readPolicies;
    private final byte[] signingKey;

    @Autowired
    public FileService(FileObjectMapper files, List<FileStorage> storages, SecurityContextFacade security,
                       AppProperties properties, List<FileReadPolicy> readPolicies) {
        this.files = files;
        this.storages = List.copyOf(storages);
        this.active = this.storages.stream().filter(candidate -> candidate.provider().equalsIgnoreCase(properties.file().provider()))
                .findFirst().orElseThrow(() -> new IllegalStateException("未找到文件存储适配器: " + properties.file().provider()));
        this.security = security;
        this.properties = properties;
        this.readPolicies = readPolicies;
        String dedicated = properties.file().signingSecret();
        if (dedicated == null || dedicated.isBlank()) {
            log.warn("未配置 FILE_SIGNING_SECRET，文件签名回退复用 JWT 密钥；生产环境请配置独立密钥");
            dedicated = properties.security().jwtSecret();
        }
        this.signingKey = dedicated.getBytes(StandardCharsets.UTF_8);
    }

    public FileService(FileObjectMapper files, FileStorage storage, SecurityContextFacade security,
                       AppProperties properties) {
        this(files, List.of(storage), security, properties, List.of());
    }

    public FileService(FileObjectMapper files, FileStorage storage, SecurityContextFacade security,
                       AppProperties properties, List<FileReadPolicy> readPolicies) {
        this(files, List.of(storage), security, properties, readPolicies);
    }

    private FileStorage storageFor(String provider) {
        return storages.stream().filter(candidate -> candidate.supportsProvider(provider)).findFirst()
                .orElseThrow(() -> BusinessException.conflict("FILE_PROVIDER_UNAVAILABLE", "文件存储服务暂不可用"));
    }

    @Transactional
    public UploadTicket createTicket(UploadTicketRequest request) {
        if (!BUSINESS_TYPES.contains(request.businessType()))
            throw BusinessException.validation("INVALID_FILE_BUSINESS_TYPE", "文件业务类型不正确");
        if (request.size() > 100L * 1024 * 1024)
            throw BusinessException.validation("FILE_TOO_LARGE", "文件不能超过100MB");
        if (request.sha256() != null && !request.sha256().matches("(?i)[0-9a-f]{64}"))
            throw BusinessException.validation("INVALID_SHA256", "文件校验值不正确");
        validateAllowedType(request.fileName(), request.contentType());
        CurrentUser user = security.requireCurrentUser();
        String objectKey = request.businessType().toLowerCase() + "/" + user.id() + "/" + UUID.randomUUID();
        FileObjectEntity file = new FileObjectEntity();
        file.setStorageProvider(active.provider());
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
        String url = signed(active.createUploadUrl(file.getId(), objectKey, properties.file().uploadTicketTtl()),
                file.getId(), expires, "UPLOAD", objectKey);
        return new UploadTicket(String.valueOf(file.getId()), url, "PUT",
                Map.of("Content-Type", request.contentType()), expires);
    }

    @Transactional
    public void uploadContent(long fileId, long expires, String signature, InputStream content, String contentType) {
        FileObjectEntity file = requireOwnedLocked(fileId);
        verify(file, expires, signature, "UPLOAD");
        if (!"PENDING".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_ALREADY_COMPLETED", "文件已经完成或删除");
        if (contentType == null || !contentType.equalsIgnoreCase(file.getContentType()))
            throw BusinessException.validation("FILE_CONTENT_TYPE_MISMATCH", "文件类型与上传票据不一致");
        FileStorage storage = storageFor(file.getStorageProvider());
        DigestingStream measured = new DigestingStream(content);
        storage.write(file.getObjectKey(), measured, file.getSizeBytes());
        if (measured.count() != file.getSizeBytes()) {
            storage.delete(file.getObjectKey());
            throw BusinessException.validation("FILE_SIZE_MISMATCH", "文件大小与上传票据不一致");
        }
        if (file.getSha256() != null && !file.getSha256().equalsIgnoreCase(measured.hexDigest())) {
            storage.delete(file.getObjectKey());
            throw BusinessException.validation("FILE_HASH_MISMATCH", "文件校验值不一致");
        }
    }

    @Transactional
    public FileView complete(long fileId) {
        FileObjectEntity file = requireOwnedLocked(fileId);
        if (!"PENDING".equals(file.getStatus()))
            throw BusinessException.conflict("FILE_ALREADY_COMPLETED", "文件已经完成或删除");
        FileStorage storage = storageFor(file.getStorageProvider());
        if (!storage.exists(file.getObjectKey()))
            throw BusinessException.validation("FILE_OBJECT_MISSING", "存储中未找到上传对象");
        if (storage.size(file.getObjectKey()) != file.getSizeBytes())
            throw BusinessException.validation("FILE_SIZE_MISMATCH", "存储中的文件大小与票据不一致");
        file.setStatus("READY");
        file.setCompletedAt(LocalDateTime.now());
        files.updateById(file);
        return toView(file);
    }

    public SignedUrl signedUrl(long fileId, boolean preview) {
        FileObjectEntity file = requireReadable(fileId);
        Instant expires = Instant.now().plus(properties.file().signedUrlTtl());
        String url = signed(storageFor(file.getStorageProvider()).createDownloadUrl(fileId, file.getObjectKey(),
                properties.file().signedUrlTtl(), preview), fileId, expires, "READ", file.getObjectKey());
        return new SignedUrl(url, expires);
    }

    public FileContent readContent(long fileId, long expires, String signature) {
        FileObjectEntity file = requireReadable(fileId);
        verify(file, expires, signature, "READ");
        return new FileContent(toView(file), storageFor(file.getStorageProvider()).read(file.getObjectKey()));
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

    private FileObjectEntity requireOwnedLocked(long id) {
        FileObjectEntity file = files.lockById(id);
        if (file == null || file.getDeletedAt() != null || "DELETED".equals(file.getStatus()))
            throw BusinessException.notFound("FILE_NOT_FOUND", "文件不存在");
        storageFor(file.getStorageProvider());
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
        storageFor(file.getStorageProvider());
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
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((fileId + ":" + expires + ":" + action + ":" + objectKey)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("文件签名初始化失败", ex);
        }
    }

    private void validateAllowedType(String fileName, String contentType) {
        String name = fileName == null ? "" : fileName.trim();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1).toLowerCase() : "";
        Set<String> allowed = ALLOWED_TYPES.get(extension);
        String mime = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase();
        if (allowed == null || !allowed.contains(mime))
            throw BusinessException.validation("FILE_TYPE_NOT_ALLOWED", "仅允许上传 pdf/doc/docx/xls/xlsx/ppt/pptx/png/jpg/zip 格式且类型需与扩展名一致");
    }

    private FileView toView(FileObjectEntity file) {
        return new FileView(String.valueOf(file.getId()), file.getOriginalName(), file.getSizeBytes(),
                file.getContentType(), file.getSha256(), file.getStatus(), String.valueOf(file.getUploaderId()), file.getCreatedAt());
    }

    public record FileContent(FileView metadata, InputStream stream) {}

    private static final class DigestingStream extends java.io.FilterInputStream {
        private final MessageDigest digest;
        private long count;
        DigestingStream(InputStream input) {
            super(input);
            try { digest = MessageDigest.getInstance("SHA-256"); }
            catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        }
        @Override public int read() throws java.io.IOException {
            int value = super.read();
            if (value >= 0) { digest.update((byte) value); count++; }
            return value;
        }
        @Override public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) { digest.update(buffer, offset, read); count += read; }
            return read;
        }
        long count() { return count; }
        String hexDigest() { return HexFormat.of().formatHex(digest.digest()); }
    }
}
