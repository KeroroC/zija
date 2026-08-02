package com.zija;

import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ZijaSessionLifecycleIntegrationTest extends AbstractMockMvcIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountMapper accountMapper;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    @Test
    void loginRotatesSessionAndCsrfWhilePersistingAuthentication() throws Exception {
        var session = new MockHttpSession();
        var oldSessionId = session.getId();
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(
                accountId, "owner", "所有者", "{bcrypt}x", true);
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        var csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        var oldCsrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(oldCsrfCookie).isNotNull();
        assertThat(oldCsrfCookie.isHttpOnly()).isFalse();
        assertThat(oldCsrfCookie.getValue()).isNotBlank();

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .cookie(oldCsrfCookie)
                        .header("X-XSRF-TOKEN", oldCsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"owner","password":"Passw0rd!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(session.getId()).isNotEqualTo(oldSessionId);
        var newCsrfCookies = Arrays.stream(loginResult.getResponse().getCookies())
                .filter(cookie -> "XSRF-TOKEN".equals(cookie.getName()))
                .filter(cookie -> !cookie.getValue().isBlank())
                .toList();
        assertThat(newCsrfCookies)
                .as("login response should issue one non-empty XSRF cookie")
                .hasSize(1);
        var newCsrfCookie = newCsrfCookies.getFirst();
        assertThat(newCsrfCookie.isHttpOnly()).isFalse();
        assertThat(newCsrfCookie.getValue()).isNotBlank()
                .isNotEqualTo(oldCsrfCookie.getValue());

        mockMvc.perform(get("/api/v1/auth/session")
                        .session(session)
                        .cookie(newCsrfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .session(session)
                        .cookie(newCsrfCookie)
                        .header("X-Request-Id", "logout-request")
                        .header("X-XSRF-TOKEN", newCsrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Request-Id", "logout-request"));

        verify(systemApi).recordAudit(argThat(event ->
                "LOGOUT".equals(event.action())
                        && accountId.equals(event.actorAccountId())
                        && "logout-request".equals(event.requestId())));
    }

    @Test
    void changeDisplayNameRefreshesPrincipalWithinSession() throws Exception {
        var accountId = UUID.randomUUID();
        var username = "owner" + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        var originalPrincipal = new ZijaPrincipal(
                accountId, username, "旧名字", "{bcrypt}x", true);
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(originalPrincipal);
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);

        var account = new com.zija.identity.internal.persistence.AccountEntity();
        account.setId(accountId);
        account.setUsername(username);
        account.setUsernameNormalized(username);
        account.setPasswordHash("{bcrypt}$2a$10$examplehash");
        account.setDisplayName("旧名字");
        account.setStatus("ACTIVE");
        accountMapper.insert(account);

        var session = new MockHttpSession();
        var csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andReturn();
        var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();

        var loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .session(session)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // CSRF token rotates on login; use the new cookie for subsequent requests.
        var newCsrfCookie = Arrays.stream(loginResult.getResponse().getCookies())
                .filter(c -> "XSRF-TOKEN".equals(c.getName()))
                .filter(c -> !c.getValue().isBlank())
                .findFirst()
                .orElse(csrfCookie);

        mockMvc.perform(put("/api/v1/auth/display-name")
                        .session(session)
                        .cookie(newCsrfCookie)
                        .header("X-Request-Id", "rename-request")
                        .header("X-XSRF-TOKEN", newCsrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"新名字"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string("X-Request-Id", "rename-request"));

        mockMvc.perform(get("/api/v1/auth/session")
                        .session(session)
                        .cookie(newCsrfCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.displayName").value("新名字"));

        verify(systemApi).recordAudit(argThat(event ->
                "DISPLAY_NAME_CHANGED".equals(event.action())
                        && accountId.equals(event.actorAccountId())));

        accountMapper.deleteById(accountId);
    }
}
