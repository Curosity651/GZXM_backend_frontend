package com.gzxm.server.modules.file.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.file.domain.FileObjectEntity;
import com.gzxm.server.modules.file.api.FileDtos.UploadTicketRequest;
import com.gzxm.server.modules.file.repository.FileObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicReference;

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
        properties = new AppProperties(new AppProperties.Security("test", "test-secret-with-at-least-thirty-two-characters",
                Duration.ofMinutes(15), Duration.ofDays(1), "http://localhost:5173"), null, properties.file());
        when(storage.provider()).thenReturn("MOCK");
        when(storage.supportsProvider("MOCK")).thenReturn(true);
        service = new FileService(files, storage, security, properties);
    }

    @Test
    void allowsOwnerToCompleteUploadedObject() {
        FileObjectEntity file = pendingFile(7);
        when(files.selectById(9L)).thenReturn(file);
        when(security.requireCurrentUser()).thenReturn(user(7, "INTERNAL_TOPIC_UNIT"));
        when(storage.exists("archive/7/key")).thenReturn(true);
        when(storage.read("archive/7/key")).thenReturn(new byte[128]);

        assertThat(service.complete(9).status()).isEqualTo("READY");
        verify(files).updateById(file);
    }

    @Test
    void refusesToCompleteDamagedStoredContent() {
        FileObjectEntity file = pendingFile(7);
        when(files.selectById(9L)).thenReturn(file);
        when(security.requireCurrentUser()).thenReturn(user(7, "INTERNAL_TOPIC_UNIT"));
        when(storage.exists("archive/7/key")).thenReturn(true);
        when(storage.read("archive/7/key")).thenReturn(new byte[3]);
        assertThatThrownBy(() -> service.complete(9)).isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("FILE_SIZE_MISMATCH");
        verify(files, never()).updateById(any(FileObjectEntity.class));
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
        when(storage.createDownloadUrl(9L, "archive/7/key", Duration.ofMinutes(10), true))
                .thenReturn("/api/v1/files/9/content?preview=true");

        assertThat(service.signedUrl(9, true).url()).startsWith("/api/v1/files/9/content?preview=true&expires=");
    }

    @Test
    void uploadTicketChecksBytesBeforeWritingAndOnlyOwnerCanUseIt() throws Exception {
        byte[] data = "archive material".getBytes(StandardCharsets.UTF_8);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        AtomicReference<FileObjectEntity> created = new AtomicReference<>();
        when(security.requireCurrentUser()).thenReturn(user(7, "INTERNAL_TOPIC_UNIT"));
        when(files.insert(any(FileObjectEntity.class))).thenAnswer(invocation -> {
            FileObjectEntity entity = invocation.getArgument(0);
            entity.setId(9L); created.set(entity); return 1;
        });
        when(storage.createUploadUrl(anyLong(), anyString(), any())).thenReturn("/api/v1/files/9/content");
        var ticket = service.createTicket(new UploadTicketRequest("proof.pdf", data.length, "application/pdf", hash, "ARCHIVE"));
        when(files.selectById(9L)).thenReturn(created.get());
        String query = URI.create(ticket.uploadUrl()).getRawQuery();
        long expires = Long.parseLong(query.split("&")[0].substring("expires=".length()));
        String signature = URLDecoder.decode(query.split("&")[1].substring("signature=".length()), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.uploadContent(9L, expires, signature, "bad".getBytes(StandardCharsets.UTF_8), "application/pdf"))
                .isInstanceOf(BusinessException.class).extracting(ex -> ((BusinessException) ex).code()).isEqualTo("FILE_SIZE_MISMATCH");
        service.uploadContent(9L, expires, signature, data, "application/pdf");
        verify(storage).write(created.get().getObjectKey(), data);
        when(security.requireCurrentUser()).thenReturn(user(8, "EXTERNAL_TOPIC_UNIT"));
        assertThatThrownBy(() -> service.uploadContent(9L, expires, signature, data, "application/pdf"))
                .isInstanceOf(BusinessException.class).extracting(ex -> ((BusinessException) ex).code()).isEqualTo("FILE_OWNER_REQUIRED");
    }

    @Test
    void archivePolicyGrantsReadOnlyForLinkedReadyFile() {
        FileObjectEntity file = pendingFile(7);
        file.setStatus("READY");
        when(files.selectById(9L)).thenReturn(file);
        when(security.requireCurrentUser()).thenReturn(user(8, "INTERNAL_TOPIC_UNIT"));
        when(storage.createDownloadUrl(anyLong(), anyString(), any(), eq(false)))
                .thenReturn("/api/v1/files/9/content?preview=false");
        AppProperties properties = new AppProperties(new AppProperties.Security("test", "test-secret-with-at-least-thirty-two-characters",
                Duration.ofMinutes(15), Duration.ofDays(1), "http://localhost:5173"), null,
                new AppProperties.File("MOCK", Path.of("storage/mock"), Duration.ofMinutes(15), Duration.ofMinutes(10)));
        service = new FileService(files, storage, security, properties, List.of((id, user) -> id == 9L && user.id() == 8L));
        assertThat(service.signedUrl(9, false).url()).contains("signature=");
    }

    private FileObjectEntity pendingFile(long uploaderId) {
        FileObjectEntity file = new FileObjectEntity();
        file.setId(9L); file.setUploaderId(uploaderId); file.setObjectKey("archive/7/key"); file.setStatus("PENDING");
        file.setStorageProvider("MOCK");
        file.setOriginalName("material.pdf"); file.setContentType("application/pdf"); file.setSizeBytes(128L);
        return file;
    }

    private CurrentUser user(long id, String role) {
        return new CurrentUser(id, "user", role.endsWith("TOPIC_UNIT") ? id : null, role, Set.of(), List.of(), 0);
    }
}
