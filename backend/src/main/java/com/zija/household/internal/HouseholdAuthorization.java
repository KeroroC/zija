package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * 家庭授权决策组件，供 Spring Security SpEL 表达式使用。
 * <p>
 * 通过 Bean 名称 {@code householdAuthorization} 在安全配置中引用，
 * 判断当前认证用户是否具有指定的最低家庭角色。
 */
@Component("householdAuthorization")
class HouseholdAuthorization {

    private final HouseholdApi householdApi;

    HouseholdAuthorization(HouseholdApi householdApi) {
        this.householdApi = householdApi;
    }

    /**
     * 判断当前认证用户是否具有至少指定级别的家庭角色。
     *
     * @param auth         Spring Security 认证对象
     * @param requiredRole 最低要求角色名称（如 OWNER、ADMIN、MEMBER）
     * @return 满足角色要求返回 true，否则返回 false
     */
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
