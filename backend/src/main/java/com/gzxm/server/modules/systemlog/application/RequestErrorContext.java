package com.gzxm.server.modules.systemlog.application;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestErrorContext {
    public static final String CODE = RequestErrorContext.class.getName() + ".code";
    public static final String MESSAGE = RequestErrorContext.class.getName() + ".message";
    public static final String ERROR = RequestErrorContext.class.getName() + ".error";
    public static final String TRACE_ID = RequestErrorContext.class.getName() + ".traceId";
    private RequestErrorContext() {}
    public static void mark(HttpServletRequest request, String code, String message, Throwable error) {
        request.setAttribute(CODE, code);
        request.setAttribute(MESSAGE, message);
        if (error != null) request.setAttribute(ERROR, error);
    }
}
