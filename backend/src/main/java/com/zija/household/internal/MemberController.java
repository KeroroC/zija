package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import com.zija.household.RequireOwner;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 成员管理控制器。
 *
 * <p>提供家庭成员列表查询、角色变更和状态管理的 REST API 端点。</p>
 *
 * <ul>
 *   <li>{@code GET /api/v1/members} — 获取当前家庭的所有成员列表</li>
 *   <li>{@code PUT /api/v1/members/{id}/role} — 修改成员角色（仅 Owner）</li>
 *   <li>{@code PUT /api/v1/members/{id}/status} — 修改成员状态（仅 Admin 及以上）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/members")
class MemberController {

    private final HouseholdService householdService;
    private final MemberService memberService;
    private final MemberMapper memberMapper;
    private final IdentityApi identityApi;

    MemberController(HouseholdService householdService, MemberService memberService,
                    MemberMapper memberMapper, IdentityApi identityApi) {
        this.householdService = householdService;
        this.memberService = memberService;
        this.memberMapper = memberMapper;
        this.identityApi = identityApi;
    }

    public record MemberResponse(
            UUID id, UUID accountId, String username, String displayName,
            String role, String status) {
    }

    public record UpdateRoleRequest(
            @NotBlank @Pattern(regexp = "ADMIN|MEMBER") String role) {
    }

    public record UpdateStatusRequest(
            @NotBlank @Pattern(regexp = "ACTIVE|DEACTIVATED") String status) {
    }

    /**
     * 获取当前家庭的所有成员列表，包含账号信息（用户名、显示名称等）。
     *
     * @return 成员列表
     */
    @GetMapping
    @RequireMember
    List<MemberResponse> list() {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var member = householdService.requireActiveMember(principal.getAccountId());
        var members = memberMapper.selectByHousehold(member.householdId());
        if (members.isEmpty()) {
            return List.of();
        }

        var accountIds = members.stream().map(MemberEntity::getAccountId).toList();
        var accounts = identityApi.findByIds(accountIds);

        return members.stream()
                .map(m -> {
                    var account = accounts.get(m.getAccountId());
                    return new MemberResponse(
                            m.getId(), m.getAccountId(),
                            account != null ? account.username() : null,
                            account != null ? account.displayName() : null,
                            m.getRole(), m.getStatus());
                })
                .toList();
    }

    /**
     * 修改指定成员的角色（ADMIN 或 MEMBER）。仅 Owner 可执行。
     *
     * @param id      成员 ID
     * @param request 角色变更请求
     */
    @PutMapping("/{id}/role")
    @RequireOwner
    void updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.updateRole(principal.getAccountId(), id, request.role());
    }

    /**
     * 修改指定成员的状态（ACTIVE 或 DEACTIVATED）。仅 Admin 及以上可执行。
     *
     * @param id      成员 ID
     * @param request 状态变更请求
     */
    @PutMapping("/{id}/status")
    @RequireAdmin
    void updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.updateStatus(principal.getAccountId(), id, request.status());
    }
}
