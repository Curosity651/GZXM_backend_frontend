package com.gzxm.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Security security, Bootstrap bootstrap, File file) {
    public record Security(String issuer, String jwtSecret, Duration accessTokenTtl,
                           Duration refreshTokenTtl, String allowedOrigins) {}

    public record Bootstrap(boolean enabled, String adminUsername, String adminPassword) {}

    public record File(String provider, Path root, Duration uploadTicketTtl,
                       Duration signedUrlTtl, Webdav webdav, String signingSecret) {
        public record Webdav(String baseUrl, String username, String password,
                             Duration connectTimeout, Duration requestTimeout) {}
    }
}
