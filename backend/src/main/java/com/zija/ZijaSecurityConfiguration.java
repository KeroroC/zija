package com.zija;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import com.zija.system.SystemApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring Security 安全配置。
 *
 * <p>主要配置内容包括：</p>
 * <ul>
 *   <li>URL 级别授权：放行登录、CSRF、家庭初始化、邀请、密码恢复、系统信息、Swagger UI 及健康检查等端点，其余请求需认证</li>
 *   <li>CSRF 防护：基于 Cookie 的 CSRF Token 仓库（HttpOnly 关闭，供前端 JS 读取）</li>
 *   <li>会话管理：按需创建会话，启用会话固定攻击防护（每次认证更换 Session ID）</li>
 *   <li>登出处理：清除会话与认证信息，删除相关 Cookie，并记录审计日志</li>
 *   <li>密码编码器：委托式编码器，默认使用 bcrypt，兼容 sha256 历史格式</li>
 *   <li>认证管理器：基于 DAO 的认证提供者</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class ZijaSecurityConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ZijaProblemHandlers problemHandlers,
            SystemApi systemApi,
            CsrfTokenRepository csrfTokenRepository
    ) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/household/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/household/bootstrap").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/inspect").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/redeem").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/owner-recovery/inspect").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/owner-recovery/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.spa()
                        .csrfTokenRepository(csrfTokenRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("ZIJA_SESSION", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, auth) -> {
                            if (auth != null && auth.getPrincipal() instanceof ZijaPrincipal principal) {
                                systemApi.recordAudit(new SystemApi.AuditEvent(
                                        "LOGOUT", "SUCCESS", null,
                                        principal.getAccountId(), null,
                                        (String) request.getAttribute("zija.request-id"),
                                        request.getRemoteAddr(), null
                                ));
                            }
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        }))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        // StandardPasswordEncoder 已弃用，但保留用于验证历史 {sha256} 格式哈希；
        // 新密码一律使用 bcrypt（DelegatingPasswordEncoder 默认前缀）。
        @SuppressWarnings("deprecation")
        var legacySha256 = new StandardPasswordEncoder();
        encoders.put("sha256", legacySha256);
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        var provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }
}
