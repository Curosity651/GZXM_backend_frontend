package com.gzxm.server.modules.file.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.file.domain.FileObjectEntity;
import com.gzxm.server.modules.file.repository.FileObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class FileServiceTest {
    private final FileObjectMapper files = mock(FileObjectMapper.class);
    private final FileStorage storage = mock(FileStorage.class);
    private final SecurityContextFacade security = mock(SecurityContextFacade.class);
    private FileService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties(null, null,
                new AppProperties.File("MOCK", Path.of("storage/mock"), Duration.ofMinutes(15), Duration.ofMinutes(10)));
        service = new FileService(files, storage, security, properties);
    }

    @Test
    void allowsOwnerToCompleteUploadedObject() {
        FileObjectEntity file = pendingFile(7);
        when(files.selectById(9L)).thenReturn(file);
        when(security.requireCurrentUser()).thenReturn(user(7, "INTERNAL_TOPIC_UNIT"));
        when(storage.exists("archive/7/key")).thenReturn(true);

        assertThat(service.complete(9).status()).isEqualTo("READY");
        verify(files).updateById(file);
    }

    @Test
    void rejectsAnotherUnitFromReadingFileByGuessedId() {
        FileObjectEntity file = pendingFile(7);
        file.setStatus("READY");
        when(files.selectById(9L)).thenReturn(file);
        when(security.requireCurrentUser()).thenReturn(user(8, "EXTERNAL_TOPIC_UNIT"));

        assertThatThrownBy(() -> service.signedUrl(9, true)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("FILE_SCOPE_DENIED");
    }

    @Test
    void allowsGlobalReviewerToReadReadyFile() {
        FileObjectEntity file = pendingFile(7);
        file.setStatus("READY");
        when(files.selectById(9L)).thenReturn(file);
        when(security.requireCurrentUser()).thenReturn(user(1, "RESEARCH_ASSISTANT"));
        when(storage.createDownloadUrl("archive/7/key", Duration.ofMinutes(10), true)).thenReturn("mock://download");

        assertThat(service.signedUrl(9, true).url()).isEqualTo("mock://download");
    }

    private FileObjectEntity pendingFile(long uploaderId) {
        FileObjectEntity file = new FileObjectEntity();
        file.setId(9L); file.setUploaderId(uploaderId); file.setObjectKey("archive/7/key"); file.setStatus("PENDING");
        file.setOriginalName("material.pdf"); file.setContentType("application/pdf"); file.setSizeBytes(128L);
        return file;
    }

    private CurrentUser user(long id, String role) {
        return new CurrentUser(id, "user", role.endsWith("TOPIC_UNIT") ? id : null, role, Set.of(), List.of(), 0);
    }
}
