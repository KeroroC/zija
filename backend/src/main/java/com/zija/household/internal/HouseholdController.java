package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireOwner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/household")
class HouseholdController {

    private final HouseholdService householdService;
    private final MemberService memberService;
    private final ZijaSessionAuthenticationSupport sessionAuth;
    private final com.zija.identity.IdentityApi identityApi;
    private final com.zija.system.SystemApi systemApi;

    HouseholdController(
            HouseholdService householdService,
            MemberService memberService,
            ZijaSessionAuthenticationSupport sessionAuth,
            com.zija.identity.IdentityApi identityApi,
            com.zija.system.SystemApi systemApi
    ) {
        this.householdService = householdService;
        this.memberService = memberService;
        this.sessionAuth = sessionAuth;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record BootstrapRequest(
            String householdName, String username, String password,
            String displayName, String email
    ) {
    }

    public record HouseholdStatusResponse(boolean initialized) {
    }

    public record CurrentMemberResponse(
            UUID householdId, UUID memberId, UUID accountId,
            String username, String displayName, String role, String status
    ) {
    }

    public record TransferOwnershipRequest(UUID targetMemberId) {
    }

    @GetMapping("/status")
    HouseholdStatusResponse status() {
        return new HouseholdStatusResponse(householdService.isInitialized());
    }

    @PostMapping("/bootstrap")
    HouseholdApi.HouseholdInfo bootstrap(
            @Valid @RequestBody BootstrapRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var household = householdService.bootstrap(new HouseholdService.BootstrapCommand(
                request.householdName(), request.username(), request.password(),
                request.displayName(), request.email()));

        sessionAuth.authenticate(
                request.username().trim().toLowerCase(),
                request.password(), httpRequest, httpResponse);
        sessionAuth.regenerateCsrfToken(httpRequest, httpResponse);

        return household;
    }

    @GetMapping("/me")
    CurrentMemberResponse me() {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var member = householdService.requireActiveMember(principal.getAccountId());
        return new CurrentMemberResponse(
                member.householdId(), member.id(), member.accountId(),
                principal.getUsername(), principal.getDisplayName(),
                member.role().name(), member.status());
    }

    @PostMapping("/transfer-ownership")
    @RequireOwner
    void transferOwnership(@RequestBody TransferOwnershipRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.transferOwnership(principal.getAccountId(), request.targetMemberId());
    }
}
