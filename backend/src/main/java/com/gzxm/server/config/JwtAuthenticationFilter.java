package com.gzxm.server.config;

import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.exception.BusinessException;
import com.gzxm.server.modules.auth.application.AuthService;
import com.gzxm.server.modules.auth.application.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final AuthService auth;

    public JwtAuthenticationFilter(JwtService jwt, AuthService auth) {
        this.jwt = jwt;
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.TokenClaims claims = jwt.parse(header.substring(7));
                CurrentUser user = auth.loadCurrentUser(claims.userId());
                if (user.tokenVersion() == claims.tokenVersion()) {
                    var authorities = user.authorities().stream().map(SimpleGrantedAuthority::new).toList();
                    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, authorities));
                }
            } catch (JwtException | IllegalArgumentException | BusinessException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
