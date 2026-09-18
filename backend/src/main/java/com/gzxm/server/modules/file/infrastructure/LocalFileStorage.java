package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.file.application.FileStorage;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(AppProperties properties) {
        if (!"FILESYSTEM".equalsIgnoreCase(properties.file().provider()))
            throw new IllegalArgumentException("当前仅支持 FILESYSTEM 文件存储；对象存储接入后再增加对应适配器");
        this.root = properties.file().root().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
            if (!Files.isWritable(root)) throw new IOException("目录不可写");
        } catch (IOException ex) {
            throw new IllegalArgumentException("FILESYSTEM 存储目录无法创建或不可写: " + root, ex);
        }
    }

    @Override public String provider() { return "FILESYSTEM"; }
    @Override public boolean supportsProvider(String storedProvider) {
        return "FILESYSTEM".equals(storedProvider) || "MOCK".equals(storedProvider);
    }
    @Override public String createUploadUrl(long fileId, String objectKey, Duration ttl) {
        return "/api/v1/files/" + fileId + "/content";
    }
    @Override public String createDownloadUrl(long fileId, String objectKey, Duration ttl, boolean preview) {
        return "/api/v1/files/" + fileId + "/content?preview=" + preview;
    }
    @Override public boolean exists(String objectKey) { return Files.isRegularFile(path(objectKey)); }

    @Override public void write(String objectKey, byte[] content) {
        Path target = path(objectKey);
        try {
            Files.createDirectories(target.getParent());
            Path temporary = Files.createTempFile(target.getParent(), "upload-", ".tmp");
            try {
                Files.write(temporary, content);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException ex) {
            throw BusinessException.conflict("FILE_STORAGE_WRITE_FAILED", "文件写入失败");
        }
    }

    @Override public byte[] read(String objectKey) {
        try {
            return Files.readAllBytes(path(objectKey));
        } catch (IOException ex) {
            throw BusinessException.notFound("FILE_OBJECT_MISSING", "文件内容不存在");
        }
    }

    private Path path(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw BusinessException.validation("INVALID_OBJECT_KEY", "文件对象键不正确");
        return target;
    }
}
