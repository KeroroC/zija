package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean HouseholdService householdService;
    @MockitoBean MemberService memberService;
    @MockitoBean MemberMapper memberMapper;
    @MockitoBean SystemApi systemApi;
    @MockitoBean ZijaSessionInvalidator sessionInvalidator;

    @Test
    void updateRoleRejectsInvalidRole() throws Exception {
        mockMvc.perform(put("/api/v1/members/{id}/role", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.user(owner()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void updateStatusRejectsInvalidStatus() throws Exception {
        mockMvc.perform(put("/api/v1/members/{id}/status", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.user(owner()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void insufficientRoleUsesStableProblemDetails() throws Exception {
        doThrow(new InsufficientRoleException())
                .when(memberService).updateStatus(any(), any(), any());

        mockMvc.perform(put("/api/v1/members/{id}/status", UUID.randomUUID())
                        .with(SecurityMockMvcRequestPostProcessors.user(owner()))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .header("X-Request-Id", "member-access-denied")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DEACTIVATED\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HOUSEHOLD_INSUFFICIENT_ROLE"))
                .andExpect(jsonPath("$.requestId").value("member-access-denied"));
    }

    private ZijaPrincipal owner() {
        var accountId = UUID.randomUUID();
        when(householdService.hasAtLeastRole(accountId, HouseholdApi.MemberRole.OWNER))
                .thenReturn(true);
        when(householdService.hasAtLeastRole(accountId, HouseholdApi.MemberRole.ADMIN))
                .thenReturn(true);
        return new ZijaPrincipal(accountId, "owner", "Owner", "hash", true);
    }
}
