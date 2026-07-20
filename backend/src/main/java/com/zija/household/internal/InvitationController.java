package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireAdmin;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.identity.IdentityApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
class InvitationController {

    private final InvitationService invitationService;
    private final HouseholdService householdService;
    private final IdentityApi identityApi;
    private final MemberService memberService;
    private final ZijaSessionAuthenticationSupport sessionAuth;

    InvitationController(
            InvitationService invitationService,
            HouseholdService householdService,
            IdentityApi identityApi,
            MemberService memberService,
            ZijaSessionAuthenticationSupport sessionAuth
    ) {
        this.invitationService = invitationService;
        this.householdService = householdService;
        this.identityApi = identityApi;
        this.memberService = memberService;
        this.sessionAuth = sessionAuth;
    }

    public record CreateInvitationRequest(String role, int expiresInHours) {
    }

    public record InspectRequest(@NotBlank String token) {
    }

    public record InvitationInfoResponse(
            UUID id, String token, String role,
            OffsetDateTime expiresAt, String path) {
    }

    public record InspectResponse(
            String householdName, String role, OffsetDateTime expiresAt, boolean valid) {
    }

    public record RedeemRequest(
            String token, String username, String password,
            String displayName, String email) {
    }

    @PostMapping
    @RequireAdmin
    InvitationInfoResponse create(@Valid @RequestBody CreateInvitationRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var member = householdService.requireActiveMember(principal.getAccountId());
        var role = HouseholdApi.MemberRole.valueOf(request.role());

        var result = invitationService.create(
                member.householdId(), principal.getAccountId(), role, request.expiresInHours());
        return new InvitationInfoResponse(result.id(), result.rawToken(),
                result.role().name(), result.expiresAt(),
                "/invitation/redeem#token=" + result.rawToken());
    }

    @PostMapping("/inspect")
    InspectResponse inspect(@Valid @RequestBody InspectRequest request) {
        var invitation = invitationService.inspect(request.token());
        if (invitation.isEmpty()) {
            return new InspectResponse(null, null, null, false);
        }
        var household = householdService.findHousehold().orElseThrow();
        var entity = invitation.get();
        return new InspectResponse(household.name(), entity.getRole(),
                entity.getExpiresAt(), true);
    }

    @PostMapping("/redeem")
    void redeem(@Valid @RequestBody RedeemRequest request,
                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        invitationService.redeem(request.token(),
                new InvitationService.RedeemCommand(
                        request.username(), request.password(),
                        request.displayName(), request.email()),
                identityApi, memberService);
        sessionAuth.authenticate(request.username().trim().toLowerCase(),
                request.password(), httpRequest, httpResponse);
        sessionAuth.regenerateCsrfToken(httpRequest, httpResponse);
    }
}
