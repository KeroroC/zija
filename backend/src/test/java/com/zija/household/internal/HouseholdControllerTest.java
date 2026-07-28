package com.zija.household.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.MemberConcurrentUpdateException;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class HouseholdControllerTest extends AbstractMockMvcIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean HouseholdService householdService;
    @MockitoBean MemberService memberService;
    @MockitoBean ZijaSessionAuthenticationSupport sessionAuth;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    @Test
    void statusIsPublic() throws Exception {
        when(householdService.isInitialized()).thenReturn(false);
        mockMvc.perform(get("/api/v1/household/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(false));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownershipTransferConflictReturnsProblemDetails() throws Exception {
        var accountId = UUID.randomUUID();
        var targetMemberId = UUID.randomUUID();
        var principal = new ZijaPrincipal(
                accountId, "owner", "所有者", "{bcrypt}x", true);
        when(householdService.hasAtLeastRole(
                accountId, HouseholdApi.MemberRole.OWNER)).thenReturn(true);
        doThrow(new MemberConcurrentUpdateException()).when(memberService)
                .transferOwnership(accountId, targetMemberId);

        mockMvc.perform(post("/api/v1/household/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("X-Request-Id", "member-concurrent-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new HouseholdController.TransferOwnershipRequest(targetMemberId))))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode")
                        .value("HOUSEHOLD_MEMBER_CONCURRENT_UPDATE"))
                .andExpect(jsonPath("$.requestId").value("member-concurrent-123"));
    }

    @Test
    void ownershipTransferRejectsMissingTargetMemberId() throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(
                accountId, "owner", "所有者", "{bcrypt}x", true);
        when(householdService.hasAtLeastRole(
                accountId, HouseholdApi.MemberRole.OWNER)).thenReturn(true);

        mockMvc.perform(post("/api/v1/household/transfer-ownership")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("X-Request-Id", "transfer-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetMemberId\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("transfer-validation"))
                .andExpect(jsonPath("$.fieldErrors.targetMemberId").exists());
    }

    @Test
    void bootstrapRejectsMalformedJsonWithStableProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("X-Request-Id", "malformed-json")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("malformed-json"))
                .andExpect(jsonPath("$.fieldErrors.request").exists());
    }

    @Test
    void bootstrapRejectsMissingBodyWithStableProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("X-Request-Id", "missing-body")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.requestId").value("missing-body"))
                .andExpect(jsonPath("$.fieldErrors.request").exists());
    }

    @Test
    void bootstrapRejectsPasswordLongerThanBcryptByteLimit() throws Exception {
        var password = "密".repeat(25);

        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "householdName": "知家",
                                  "username": "owner",
                                  "password": "%s",
                                  "displayName": "所有者"
                                }
                                """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void bootstrapReturnsCreatedAuthenticatedSession() throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(
                accountId, "owner", "所有者", "{bcrypt}x", true);
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(householdService.bootstrap(any())).thenReturn(
                new HouseholdApi.HouseholdInfo(UUID.randomUUID(), "知家", "Asia/Shanghai"));
        when(sessionAuth.authenticate(any(), any(), any(), any()))
                .thenReturn(authentication);

        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "householdName": "知家",
                                  "username": "owner",
                                  "password": "Passw0rd!",
                                  "displayName": "所有者"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value("owner"));
    }
}
