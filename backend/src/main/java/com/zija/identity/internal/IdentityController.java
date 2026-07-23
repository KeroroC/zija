package com.zija.identity.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.identity.IdentityApi;
import com.zija.identity.SessionInfo;
import com.zija.identity.internal.auth.ChangePasswordRequest;
import com.zija.identity.internal.auth.LoginRequest;
import com.zija.system.SystemApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

/**
 * 身份认证控制器。
 *
 * <p>提供用户登录、登出、会话管理、CSRF 令牌获取及密码修改的 REST API 端点。</p>
 *
 * <ul>
 *   <li>{@code POST /api/v1/auth/login} — 用户登录</li>
 *   <li>{@code POST /api/v1/auth/logout} — 用户登出</li>
 *   <li>{@code GET /api/v1/auth/session} — 获取当前会话信息</li>
 *   <li>{@code GET /api/v1/auth/csrf} — 获取 CSRF 令牌</li>
 *   <li>{@code PUT /api/v1/auth/password} — 修改当前用户密码</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
class IdentityController {

    private final IdentityService identityService;
    private final LoginRateLimiter rateLimiter;
    private final ZijaSessionAuthenticationSupport sessionAuth;
    private final SystemApi systemApi;

    IdentityController(
            IdentityService identityService,
            LoginRateLimiter rateLimiter,
            ZijaSessionAuthenticationSupport sessionAuth,
            SystemApi systemApi
    ) {
        this.identityService = identityService;
        this.rateLimiter = rateLimiter;
        this.sessionAuth = sessionAuth;
        this.systemApi = systemApi;
    }

    /**
     * 用户登录。支持登录频率限制，并记录审计日志。
     *
     * @param request    登录请求（用户名和密码）
     * @param httpRequest  HTTP 请求，用于获取客户端 IP
     * @param httpResponse HTTP 响应，用于写入会话
     * @return 登录成功后的会话信息
     */
    @PostMapping("/login")
    SessionInfo login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var normalized = request.username().trim().toLowerCase(Locale.ROOT);
        var ip = resolveClientIp(httpRequest);
        rateLimiter.checkAllowed(normalized, ip);

        try {
            var authentication = sessionAuth.authenticate(
                    normalized, request.password(), httpRequest, httpResponse);
            rateLimiter.recordSuccess(normalized);
            var principal = (ZijaPrincipal) authentication.getPrincipal();
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGIN_SUCCESS", "SUCCESS", null,
                    principal.getAccountId(), null,
                    (String) httpRequest.getAttribute("zija.request-id"),
                    ip, Map.of("username", principal.getUsername())
            ));
            return new SessionInfo(true, principal.getAccountId(),
                    principal.getUsername(), principal.getDisplayName());
        } catch (AuthenticationException ex) {
            LoginRateLimitedException rateLimit = null;
            try {
                rateLimiter.recordFailure(normalized, ip);
            } catch (LoginRateLimitedException rateEx) {
                rateLimit = rateEx;
            }
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGIN_FAILURE", "FAILURE", null,
                    null, null,
                    (String) httpRequest.getAttribute("zija.request-id"),
                    ip, Map.of("username", normalized)
            ));
            if (rateLimit != null) {
                throw rateLimit;
            }
            throw new InvalidCredentialsException();
        }
    }

    /**
     * 用户登出。销毁当前会话并记录审计日志。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     */
    @PostMapping("/logout")
    void logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var accountId = authentication != null && authentication.getPrincipal() instanceof ZijaPrincipal p
                ? p.getAccountId() : null;
        request.getSession().invalidate();
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        if (accountId != null) {
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGOUT", "SUCCESS", null, accountId, null,
                    (String) request.getAttribute("zija.request-id"),
                    resolveClientIp(request), null
            ));
        }
    }

    /**
     * 获取当前会话信息。未认证时返回匿名会话。
     *
     * @return 会话信息
     */
    @GetMapping("/session")
    SessionInfo session() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ZijaPrincipal principal)) {
            return SessionInfo.anonymous();
        }
        return new SessionInfo(true, principal.getAccountId(),
                principal.getUsername(), principal.getDisplayName());
    }

    /**
     * 获取 CSRF 令牌信息，包括令牌值、请求头名称和参数名称。
     *
     * @param csrfToken CSRF 令牌对象
     * @return 包含令牌详情的 Map
     */
    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        );
    }

    /**
     * 修改当前登录用户的密码。操作成功后记录审计日志。
     *
     * @param request     修改密码请求（当前密码和新密码）
     * @param httpRequest HTTP 请求
     */
    @PutMapping("/password")
    void changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var principal = (ZijaPrincipal) authentication.getPrincipal();
        identityService.changePassword(principal.getAccountId(),
                new IdentityApi.ChangePasswordCommand(
                        request.currentPassword(), request.newPassword()));
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "PASSWORD_CHANGED", "SUCCESS", null,
                principal.getAccountId(), principal.getAccountId(),
                (String) httpRequest.getAttribute("zija.request-id"),
                resolveClientIp(httpRequest), null
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
