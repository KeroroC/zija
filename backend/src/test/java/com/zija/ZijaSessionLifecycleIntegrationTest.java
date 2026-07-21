package com.zija;

import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class ZijaSessionLifecycleIntegrationTest {

    @Autowired MockMvc mockMvc;
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
                        .header("X-XSRF-TOKEN", newCsrfCookie.getValue()))
                .andExpect(status().isNoContent());
    }
}
