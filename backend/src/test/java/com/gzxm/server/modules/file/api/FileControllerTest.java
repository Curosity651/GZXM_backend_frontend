package com.gzxm.server.modules.file.api;

import com.gzxm.server.common.audit.AuditService;
import com.gzxm.server.modules.file.application.FileService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileControllerTest {
    private final FileService service = mock(FileService.class);
    private final FileController controller = new FileController(service, mock(AuditService.class));

    @Test
    void unsafeUploadedTypeIsDownloadedEvenWhenPreviewWasRequested() {
        when(service.readContent(7L, 123L, "signature")).thenReturn(content("text/html"));

        var response = controller.readContent(7L, 123L, "signature", true);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void pdfCanBePreviewedInline() {
        when(service.readContent(7L, 123L, "signature")).thenReturn(content("application/pdf"));

        var response = controller.readContent(7L, 123L, "signature", true);

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).startsWith("inline;");
    }

    private FileService.FileContent content(String type) {
        var metadata = new FileDtos.FileView("7", "document", 3, type, null, "READY", "3", LocalDateTime.now());
        return new FileService.FileContent(metadata, new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 }));
    }
}
