package com.zija;

import com.zija.shared.ZijaErrorCodes;
import com.zija.shared.ZijaProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 未被任何异常处理器认领的异常兜底。
 * <p>
 * 排在 Spring MVC 自带解析器（{@code @ExceptionHandler}、{@code @ResponseStatus}、
 * 标准 4xx 映射）之后，只接手它们都放弃的异常：记录 ERROR（含堆栈与 MDC 中的 requestId），
 * 并返回不暴露内部细节的 {@code INTERNAL_ERROR} Problem Details，用户可凭 {@code requestId} 反馈。
 * <p>
 * 安全异常不接手，交还给 Spring Security 的 ExceptionTranslationFilter 映射为 401/403。
 */
@Component
public class ZijaFallbackExceptionHandler implements HandlerExceptionResolver, Ordered {

    private static final Logger log = LoggerFactory.getLogger(ZijaFallbackExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public ZijaFallbackExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public @Nullable ModelAndView resolveException(@NonNull HttpServletRequest request,
                                                   @NonNull HttpServletResponse response,
                                                   @Nullable Object handler,
                                                   @NonNull Exception exception) {
        if (exception instanceof AccessDeniedException || exception instanceof AuthenticationException) {
            return null;
        }
        log.error("未处理异常 {} {}", request.getMethod(), request.getRequestURI(), exception);
        if (response.isCommitted()) {
            return new ModelAndView();
        }
        var problem = ZijaProblems.of(request, HttpStatus.INTERNAL_SERVER_ERROR,
                "服务器内部错误", "服务器内部错误，请携带 requestId 反馈",
                ZijaErrorCodes.INTERNAL_ERROR);
        try {
            response.resetBuffer();
            response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write(objectMapper.writeValueAsString(problem));
        } catch (IOException | IllegalStateException writeFailure) {
            log.warn("写入 500 响应失败", writeFailure);
        }
        return new ModelAndView();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
