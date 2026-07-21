package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class OwnerRecoveryControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean OwnerRecoveryService recoveryService;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

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
}
