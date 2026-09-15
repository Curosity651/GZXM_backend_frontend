package com.gzxm.server.modules.topic.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.net.URI;

/** Keep malformed-input handling scoped to Topics; shared error handling remains untouched. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = TopicController.class)
public class TopicRequestExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail malformed(Exception exception, HttpServletRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "请求格式、日期或参数类型不正确");
        problem.setTitle("Unprocessable Entity");
        problem.setType(URI.create("https://gzxm.local/problems/invalid_topic_request"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "INVALID_TOPIC_REQUEST");
        return problem;
    }
}
