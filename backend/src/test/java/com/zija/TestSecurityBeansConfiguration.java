package com.zija;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test-only PasswordEncoder bean.
 *
 * <p>Spring Boot 4.1 does not auto-configure a {@link PasswordEncoder} (no
 * {@code PasswordEncoderAutoConfiguration} exists). The production bean will be
 * declared in the security configuration (task 16); until then this test-scoped
 * configuration supplies a {@code DelegatingPasswordEncoder} so that
 * {@code @SpringBootTest} context loads succeed.
 */
@Configuration
class TestSecurityBeansConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
