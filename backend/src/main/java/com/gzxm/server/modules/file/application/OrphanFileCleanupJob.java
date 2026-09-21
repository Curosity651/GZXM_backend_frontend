package com.gzxm.server.modules.file.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.gzxm.server.modules.file.domain.FileObjectEntity;
import com.gzxm.server.modules.file.repository.FileObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrphanFileCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(OrphanFileCleanupJob.class);
    private static final Duration ORPHAN_AGE = Duration.ofHours(24);
    private static final int BATCH_LIMIT = 200;
    private final FileObjectMapper files;
    private final List<FileStorage> storages;

    public OrphanFileCleanupJob(FileObjectMapper files, List<FileStorage> storages) {
        this.files = files;
        this.storages = List.copyOf(storages);
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        LocalDateTime cutoff = LocalDateTime.now().minus(ORPHAN_AGE);
        List<FileObjectEntity> orphans = files.selectList(new LambdaQueryWrapper<FileObjectEntity>()
                .eq(FileObjectEntity::getStatus, "PENDING")
                .lt(FileObjectEntity::getCreatedAt, cutoff)
                .last("LIMIT " + BATCH_LIMIT));
        int removed = 0;
        for (FileObjectEntity file : orphans) {
            try { storageFor(file.getStorageProvider()).delete(file.getObjectKey()); }
            catch (Exception ex) {
                log.warn("孤儿文件删除失败，保留记录待重试: id={} provider={} reason={}",
                        file.getId(), file.getStorageProvider(), ex.getMessage());
                continue;
            }
            files.deleteById(file.getId());
            removed++;
        }
        if (!orphans.isEmpty()) log.info("孤儿文件清理完成: 扫描 {} 条, 删除 {} 条", orphans.size(), removed);
    }

    private FileStorage storageFor(String provider) {
        return storages.stream().filter(candidate -> candidate.supportsProvider(provider)).findFirst()
                .orElseThrow(() -> new IllegalStateException("未找到文件存储适配器: " + provider));
    }
}
