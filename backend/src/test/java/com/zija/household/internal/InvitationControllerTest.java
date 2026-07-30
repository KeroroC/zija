package com.zija.household.internal;

import com.zija.AbstractWebMvcSliceTest;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.HouseholdApi;
import com.zija.identity.IdentityApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InvitationController.class)
@Import({HouseholdAuthzTestSupport.class, HouseholdExceptionHandler.class})
class InvitationControllerTest extends AbstractWebMvcSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean InvitationService invitationService;
    @MockitoBean HouseholdService householdService;
    @MockitoBean MemberService memberService;
    @MockitoBean ZijaSessionAuthenticationSupport sessionAuth;
    @MockitoBean IdentityApi identityApi;

    @Test
    void createRejectsInvalidRoleAndExpiry() throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(accountId, "owner", "Owner", "hash", true);
        when(householdService.hasAtLeastRole(accountId, HouseholdApi.MemberRole.ADMIN))
                .thenReturn(true);

        mockMvc.perform(post("/api/v1/invitations")
                        .with(SecurityMockMvcRequestPostProcessors.user(principal))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\",\"expiresInHours\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.role").exists())
                .andExpect(jsonPath("$.fieldErrors.expiresInHours").exists());
    }

    @Test
    void redeemRejectsPasswordLongerThanBcryptByteLimit() throws Exception {
        var password = "密".repeat(25);

        mockMvc.perform(post("/api/v1/invitations/redeem")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "secret",
                                  "username": "member",
                                  "password": "%s",
                                  "displayName": "成员"
                                }
                                """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void redeemReturnsCreatedAuthenticatedSession() throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(
                accountId, "member", "成员", "{bcrypt}x", true);
        var authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(principal);
        when(sessionAuth.authenticate(any(), any(), any(), any()))
                .thenReturn(authentication);

        mockMvc.perform(post("/api/v1/invitations/redeem")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "token": "secret",
                                  "username": "member",
                                  "password": "Passw0rd!",
                                  "displayName": "成员"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value("member"));
    }
}
