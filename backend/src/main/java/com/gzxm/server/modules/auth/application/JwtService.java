package com.gzxm.server.modules.auth.application;

import com.gzxm.server.config.AppProperties;
import com.gzxm.server.common.security.CurrentUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties.Security properties;
    private final SecretKey key;

    public JwtService(AppProperties appProperties) {
        this.properties = appProperties.security();
        this.key = Keys.hmacShaKeyFor(properties.jwtSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(CurrentUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.id()))
                .claim("username", user.username())
                .claim("unitId", user.unitId())
                .claim("role", user.roleCode())
                .claim("tokenVersion", user.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public TokenClaims parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build()
                .parseSignedClaims(token).getPayload();
        return new TokenClaims(Long.parseLong(claims.getSubject()), claims.get("tokenVersion", Integer.class));
    }

    public long accessTokenExpiresInSeconds() { return properties.accessTokenTtl().toSeconds(); }

    public record TokenClaims(long userId, int tokenVersion) {}
}
