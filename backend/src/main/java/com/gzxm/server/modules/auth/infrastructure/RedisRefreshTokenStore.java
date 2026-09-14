package com.gzxm.server.modules.auth.infrastructure;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.auth.application.RefreshTokenStore;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class RedisRefreshTokenStore implements RefreshTokenStore {
    private static final String PREFIX = "gzxm:refresh:";
    private final StringRedisTemplate redis;
    private final AppProperties properties;

    public RedisRefreshTokenStore(StringRedisTemplate redis, AppProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public String create(long userId, int tokenVersion) {
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        redis.opsForValue().set(key(token), userId + ":" + tokenVersion, properties.security().refreshTokenTtl());
        return token;
    }

    @Override
    public RefreshSession consume(String token) {
        if (token == null || token.isBlank()) throw invalid();
        String key = key(token);
        String value = redis.opsForValue().getAndDelete(key);
        if (value == null) throw invalid();
        String[] parts = value.split(":", 2);
        return new RefreshSession(Long.parseLong(parts[0]), Integer.parseInt(parts[1]));
    }

    @Override
    public void revoke(String token) {
        if (token != null && !token.isBlank()) redis.delete(key(token));
    }

    private String key(String token) { return PREFIX + sha256(token); }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private BusinessException invalid() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "登录状态已失效，请重新登录");
    }
}
