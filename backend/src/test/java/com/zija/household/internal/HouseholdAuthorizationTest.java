package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import com.zija.household.RequireOwner;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HouseholdAuthorizationTest {

    @Test
    void memberEndpointsDeclareOperationSpecificAuthority() throws NoSuchMethodException {
        assertThat(MemberController.class.isAnnotationPresent(RequireAdmin.class)).isFalse();
        assertThat(MemberController.class.getDeclaredMethod("list", ZijaPrincipal.class)
                .isAnnotationPresent(RequireMember.class)).isTrue();
        assertThat(MemberController.class.getDeclaredMethod("updateRole", ZijaPrincipal.class, UUID.class,
                        MemberController.UpdateRoleRequest.class)
                .isAnnotationPresent(RequireOwner.class)).isTrue();
        assertThat(MemberController.class.getDeclaredMethod("updateStatus", ZijaPrincipal.class, UUID.class,
                        MemberController.UpdateStatusRequest.class)
                .isAnnotationPresent(RequireAdmin.class)).isTrue();
    }

    @Test
    void rolesHaveExplicitDescendingAuthority() {
        assertThat(HouseholdApi.MemberRole.OWNER.isAtLeast(HouseholdApi.MemberRole.OWNER)).isTrue();
        assertThat(HouseholdApi.MemberRole.OWNER.isAtLeast(HouseholdApi.MemberRole.ADMIN)).isTrue();
        assertThat(HouseholdApi.MemberRole.OWNER.isAtLeast(HouseholdApi.MemberRole.MEMBER)).isTrue();
        assertThat(HouseholdApi.MemberRole.ADMIN.isAtLeast(HouseholdApi.MemberRole.OWNER)).isFalse();
        assertThat(HouseholdApi.MemberRole.ADMIN.isAtLeast(HouseholdApi.MemberRole.ADMIN)).isTrue();
        assertThat(HouseholdApi.MemberRole.ADMIN.isAtLeast(HouseholdApi.MemberRole.MEMBER)).isTrue();
        assertThat(HouseholdApi.MemberRole.MEMBER.isAtLeast(HouseholdApi.MemberRole.OWNER)).isFalse();
        assertThat(HouseholdApi.MemberRole.MEMBER.isAtLeast(HouseholdApi.MemberRole.ADMIN)).isFalse();
        assertThat(HouseholdApi.MemberRole.MEMBER.isAtLeast(HouseholdApi.MemberRole.MEMBER)).isTrue();
    }

    @Test
    void ownerIsAtLeastMember() {
        var api = mock(com.zija.household.HouseholdApi.class);
        var accountId = UUID.randomUUID();
        when(api.hasAtLeastRole(accountId, HouseholdApi.MemberRole.MEMBER)).thenReturn(true);
        var auth = auth(accountId);

        var evaluator = new HouseholdAuthorization(api);
        assertThat(evaluator.hasAtLeast(auth, "MEMBER")).isTrue();
    }

    @Test
    void anonymousAuthenticationRejected() {
        var api = mock(com.zija.household.HouseholdApi.class);
        var evaluator = new HouseholdAuthorization(api);
        var auth = new UsernamePasswordAuthenticationToken("anon", "creds");
        assertThat(evaluator.hasAtLeast(auth, "MEMBER")).isFalse();
    }

    private Authentication auth(UUID accountId) {
        var principal = new ZijaPrincipal(accountId, "u", "d", "h", true);
        return new UsernamePasswordAuthenticationToken(principal, "creds", java.util.List.of());
    }
}
