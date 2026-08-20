package com.zija.household.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaSessionInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.session.jdbc.initialize-schema=never")
@AutoConfigureMockMvc
@TestPropertySource(properties = "zija.setup.token=integration-setup-token")
class HouseholdBootstrapSetupTokenIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final String BOOTSTRAP_BODY = """
            {
              "householdName": "测试家庭",
              "username": "owner",
              "password": "Passw0rd!",
              "displayName": "所有者"
            }
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void cleanTables() {
        TestDb.cleanAll(jdbcTemplate);
    }

    @Test
    void statusReportsSetupTokenRequiredWhenConfigured() throws Exception {
        mockMvc.perform(get("/api/v1/household/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupTokenRequired").value(true));
    }

    @Test
    void bootstrapRejectsMissingSetupToken() throws Exception {
        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOTSTRAP_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HOUSEHOLD_TOKEN_INVALID"));
    }

    @Test
    void bootstrapRejectsWrongSetupToken() throws Exception {
        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(csrf())
                        .header(HouseholdSetupTokenGuard.HEADER_NAME, "wrong-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOTSTRAP_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HOUSEHOLD_TOKEN_INVALID"));
    }

    @Test
    void bootstrapSucceedsWithCorrectSetupToken() throws Exception {
        mockMvc.perform(post("/api/v1/household/bootstrap")
                        .with(csrf())
                        .header(HouseholdSetupTokenGuard.HEADER_NAME, "integration-setup-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BOOTSTRAP_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("owner"));
    }
}
