package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HouseholdAuthorizationTest {

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
