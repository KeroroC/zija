package com.zija.household.internal;

import com.zija.Utf8ByteLength;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import com.zija.identity.IdentityApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 家庭所有者密码恢复控制器。
 *
 * <p>提供通过恢复令牌重置 Owner 密码的 REST API 端点。</p>
 *
 * <ul>
 *   <li>{@code POST /api/v1/owner-recovery/inspect} — 检查恢复令牌有效性</li>
 *   <li>{@code POST /api/v1/owner-recovery/reset-password} — 使用恢复令牌重置密码</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/owner-recovery")
class OwnerRecoveryController {

    private final OwnerRecoveryService recoveryService;
    private final ZijaSessionAuthenticationSupport sessionAuth;
    private final IdentityApi identityApi;

    OwnerRecoveryController(
            OwnerRecoveryService recoveryService,
            ZijaSessionAuthenticationSupport sessionAuth,
            IdentityApi identityApi
    ) {
        this.recoveryService = recoveryService;
        this.sessionAuth = sessionAuth;
        this.identityApi = identityApi;
    }

    public record InspectRequest(@NotBlank @Size(max = 200) String token) {
    }

    public record InspectResponse(boolean valid, String ownerDisplayName) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(min = 8, max = 72) @Utf8ByteLength(max = 72) String newPassword) {
    }

    /**
     * 检查恢复令牌是否有效。
     *
     * @param request 检查请求（令牌）
     * @return 令牌有效性及 Owner 显示名称
     */
    @PostMapping("/inspect")
    InspectResponse inspect(@Valid @RequestBody InspectRequest request) {
        Optional<OwnerRecoveryTokenEntity> token = recoveryService.inspect(request.token());
        String ownerDisplayName = token
                .flatMap(t -> identityApi.findById(t.getAccountId()))
                .map(IdentityApi.AccountInfo::displayName)
                .orElse(null);
        return new InspectResponse(token.isPresent(), ownerDisplayName);
    }

    /**
     * 使用恢复令牌重置 Owner 密码，并刷新 CSRF 令牌。
     *
     * @param request      重置密码请求（令牌和新密码）
     * @param httpRequest  HTTP 请求
     * @param httpResponse HTTP 响应
     */
    @PostMapping("/reset-password")
    void resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        recoveryService.resetPassword(request.token(), request.newPassword());
        sessionAuth.regenerateCsrfToken(httpRequest, httpResponse);
    }
}
