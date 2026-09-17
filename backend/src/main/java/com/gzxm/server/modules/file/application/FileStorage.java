package com.gzxm.server.modules.file.application;

import java.time.Duration;

public interface FileStorage {
    String provider();
    default boolean supportsProvider(String storedProvider) { return provider().equals(storedProvider); }
    String createUploadUrl(long fileId, String objectKey, Duration ttl);
    String createDownloadUrl(long fileId, String objectKey, Duration ttl, boolean preview);
    boolean exists(String objectKey);
    void write(String objectKey, byte[] content);
    byte[] read(String objectKey);
}
