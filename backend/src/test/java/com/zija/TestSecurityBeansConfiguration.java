package com.zija;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/**
 * Test-only beans required by security-related components.
 *
 * <p>Spring Boot 4.1 does not auto-configure a {@link PasswordEncoder} (no
 * {@code PasswordEncoderAutoConfiguration} exists). The production bean will be
 * declared in the security configuration (task 16); until then this test-scoped
 * configuration supplies a {@code DelegatingPasswordEncoder} so that
 * {@code @SpringBootTest} context loads succeed.
 *
 * <p>{@link AuthenticationManager} and {@link CsrfTokenRepository} are required
 * by {@link ZijaSessionAuthenticationSupport}; their production beans are also
 * declared in the security configuration (task 16). Mocks are registered here
 * so that the application context loads without those production beans.
 */
@Configuration
class TestSecurityBeansConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager() {
        return Mockito.mock(AuthenticationManager.class);
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return Mockito.mock(CsrfTokenRepository.class);
    }
}
