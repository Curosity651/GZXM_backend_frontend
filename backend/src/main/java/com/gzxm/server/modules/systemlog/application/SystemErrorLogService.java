package com.gzxm.server.modules.systemlog.application;

import com.gzxm.server.common.api.PageResult;
import com.gzxm.server.common.security.CurrentUser;
import com.gzxm.server.modules.systemlog.repository.SystemErrorLogMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

@Service
public class SystemErrorLogService {
    private final SystemErrorLogMapper mapper;
    public SystemErrorLogService(SystemErrorLogMapper mapper) { this.mapper = mapper; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String traceId, String method, String path, int status, String code, String message,
                       Throwable error, long durationMs, String clientIp, String userAgent) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser user = authentication != null && authentication.getPrincipal() instanceof CurrentUser current ? current : null;
        mapper.insert(limit(traceId, 64), status >= 500 ? "ERROR" : "WARN", user == null ? null : user.id(),
                user == null ? null : limit(user.username(), 100), limit(method, 12), limit(path, 500), status,
                limit(code, 100), sanitize(message, 1000), error == null ? null : limit(error.getClass().getName(), 255),
                stack(error), Math.max(durationMs, 0), limit(clientIp, 64), sanitize(userAgent, 500));
    }

    @Transactional(readOnly = true)
    public PageResult<SystemErrorLogMapper.Row> list(long page, long size, String severity, Integer status,
                                                      String username, String traceId,
                                                      LocalDateTime from, LocalDateTime to) {
        if (page < 1 || size < 1 || size > 200) throw new IllegalArgumentException("分页参数不正确");
        long offset = (page - 1) * size;
        return PageResult.of(mapper.list(severity, status, username, traceId, from, to, offset, size),
                page, size, mapper.count(severity, status, username, traceId, from, to));
    }

    private String stack(Throwable error) {
        if (error == null) return null;
        StringWriter writer = new StringWriter();
        error.printStackTrace(new PrintWriter(writer));
        return sanitize(writer.toString(), 8000);
    }
    private String sanitize(String value, int max) {
        if (value == null) return null;
        String cleaned = value
                .replaceAll("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+", "Bearer [REDACTED]")
                .replaceAll("(?i)(password|token|secret)(\\s*[=:]\\s*)[^\\s,;]+", "$1$2[REDACTED]");
        return limit(cleaned, max);
    }
    private String limit(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }
}
