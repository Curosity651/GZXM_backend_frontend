package com.gzxm.server.modules.file.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

public final class FileDtos {
    private FileDtos() {}

    public record UploadTicketRequest(@NotBlank String fileName, @Positive long size,
                                      @NotBlank String contentType, String sha256,
                                      @NotBlank String businessType) {}
    public record UploadTicket(String fileId, String uploadUrl, String method,
                               Map<String, String> headers, Instant expiresAt) {}
    public record FileView(String id, String originalName, long size, String contentType,
                           String sha256, String status, String uploaderId, LocalDateTime createdAt) {}
    public record SignedUrl(String url, Instant expiresAt) {}
}
