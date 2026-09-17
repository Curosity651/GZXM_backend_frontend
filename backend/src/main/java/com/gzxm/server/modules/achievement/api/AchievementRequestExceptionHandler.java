package com.gzxm.server.modules.achievement.api;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes={AchievementController.class,AchievementProgressController.class})
public class AchievementRequestExceptionHandler {
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ProblemDetail malformed(Exception ex){var result=ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY,"成果请求格式或参数不正确");result.setProperty("code","INVALID_ACHIEVEMENT_REQUEST");return result;}
}
