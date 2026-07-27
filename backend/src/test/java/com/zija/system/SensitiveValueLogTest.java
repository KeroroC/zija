package com.zija.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.ObjectMapper;
import com.zija.ZijaSessionInvalidator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 对抗测试：验证敏感值（密码、会话 Cookie、恢复令牌）不会泄漏到应用日志。
 *
 * <p>本测试作为代码纪律守卫——如果开发者在登录失败时 log.info(password)，
 * 或在恢复流程中 log.info(token)，此测试将捕获回归。</p>
 *
 * <p>采用 Logback {@link ListAppender} 捕获根日志器输出，
 * 在每次 HTTP 交互后断言敏感字面量不存在于日志消息中。</p>
 *
 * <p>使用 Testcontainers 提供真实 PostgreSQL，通过 {@code @ServiceConnection} 自动配置数据源。
 * 在 {@code @BeforeAll} 中执行 household bootstrap 创建 Owner 账号，
 * 随后各测试方法分别验证登录失败、登录成功、恢复令牌场景。</p>
 */
@Testcontainers
@SpringBootTest(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
class SensitiveValueLogTest {

    private static final String OWNER_PASSWORD = "Bootstrap1!";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    /**
     * 整个测试类只执行一次：bootstrap 家庭并创建 Owner 账号，随后登出。
     */
    @BeforeAll
    static void bootstrapHousehold(@Autowired MockMvc mvc) throws Exception {
        mvc.perform(post("/api/v1/household/bootstrap")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdName":"TestHome","username":"owner",
                                 "password":"%s","displayName":"Test Owner",
                                 "email":"owner@test.com"}
                                """.formatted(OWNER_PASSWORD)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @BeforeEach
    void captureLogs() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void stopCapturing() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    private String allLogMessages() {
        return logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    @Test
    void loginFailureDoesNotLogPassword() throws Exception {
        String wrongPassword = "WrongPass123!";

        mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginPayload("owner", wrongPassword))))
                .andExpect(status().isUnauthorized());

        assertThat(allLogMessages())
                .as("login failure logs must not contain plaintext password")
                .doesNotContain(wrongPassword);
    }

    @Test
    void sessionCookieValueNotLogged() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginPayload("owner", OWNER_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();

        // 从 Set-Cookie 头提取会话 Cookie 值
        String setCookie = response.getHeader("Set-Cookie");
        if (setCookie != null && setCookie.contains("SESSION=")) {
            String cookieValue = setCookie.replaceAll(".*SESSION=([^;]+).*", "$1");
            assertThat(cookieValue).isNotEmpty();
            assertThat(allLogMessages())
                    .as("logs must not contain session cookie value")
                    .doesNotContain(cookieValue);
        }

        // 密码也不应出现在日志中
        assertThat(allLogMessages())
                .as("login success logs must not contain plaintext password")
                .doesNotContain(OWNER_PASSWORD);
    }

    @Test
    void recoveryTokenNotLogged() throws Exception {
        String rawToken = "recovery-token-abc123xyz789";

        mvc.perform(post("/api/v1/owner-recovery/inspect")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new InspectPayload(rawToken))));

        assertThat(allLogMessages())
                .as("recovery endpoint logs must not contain raw token")
                .doesNotContain(rawToken);
    }

    /** Login request body record. */
    private record LoginPayload(String username, String password) {}

    /** Recovery inspect request body record. */
    private record InspectPayload(String token) {}
}
