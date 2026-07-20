package com.zija.identity.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.auth.ChangePasswordRequest;
import com.zija.identity.internal.auth.LoginRequest;
import com.zija.identity.internal.auth.SessionInfo;
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

    @PostMapping("/login")
    SessionInfo login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var normalized = request.username().trim().toLowerCase(Locale.ROOT);
        var ip = resolveClientIp(httpRequest);

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
            try {
                rateLimiter.recordFailure(normalized, ip);
            } catch (LoginRateLimitedException rateEx) {
                throw rateEx;
            }
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGIN_FAILURE", "FAILURE", null,
                    null, null,
                    (String) httpRequest.getAttribute("zija.request-id"),
                    ip, Map.of("username", normalized)
            ));
            throw new InvalidCredentialsException();
        }
    }

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

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        );
    }

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
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
