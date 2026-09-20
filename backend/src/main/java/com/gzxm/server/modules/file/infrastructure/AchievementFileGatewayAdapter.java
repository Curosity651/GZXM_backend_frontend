package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.modules.achievement.application.AchievementFileGateway;
import com.gzxm.server.modules.file.api.FileDtos.FileView;
import com.gzxm.server.modules.file.application.FileService;
import org.springframework.stereotype.Component;

/** Connects achievement material references to the configured file storage provider. */
@Component
public class AchievementFileGatewayAdapter implements AchievementFileGateway {
    private final FileService files;

    public AchievementFileGatewayAdapter(FileService files) {
        this.files = files;
    }

    @Override
    public FileView requireOwnedReady(long fileId) {
        return files.requireOwnedReady(fileId);
    }

    @Override
    public FileView readMetadata(long fileId) {
        return files.readMetadata(fileId);
    }
}
