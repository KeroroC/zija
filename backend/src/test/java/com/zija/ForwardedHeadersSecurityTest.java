package com.zija;

import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归防护：Cookie 的 Secure 标志必须由传输层决定。
 *
 * <p>背景（见 {@code application-prod.yml} 与 {@code deploy/nginx/default.conf}）：
 * 应用的自定义 {@code CookieSerializer} 不会读取 {@code server.servlet.session.cookie.secure}，
 * Secure 标志实际跟随 {@code request.isSecure()}（经 {@code X-Forwarded-Proto} 透传）。
 * 若任何配置在纯 HTTP 下强制设置 Secure，浏览器将拒收 Cookie，prod 部署无法登录；
 * 若 TLS 终止后不透传 {@code X-Forwarded-Proto: https}，则 Secure 标志静默缺失。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.session.jdbc.initialize-schema=never",
        "server.forward-headers-strategy=native",
        "zija.file.storage-path=/tmp/zija-test-files"
})
class ForwardedHeadersSecurityTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @MockitoBean
    AuthenticationManager authenticationManager;

    @MockitoBean
    SystemApi systemApi;

    @Test
    void prodProfileOverPlainHttpIssuesNoSecureCookies() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = Arrays.stream(result.getResponse().getCookies()).toList();
        assertThat(cookies)
                .as("纯 HTTP（prod profile）下任何 Cookie 都不得带 Secure，否则浏览器拒收导致无法登录")
                .isNotEmpty()
                .allSatisfy(cookie -> assertThat(cookie.getSecure())
                        .as("Cookie %s 不应带 Secure", cookie.getName())
                        .isFalse());
    }

    @Test
    void secureTransportProducesSecureCookies() throws Exception {
        var result = mockMvc.perform(get("/api/v1/auth/csrf").secure(true))
                .andExpect(status().isOk())
                .andReturn();

        var cookies = Arrays.stream(result.getResponse().getCookies()).toList();
        assertThat(cookies)
                .as("HTTPS（secure 请求）下 Cookie 必须带 Secure，TLS 终止部署依赖此行为")
                .isNotEmpty()
                .allSatisfy(cookie -> assertThat(cookie.getSecure())
                        .as("Cookie %s 应带 Secure", cookie.getName())
                        .isTrue());
    }

    /** 登录成功后的会话 Cookie（DefaultCookieSerializer 写出）同样跟随传输层。 */
    private void assertSessionCookieSecureFlag(boolean secureTransport) throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(accountId, "owner", "所有者", "{bcrypt}x", true);
        // 真实 Authentication 对象（可被 JDBC 会话存储 Java 序列化），而非 Mockito mock
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(principal, "Passw0rd!", principal.getAuthorities());
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        var csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").secure(secureTransport))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        var loginRequest = post("/api/v1/auth/login")
                .cookie(csrfCookie)
                .header("X-XSRF-TOKEN", csrfCookie.getValue())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"owner","password":"Passw0rd!"}
                        """);
        if (secureTransport) {
            loginRequest = loginRequest.secure(true);
        }
        var loginResult = mockMvc.perform(loginRequest)
                .andExpect(status().isOk())
                .andReturn();

        var sessionCookie = loginResult.getResponse().getCookie("ZIJA_SESSION");
        assertThat(sessionCookie)
                .as("登录成功后应下发 ZIJA_SESSION 会话 Cookie")
                .isNotNull();
        assertThat(sessionCookie.getSecure())
                .as(secureTransport
                        ? "HTTPS 登录下 ZIJA_SESSION 必须带 Secure"
                        : "纯 HTTP 登录下 ZIJA_SESSION 不得带 Secure（浏览器会拒收）")
                .isEqualTo(secureTransport);
    }

    @Test
    void sessionCookieFollowsTransportOnLogin() throws Exception {
        assertSessionCookieSecureFlag(true);
    }

    @Test
    void plainHttpLoginIssuesNonSecureSessionCookie() throws Exception {
        assertSessionCookieSecureFlag(false);
    }
}
