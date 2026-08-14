package com.zija;

import com.zija.shared.ZijaProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Spring Security 异常处理器。
 * <p>
 * 将认证失败（{@link AuthenticationException}）和授权拒绝（{@link AccessDeniedException}）
 * 转换为 RFC 7807 Problem Details JSON 响应，包括 CSRF 令牌无效的场景。
 * 与 {@link ZijaValidationExceptionHandler} 共同构成统一的错误响应体系。
 */
@Component
public class ZijaProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ZijaProblemHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 未认证请求访问受保护资源时返回 401 Problem Details 响应。 */
    @Override
    public void commence(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                         @NonNull AuthenticationException ex) throws IOException {
        writeProblem(request, response, HttpStatus.UNAUTHORIZED,
                "需要认证", "AUTHENTICATION_REQUIRED");
    }

    /** 已认证但权限不足时返回 403 Problem Details 响应，CSRF 失败会使用专用错误码。 */
    @Override
    public void handle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                       @NonNull AccessDeniedException ex) throws IOException {
        var errorCode = ex instanceof CsrfException ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED";
        var title = ex instanceof CsrfException ? "CSRF Token 无效" : "权限不足";
        writeProblem(request, response, HttpStatus.FORBIDDEN, title, errorCode);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String title, String errorCode) throws IOException {
        var problem = ZijaProblems.of(request, status, title, errorCode);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Must be set before getWriter(): the Servlet spec defaults the response
        // encoding to ISO-8859-1, which turns every Chinese title/detail into "????".
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
