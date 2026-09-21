package com.gzxm.server.modules.file.application;

import java.io.InputStream;
import java.time.Duration;

public interface FileStorage {
    String provider();
    default boolean supportsProvider(String storedProvider) { return provider().equals(storedProvider); }
    String createUploadUrl(long fileId, String objectKey, Duration ttl);
    String createDownloadUrl(long fileId, String objectKey, Duration ttl, boolean preview);
    boolean exists(String objectKey);
    long size(String objectKey);
    void write(String objectKey, InputStream content, long size);
    InputStream read(String objectKey);
    void delete(String objectKey);
}
