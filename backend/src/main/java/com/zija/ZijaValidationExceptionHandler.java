package com.zija;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 请求校验异常处理器。
 * <p>
 * 统一捕获并转换以下异常为 RFC 7807 Problem Details 响应：
 * <ul>
 *   <li>{@link MethodArgumentNotValidException} — Bean Validation 校验失败，返回逐字段错误</li>
 *   <li>{@link HttpMessageNotReadableException} — 请求体无法解析（格式错误）</li>
 *   <li>{@link MethodArgumentTypeMismatchException} — 方法参数类型不匹配</li>
 * </ul>
 * 所有响应均携带 {@code errorCode} 和 {@code requestId}，前端可据此定位问题。
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ZijaValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return problem(request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpServletRequest request) {
        return problem(request, Map.of("request", "请求体格式错误"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request
    ) {
        return problem(request, Map.of(exception.getName(), "格式不正确"));
    }

    private ProblemDetail problem(
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "请求字段校验失败");
        problem.setTitle("请求字段校验失败");
        problem.setProperty("errorCode", "VALIDATION_FAILED");
        problem.setProperty("requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE));
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }
}
