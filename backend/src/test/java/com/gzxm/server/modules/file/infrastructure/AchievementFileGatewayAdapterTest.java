package com.gzxm.server.modules.file.infrastructure;

import com.gzxm.server.modules.file.api.FileDtos.FileView;
import com.gzxm.server.modules.file.application.FileService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AchievementFileGatewayAdapterTest {
    @Test
    void delegatesOwnershipAndMetadataChecksToTheRealFileService() {
        FileService files = mock(FileService.class);
        FileView view = new FileView("7", "proof.pdf", 10, "application/pdf", null,
                "READY", "12", LocalDateTime.of(2026, 9, 20, 12, 0));
        when(files.requireOwnedReady(7)).thenReturn(view);
        when(files.readMetadata(7)).thenReturn(view);

        var adapter = new AchievementFileGatewayAdapter(files);

        assertThat(adapter.requireOwnedReady(7)).isSameAs(view);
        assertThat(adapter.readMetadata(7)).isSameAs(view);
        verify(files).requireOwnedReady(7);
        verify(files).readMetadata(7);
    }
}
