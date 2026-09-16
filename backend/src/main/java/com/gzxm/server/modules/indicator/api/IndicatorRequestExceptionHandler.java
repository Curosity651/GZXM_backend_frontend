package com.gzxm.server.modules.indicator.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={TopicIndicatorController.class,IndicatorCatalogController.class,UnitAllocationController.class})
public class IndicatorRequestExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class,MissingServletRequestParameterException.class})
    public ProblemDetail malformed(Exception ex,HttpServletRequest request) {
        var problem=ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,"请求格式或参数不正确");
        problem.setProperty("code","INVALID_INDICATOR_REQUEST");
        problem.setInstance(java.net.URI.create(request.getRequestURI()));
        return problem;
    }
}
