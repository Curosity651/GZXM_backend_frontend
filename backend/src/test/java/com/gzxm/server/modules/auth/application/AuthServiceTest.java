package com.gzxm.server.modules.auth.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.modules.system.domain.UserEntity;
import com.gzxm.server.modules.system.repository.SystemRelationMapper;
import com.gzxm.server.modules.system.repository.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UserMapper users = mock(UserMapper.class);
    private final SystemRelationMapper relations = mock(SystemRelationMapper.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final RefreshTokenStore refreshTokens = mock(RefreshTokenStore.class);
    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(users, relations, encoder, jwt, refreshTokens);
    }

    @Test
    void issuesTokensForEnabledUserWithValidPassword() {
        UserEntity user = user(true);
        when(users.findForAuthentication("admin")).thenReturn(user);
        when(users.selectById(1L)).thenReturn(user);
        when(encoder.matches("correct", "hash")).thenReturn(true);
        when(relations.findRoleCode(1)).thenReturn("SYSTEM_ADMIN");
        when(relations.findPermissionCodes(1)).thenReturn(List.of("system.manage"));
        when(jwt.createAccessToken(any())).thenReturn("access-token");
        when(jwt.accessTokenExpiresInSeconds()).thenReturn(900L);
        when(refreshTokens.create(1, 0)).thenReturn("refresh-token");

        AuthService.LoginResult result = service.login("admin", "correct");

        assertThat(result.response().accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        verify(users).updateById(user);
    }

    @Test
    void rejectsWrongPasswordWithoutIssuingToken() {
        when(users.findForAuthentication("admin")).thenReturn(user(true));
        when(encoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login("admin", "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("INVALID_CREDENTIALS");
        verifyNoInteractions(jwt, refreshTokens);
    }

    @Test
    void rejectsDisabledAccount() {
        when(users.findForAuthentication("admin")).thenReturn(user(false));

        assertThatThrownBy(() -> service.login("admin", "correct"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("INVALID_CREDENTIALS");
        verifyNoInteractions(jwt, refreshTokens);
    }

    @Test
    void rejectsRefreshSessionAfterTokenVersionChanges() {
        when(refreshTokens.consume("old-refresh")).thenReturn(new RefreshTokenStore.RefreshSession(1, 0));
        UserEntity user = user(true);
        user.setTokenVersion(1);
        when(users.selectById(1L)).thenReturn(user);
        when(relations.findRoleCode(1)).thenReturn("SYSTEM_ADMIN");
        when(relations.findPermissionCodes(1)).thenReturn(List.of("system.manage"));

        assertThatThrownBy(() -> service.refresh("old-refresh"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).code()).isEqualTo("TOKEN_REVOKED");
    }

    private UserEntity user(boolean enabled) {
        UserEntity user = new UserEntity();
        user.setId(1L); user.setUsername("admin"); user.setPasswordHash("hash");
        user.setEnabled(enabled); user.setTokenVersion(0);
        return user;
    }
}
