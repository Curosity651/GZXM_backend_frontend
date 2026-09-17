package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.modules.file.application.FileStorage;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.common.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Component
public class MockFileStorage implements FileStorage {
    private final Path root;
    private final String provider;

    public MockFileStorage(AppProperties properties) {
        this.root = properties.file().mockRoot().toAbsolutePath().normalize();
        this.provider = properties.file().provider();
        if (!"MOCK".equals(provider) && !"FILESYSTEM".equals(provider))
            throw new IllegalArgumentException("不支持的文件存储类型: " + provider);
        if ("FILESYSTEM".equals(provider) && (!properties.file().mockRoot().isAbsolute()
                || !Files.isDirectory(root) || !Files.isWritable(root)))
            throw new IllegalArgumentException("FILESYSTEM 存储需要已存在且可写的绝对路径: " + root);
    }

    @Override public String provider() { return provider; }
    @Override public boolean supportsProvider(String storedProvider) {
        return "MOCK".equals(storedProvider) || "FILESYSTEM".equals(storedProvider);
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
