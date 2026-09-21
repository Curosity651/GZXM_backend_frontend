package com.gzxm.server.modules.systemlog.application;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(-90)
public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestTraceFilter.class);
    private final SystemErrorLogService logs;
    public RequestTraceFilter(SystemErrorLogService logs) { this.logs = logs; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String supplied = request.getHeader("X-Request-Id");
        String traceId = supplied != null && supplied.matches("[A-Za-z0-9._-]{1,64}") ? supplied : UUID.randomUUID().toString();
        long started = System.nanoTime();
        request.setAttribute(RequestErrorContext.TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);
        MDC.put("traceId", traceId);
        Throwable escaped = null;
        try { chain.doFilter(request, response); }
        catch (IOException | ServletException | RuntimeException ex) { escaped = ex; throw ex; }
        finally {
            int status = response.getStatus();
            if (escaped != null && status < 400) status = 500;
            if (status >= 400) {
                try {
                    Throwable marked = request.getAttribute(RequestErrorContext.ERROR) instanceof Throwable value ? value : escaped;
                    logs.record(traceId, request.getMethod(), request.getRequestURI(), status,
                            string(request.getAttribute(RequestErrorContext.CODE)),
                            string(request.getAttribute(RequestErrorContext.MESSAGE)), marked,
                            (System.nanoTime() - started) / 1_000_000,
                            clientIp(request), request.getHeader("User-Agent"));
                } catch (Exception logFailure) {
                    log.error("Failed to persist request error log traceId={}", traceId, logFailure);
                }
            }
            MDC.remove("traceId");
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank() ? request.getRemoteAddr() : forwarded.split(",", 2)[0].trim();
    }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
}
