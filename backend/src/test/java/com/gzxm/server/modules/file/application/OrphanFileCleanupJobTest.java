package com.gzxm.server.modules.file.application;

import com.gzxm.server.modules.file.domain.FileObjectEntity;
import com.gzxm.server.modules.file.repository.FileObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrphanFileCleanupJobTest {
    private final FileObjectMapper files = mock(FileObjectMapper.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final OrphanFileCleanupJob job = new OrphanFileCleanupJob(files, List.of(storage));

    @Test void deletesAgedPendingObjectsAndMetadata() {
        when(storage.supportsProvider("WEBDAV")).thenReturn(true);
        when(files.selectList(any())).thenReturn(List.of(orphan()));
        job.cleanup();
        verify(storage).delete("archive/7/key");
        verify(files).deleteById(9L);
    }

    @Test void keepsMetadataWhenNasDeleteFails() {
        when(storage.supportsProvider("WEBDAV")).thenReturn(true);
        when(files.selectList(any())).thenReturn(List.of(orphan()));
        doThrow(new IllegalStateException("NAS unavailable")).when(storage).delete(anyString());
        job.cleanup();
        verify(files, never()).deleteById(anyLong());
    }

    private FileObjectEntity orphan() {
        FileObjectEntity file = new FileObjectEntity();
        file.setId(9L); file.setStorageProvider("WEBDAV"); file.setObjectKey("archive/7/key"); file.setStatus("PENDING");
        return file;
    }
}
