package com.zija.identity.internal;

import com.zija.AbstractWebMvcSliceTest;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.auth.ChangePasswordRequest;
import com.zija.identity.internal.auth.LoginRequest;
import com.zija.identity.internal.exception.AccountVersionConflictException;
import com.zija.identity.internal.exception.LoginRateLimitedException;
import com.zija.identity.internal.persistence.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IdentityController.class)
@Import(IdentityExceptionHandler.class)
class IdentityControllerTest extends AbstractWebMvcSliceTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean IdentityService identityService;
    @MockitoBean AccountMapper accountMapper;
    @MockitoBean AuthenticationManager authenticationManager;
    @MockitoBean ZijaSessionAuthenticationSupport sessionAuthSupport;
    @MockitoBean LoginRateLimiter rateLimiter;

    @Test
    void loginReturnsSessionAndAccountIdentity() throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(accountId, "owner", "所有者", "{bcrypt}x", true);
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(auth.isAuthenticated()).thenReturn(true);
        when(sessionAuthSupport.authenticate(any(), any(), any(), any())).thenReturn(auth);
        when(identityService.findByNormalizedUsername("owner"))
                .thenReturn(Optional.of(new IdentityApi.AccountInfo(
                        accountId, "owner", "所有者", null, "ACTIVE")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("owner", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value("owner"));
    }

    @Test
    void loginFailureReturnsSameErrorAsUnknownUser() throws Exception {
        when(sessionAuthSupport.authenticate(any(), any(), any(), any()))
                .thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("ghost", "Passw0rd!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGIN_FAILED"));
    }

    @Test
    void getSessionReturnsNotAuthenticatedWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsUsernameLongerThanStorageLimit() throws Exception {
        var principal = new ZijaPrincipal(
                UUID.randomUUID(), "owner", "所有者", "{bcrypt}x", true);
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(sessionAuthSupport.authenticate(any(), any(), any(), any())).thenReturn(auth);

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("X-Request-Id", "login-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("x".repeat(51), "Passw0rd!"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("login-validation"))
                .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    void changePasswordRejectsShortNewPassword() throws Exception {
        var principal = new ZijaPrincipal(
                UUID.randomUUID(), "owner", "所有者", "{bcrypt}x", true);

        mockMvc.perform(put("/api/v1/auth/password")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPass1", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        verifyNoInteractions(identityService);
    }

    @Test
    void changePasswordRejectsNewPasswordLongerThanBcryptByteLimit() throws Exception {
        var principal = new ZijaPrincipal(
                UUID.randomUUID(), "owner", "所有者", "{bcrypt}x", true);

        mockMvc.perform(put("/api/v1/auth/password")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPass1", "密".repeat(25)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        verifyNoInteractions(identityService);
    }

    @Test
    void blockedLoginIsRejectedBeforeAuthentication() throws Exception {
        doThrow(new LoginRateLimitedException("rate limited"))
                .when(rateLimiter).checkAllowed("owner", "203.0.113.10");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("Owner", "Passw0rd!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGIN_RATE_LIMITED"));

        verifyNoInteractions(sessionAuthSupport);
    }

    @Test
    void loginIgnoresUntrustedForwardedForHeader() throws Exception {
        when(sessionAuthSupport.authenticate(any(), any(), any(), any()))
                .thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .with(request -> {
                            request.setRemoteAddr("203.0.113.10");
                            return request;
                        })
                        .header("X-Forwarded-For", "198.51.100.99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("ghost", "Passw0rd!"))))
                .andExpect(status().isUnauthorized());

        verify(rateLimiter).recordFailure("ghost", "203.0.113.10");
    }

    @Test
    void changeDisplayNameUpdatesPrincipalAndAudits() throws Exception {
        var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧名字", "{bcrypt}x", true);
        var updatedInfo = new IdentityApi.AccountInfo(
                principal.getAccountId(), "owner", "新名字", null, "ACTIVE");
        when(identityService.updateDisplayName(principal.getAccountId(), "  新名字  "))
                .thenReturn(updatedInfo);

        mockMvc.perform(put("/api/v1/auth/display-name")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "  新名字  "))))
                .andExpect(status().isNoContent());

        verify(identityService).updateDisplayName(principal.getAccountId(), "  新名字  ");
        verify(sessionAuthSupport).refreshPrincipal(any(ZijaPrincipal.class), any(), any());
        verify(systemApi).recordAudit(argThat(e ->
                "DISPLAY_NAME_CHANGED".equals(e.action())
                        && principal.getAccountId().equals(e.actorAccountId())));
    }

    @Test
    void changeDisplayNameRejectsBlank() throws Exception {
        var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧", "{bcrypt}x", true);

        mockMvc.perform(put("/api/v1/auth/display-name")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());

        verifyNoInteractions(identityService);
    }

    @Test
    void changeDisplayNameRejectsTooLong() throws Exception {
        var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧", "{bcrypt}x", true);

        mockMvc.perform(put("/api/v1/auth/display-name")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "长".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.displayName").exists());

        verifyNoInteractions(identityService);
    }

    @Test
    void changePasswordReturns409OnVersionConflict() throws Exception {
        var principal = new ZijaPrincipal(
                UUID.randomUUID(), "owner", "所有者", "{bcrypt}x", true);
        doThrow(new AccountVersionConflictException())
                .when(identityService).changePassword(any(), any());

        mockMvc.perform(put("/api/v1/auth/password")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPass1", "NewPass2"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_VERSION_CONFLICT"));
    }

    @Test
    void changeDisplayNameReturns409OnVersionConflict() throws Exception {
        var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧", "{bcrypt}x", true);
        doThrow(new AccountVersionConflictException())
                .when(identityService).updateDisplayName(any(), any());

        mockMvc.perform(put("/api/v1/auth/display-name")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("displayName", "新名字"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_VERSION_CONFLICT"));
    }
}
