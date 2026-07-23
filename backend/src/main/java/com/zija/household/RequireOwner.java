package com.zija.household;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法/类级别权限注解，要求当前用户至少拥有家庭「所有者」角色。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'OWNER')")
public @interface RequireOwner {
}
