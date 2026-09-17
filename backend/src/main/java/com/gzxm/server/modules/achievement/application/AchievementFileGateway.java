package com.gzxm.server.modules.achievement.application;
import com.gzxm.server.modules.file.api.FileDtos.FileView;
/** Boundary awaiting A's public file-reference capability; never read its Mapper here. */
public interface AchievementFileGateway {
    FileView requireOwnedReady(long fileId);
    FileView readMetadata(long fileId);
}
