package com.zija.household.internal;

import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
class HouseholdControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean HouseholdService householdService;
    @MockitoBean MemberService memberService;
    @MockitoBean ZijaSessionAuthenticationSupport sessionAuth;
    @MockitoBean SystemApi systemApi;

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
}
