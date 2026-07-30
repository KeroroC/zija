package com.zija.household.internal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * 暴露包私有的 {@link HouseholdAuthorization} 给其他模块的
 * {@code @WebMvcTest} 使用。{@code @RequireMember} 注解里的
 * {@code @PreAuthorize("@householdAuthorization...")} SpEL 在切片中需要该 bean。
 *
 * <p>位于 {@code com.zija.household.internal} 同包内是必要条件,
 * 这样 {@link Import} 才能解析包私有引用。
 */
@TestConfiguration(proxyBeanMethods = false)
@Import(HouseholdAuthorization.class)
public class HouseholdAuthzTestSupport {
}
