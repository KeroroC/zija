package com.zija;

import com.zija.system.SystemApi;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@code @WebMvcTest} 切片基类。
 *
 * <p>提供 Web 切片测试所需的 Spring Security 装配与共享 mock bean，
 * 避免每个测试类重复样板。具体差异仍由子类通过 {@code @WebMvcTest(controllers=...)}
 * 显式声明。{@code HouseholdAuthorization}（{@code @RequireMember} 所需）位于
 * 另一个模块的包私有命名空间，由 {@code HouseholdAuthzTestSupport} 桥接导入。
 */
@ImportAutoConfiguration({
        SecurityAutoConfiguration.class,
        ServletWebSecurityAutoConfiguration.class,
        SecurityFilterAutoConfiguration.class
})
@Import({
        ZijaSecurityConfiguration.class,
        ZijaRequestIdFilter.class,
        ZijaProblemHandlers.class
})
public abstract class AbstractWebMvcSliceTest {

    @MockitoBean
    protected UserDetailsService userDetailsService;

    @MockitoBean
    protected SystemApi systemApi;

    @MockitoBean
    protected ZijaSessionInvalidator sessionInvalidator;
}
