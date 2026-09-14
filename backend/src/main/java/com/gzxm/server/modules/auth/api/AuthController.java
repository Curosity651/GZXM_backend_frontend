package com.gzxm.server.modules.auth.api;

import com.gzxm.server.common.security.SecurityContextFacade;
import com.gzxm.server.config.AppProperties;
import com.gzxm.server.modules.auth.api.AuthDtos.*;
import com.gzxm.server.modules.auth.application.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private static final String REFRESH_COOKIE = "gzxm_refresh_token";
    private final AuthService auth;
    private final SecurityContextFacade security;
    private final AppProperties properties;

    public AuthController(AuthService auth, SecurityContextFacade security, AppProperties properties) {
        this.auth = auth;
        this.security = security;
        this.properties = properties;
    }

    @PostMapping("/login")
    TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.LoginResult result = auth.login(request.username(), request.password());
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/refresh")
    TokenResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String token, HttpServletResponse response) {
        AuthService.LoginResult result = auth.refresh(token);
        setRefreshCookie(response, result.refreshToken());
        return result.response();
    }

    @PostMapping("/logout")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String token, HttpServletResponse response) {
        auth.logout(token);
        response.addHeader(HttpHeaders.SET_COOKIE, REFRESH_COOKIE + "=; Path=/api/v1/auth; HttpOnly; SameSite=Lax; Max-Age=0");
    }

    @GetMapping("/me")
    CurrentUserView me() { return CurrentUserView.from(security.requireCurrentUser()); }

    private void setRefreshCookie(HttpServletResponse response, String token) {
        long maxAge = properties.security().refreshTokenTtl().toSeconds();
        response.addHeader(HttpHeaders.SET_COOKIE, REFRESH_COOKIE + "=" + token
                + "; Path=/api/v1/auth; HttpOnly; SameSite=Lax; Max-Age=" + maxAge);
    }
}
