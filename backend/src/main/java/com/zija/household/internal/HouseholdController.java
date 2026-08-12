package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.Utf8ByteLength;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireOwner;
import com.zija.identity.SessionInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 家庭管理控制器。
 *
 * <p>提供家庭初始化、状态查询、当前成员信息获取及所有权转让的 REST API 端点。</p>
 *
 * <ul>
 *   <li>{@code GET /api/v1/household/status} — 查询家庭是否已初始化</li>
 *   <li>{@code POST /api/v1/household/bootstrap} — 初始化家庭并创建 Owner 账号</li>
 *   <li>{@code GET /api/v1/household/me} — 获取当前登录成员信息</li>
 *   <li>{@code POST /api/v1/household/transfer-ownership} — 转让家庭所有权（仅 Owner）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/household")
class HouseholdController {

    private final HouseholdService householdService;
    private final MemberService memberService;
    private final ZijaSessionAuthenticationSupport sessionAuth;

    HouseholdController(
            HouseholdService householdService,
            MemberService memberService,
            ZijaSessionAuthenticationSupport sessionAuth
    ) {
        this.householdService = householdService;
        this.memberService = memberService;
        this.sessionAuth = sessionAuth;
    }

    public record BootstrapRequest(
            @NotBlank(message = "不能为空") @Size(max = 100, message = "长度不能超过 {max} 个字符") String householdName,
            @NotBlank(message = "不能为空") @Size(max = 50, message = "长度不能超过 {max} 个字符") String username,
            @NotBlank(message = "不能为空") @Size(min = 8, max = 72, message = "长度必须在 {min} 到 {max} 个字符之间") @Utf8ByteLength(max = 72) String password,
            @NotBlank(message = "不能为空") @Size(max = 100, message = "长度不能超过 {max} 个字符") String displayName,
            @Email(message = "格式不正确") @Size(max = 255, message = "长度不能超过 {max} 个字符") String email
    ) {
    }

    public record HouseholdStatusResponse(boolean initialized) {
    }

    public record CurrentMemberResponse(
            UUID householdId, UUID memberId, UUID accountId,
            String username, String displayName, String role, String status,
            String householdName
    ) {
    }

    public record TransferOwnershipRequest(@NotNull UUID targetMemberId) {
    }

    /**
     * 查询家庭是否已完成初始化。
     *
     * @return 家庭状态响应
     */
    @GetMapping("/status")
    HouseholdStatusResponse status() {
        return new HouseholdStatusResponse(householdService.isInitialized());
    }

    /**
     * 初始化家庭。创建家庭并注册 Owner 账号，完成后自动登录。
     *
     * @param request      初始化请求（家庭名称、用户名、密码等）
     * @param httpRequest  HTTP 请求
     * @param httpResponse HTTP 响应
     * @return 登录后的会话信息
     */
    @PostMapping("/bootstrap")
    @ResponseStatus(HttpStatus.CREATED)
    SessionInfo bootstrap(
            @Valid @RequestBody BootstrapRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        householdService.bootstrap(new HouseholdService.BootstrapCommand(
                request.householdName(), request.username(), request.password(),
                request.displayName(), request.email()));

        var authentication = sessionAuth.authenticate(
                request.username().trim().toLowerCase(),
                request.password(), httpRequest, httpResponse);
        var principal = ZijaSessionAuthenticationSupport.requirePrincipal(authentication);

        return new SessionInfo(true, principal.getAccountId(),
                principal.getUsername(), principal.getDisplayName());
    }

    /**
     * 获取当前登录用户的成员信息，包括家庭 ID、角色和状态。
     *
     * @return 当前成员信息
     */
    @GetMapping("/me")
    CurrentMemberResponse me(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdService.requireActiveMember(principal.getAccountId());
        String householdName = householdService.findHousehold()
                .map(com.zija.household.HouseholdApi.HouseholdInfo::name)
                .orElse(null);
        return new CurrentMemberResponse(
                member.householdId(), member.id(), member.accountId(),
                principal.getUsername(), principal.getDisplayName(),
                member.role().name(), member.status(), householdName);
    }

    /**
     * 转让家庭所有权给指定成员。仅当前 Owner 可执行此操作。
     *
     * @param request 转让请求（目标成员 ID）
     */
    @PostMapping("/transfer-ownership")
    @RequireOwner
    void transferOwnership(@AuthenticationPrincipal ZijaPrincipal principal,
                           @Valid @RequestBody TransferOwnershipRequest request) {
        memberService.transferOwnership(principal.getAccountId(), request.targetMemberId());
    }
}
