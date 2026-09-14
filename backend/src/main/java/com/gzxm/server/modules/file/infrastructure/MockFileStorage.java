package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.modules.file.application.FileStorage;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MockFileStorage implements FileStorage {
    @Override public String provider() { return "MOCK"; }
    @Override public String createUploadUrl(String objectKey, Duration ttl) { return "mock://upload/" + objectKey; }
    @Override public String createDownloadUrl(String objectKey, Duration ttl, boolean preview) {
        return "mock://" + (preview ? "preview/" : "download/") + objectKey;
    }
    @Override public boolean exists(String objectKey) { return true; }
}
