package com.gzxm.server.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import com.gzxm.server.modules.systemlog.application.RequestErrorContext;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        RequestErrorContext.mark(request, ex.code(), ex.getMessage(), ex.status().is5xxServerError() ? ex : null);
        return problem(ex.status(), ex.code(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        RequestErrorContext.mark(request, "VALIDATION_FAILED", "请求字段校验失败", null);
        ProblemDetail detail = problem(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "请求字段校验失败", request);
        List<FieldErrorItem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorItem(error.getField(), error.getDefaultMessage()))
                .toList();
        detail.setProperty("fieldErrors", errors);
        return detail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleForbidden(AccessDeniedException ex, HttpServletRequest request) {
        RequestErrorContext.mark(request, "ACCESS_DENIED", "没有执行该操作的权限", null);
        return problem(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有执行该操作的权限", request);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled request error", ex);
        RequestErrorContext.mark(request, "INTERNAL_ERROR", "服务器处理请求失败", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务器处理请求失败", request);
    }

    private ProblemDetail problem(HttpStatus status, String code, String message, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setType(URI.create("https://gzxm.local/problems/" + code.toLowerCase()));
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        Object traceId = request.getAttribute(RequestErrorContext.TRACE_ID);
        if (traceId != null) detail.setProperty("traceId", traceId);
        return detail;
    }

    private record FieldErrorItem(String field, String message) {}
}
