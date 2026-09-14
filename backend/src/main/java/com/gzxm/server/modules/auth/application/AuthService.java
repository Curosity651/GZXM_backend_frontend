package com.gzxm.server.modules.auth.application;

import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.modules.auth.api.AuthDtos.TokenResponse;
import com.gzxm.server.modules.system.domain.UserEntity;
import com.gzxm.server.modules.system.repository.SystemRelationMapper;
import com.gzxm.server.modules.system.repository.UserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.gzxm.server.modules.auth.api.AuthDtos.CurrentUserView;

@Service
public class AuthService {
    private final UserMapper users;
    private final SystemRelationMapper relations;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final RefreshTokenStore refreshTokens;

    public AuthService(UserMapper users, SystemRelationMapper relations, PasswordEncoder encoder,
                       JwtService jwt, RefreshTokenStore refreshTokens) {
        this.users = users;
        this.relations = relations;
        this.encoder = encoder;
        this.jwt = jwt;
        this.refreshTokens = refreshTokens;
    }

    public LoginResult login(String username, String password) {
        UserEntity user = users.findForAuthentication(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled()) || !encoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
        user.setLastLoginAt(LocalDateTime.now());
        users.updateById(user);
        CurrentUser current = loadCurrentUser(user.getId());
        return issue(current);
    }

    public LoginResult refresh(String refreshToken) {
        RefreshTokenStore.RefreshSession session = refreshTokens.consume(refreshToken);
        CurrentUser current = loadCurrentUser(session.userId());
        if (current.tokenVersion() != session.tokenVersion()) throw unauthorized("TOKEN_REVOKED", "登录状态已失效，请重新登录");
        return issue(current);
    }

    public void logout(String refreshToken) { refreshTokens.revoke(refreshToken); }

    public CurrentUser loadCurrentUser(long userId) {
        UserEntity user = users.selectById(userId);
        if (user == null || user.getDeletedAt() != null || !Boolean.TRUE.equals(user.getEnabled())) {
            throw unauthorized("ACCOUNT_DISABLED", "账号不存在或已停用");
        }
        String roleCode = relations.findRoleCode(userId);
        if (roleCode == null) throw unauthorized("ROLE_UNAVAILABLE", "账号角色不存在或已停用");
        Set<String> authorities = new HashSet<>(relations.findPermissionCodes(userId));
        authorities.add("ROLE_" + roleCode);
        List<CurrentUser.TopicMembership> memberships = user.getUnitId() == null ? List.of() : relations.findMemberships(user.getUnitId());
        return new CurrentUser(user.getId(), user.getUsername(), user.getUnitId(), roleCode,
                Set.copyOf(authorities), List.copyOf(memberships), user.getTokenVersion());
    }

    private LoginResult issue(CurrentUser current) {
        String accessToken = jwt.createAccessToken(current);
        String refreshToken = refreshTokens.create(current.id(), current.tokenVersion());
        return new LoginResult(new TokenResponse(accessToken, "Bearer", jwt.accessTokenExpiresInSeconds(), CurrentUserView.from(current)), refreshToken);
    }

    private BusinessException unauthorized(String code, String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public record LoginResult(TokenResponse response, String refreshToken) {}
}
