package com.gzxm.server.modules.file.application;

import java.time.Duration;

public interface FileStorage {
    String provider();
    String createUploadUrl(String objectKey, Duration ttl);
    String createDownloadUrl(String objectKey, Duration ttl, boolean preview);
    boolean exists(String objectKey);
}
