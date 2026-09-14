package com.gzxm.server.common.security;

import com.gzxm.server.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.security.core.context.SecurityContextHolder;

@Component
public class SecurityContextFacade {
    public CurrentUser requireCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw BusinessException.forbidden("AUTHENTICATION_REQUIRED", "请先登录");
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof CurrentUser currentUser) return currentUser;
        throw BusinessException.forbidden("AUTHENTICATION_REQUIRED", "请先登录");
    }
}
