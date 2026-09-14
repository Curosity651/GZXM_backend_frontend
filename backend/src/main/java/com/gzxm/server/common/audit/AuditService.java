package com.gzxm.server.common.audit;

import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.common.security.SecurityContextFacade;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final AuditMapper mapper;
    private final SecurityContextFacade security;
    private final HttpServletRequest request;

    public AuditService(AuditMapper mapper, SecurityContextFacade security, HttpServletRequest request) {
        this.mapper = mapper;
        this.security = security;
        this.request = request;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void success(String action, String resourceType, String resourceId) {
        CurrentUser user = security.requireCurrentUser();
        mapper.insert(user.id(), user.username(), action, resourceType, resourceId,
                request.getHeader("X-Request-Id"), request.getRemoteAddr(), true, "{}");
    }
}
