package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import com.zija.household.RequireOwner;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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

    public record UpdateRoleRequest(String role) {
    }

    public record UpdateStatusRequest(String status) {
    }

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

    @PutMapping("/{id}/role")
    @RequireOwner
    void updateRole(@PathVariable UUID id, @RequestBody UpdateRoleRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.updateRole(principal.getAccountId(), id, request.role());
    }

    @PutMapping("/{id}/status")
    @RequireAdmin
    void updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.updateStatus(principal.getAccountId(), id, request.status());
    }
}
