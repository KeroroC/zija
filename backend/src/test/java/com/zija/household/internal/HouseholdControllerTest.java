package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.HouseholdApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class HouseholdControllerTest {

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
}
