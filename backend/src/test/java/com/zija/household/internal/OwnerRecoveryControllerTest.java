package com.zija.household.internal;

import com.zija.AbstractWebMvcSliceTest;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.identity.IdentityApi;
import com.zija.household.internal.exception.InvalidInvitationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OwnerRecoveryController.class)
@Import(HouseholdExceptionHandler.class)
class OwnerRecoveryControllerTest extends AbstractWebMvcSliceTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OwnerRecoveryService recoveryService;
    @MockitoBean ZijaSessionAuthenticationSupport sessionAuth;
    @MockitoBean IdentityApi identityApi;

    @Test
    void resetRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/v1/owner-recovery/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"secret\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void invalidTokenUsesGenericProblemDetails() throws Exception {
        doThrow(new InvalidInvitationException())
                .when(recoveryService).resetPassword(anyString(), anyString());

        mockMvc.perform(post("/api/v1/owner-recovery/reset-password")
                        .with(csrf())
                        .header("X-Request-Id", "recovery-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"secret\",\"newPassword\":\"new-password\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("HOUSEHOLD_TOKEN_INVALID"))
                .andExpect(jsonPath("$.requestId").value("recovery-invalid"));
    }

    @Test
    void resetRejectsPasswordLongerThanBcryptByteLimit() throws Exception {
        var password = "密".repeat(25);

        mockMvc.perform(post("/api/v1/owner-recovery/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"secret","newPassword":"%s"}
                                """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
    }

    @Test
    void successfulResetRotatesCsrfToken() throws Exception {
        mockMvc.perform(post("/api/v1/owner-recovery/reset-password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"secret","newPassword":"new-password"}
                                """))
                .andExpect(status().isOk());

        verify(sessionAuth).regenerateCsrfToken(any(), any());
    }
}
