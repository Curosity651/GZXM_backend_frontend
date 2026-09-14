package com.gzxm.server.modules.auth.application;

public interface RefreshTokenStore {
    String create(long userId, int tokenVersion);
    RefreshSession consume(String token);
    void revoke(String token);

    record RefreshSession(long userId, int tokenVersion) {}
}
