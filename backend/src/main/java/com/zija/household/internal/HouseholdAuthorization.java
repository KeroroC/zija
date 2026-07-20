package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("householdAuthorization")
class HouseholdAuthorization {

    private final HouseholdApi householdApi;

    HouseholdAuthorization(HouseholdApi householdApi) {
        this.householdApi = householdApi;
    }

    public boolean hasAtLeast(Authentication auth, String requiredRole) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof ZijaPrincipal principal)) {
            return false;
        }
        return householdApi.hasAtLeastRole(
                principal.getAccountId(),
                HouseholdApi.MemberRole.valueOf(requiredRole));
    }
}
