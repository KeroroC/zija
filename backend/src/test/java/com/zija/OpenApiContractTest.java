package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.flyway.enabled=false", "zija.session.jdbc.enabled=false"})
@AutoConfigureMockMvc
class OpenApiContractTest {

    private static final Set<String> REQUIRED_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/session",
            "/api/v1/auth/csrf",
            "/api/v1/auth/password",
            "/api/v1/household/status",
            "/api/v1/household/bootstrap",
            "/api/v1/household/me",
            "/api/v1/household/transfer-ownership",
            "/api/v1/invitations",
            "/api/v1/invitations/inspect",
            "/api/v1/invitations/redeem",
            "/api/v1/members",
            "/api/v1/owner-recovery/inspect",
            "/api/v1/owner-recovery/reset-password",
            "/api/v1/system/info"
    );

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @Test
    void apiDocsMatchApprovedPhase2ContractBaseline() throws Exception {
        var response = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var docs = objectMapper.readTree(response);
        var paths = docs.path("paths");
        assertThat(paths.isObject()).isTrue();

        var actualPaths = new TreeSet<>(paths.propertyNames());
        assertThat(actualPaths).containsAll(REQUIRED_PATHS);

        var baseline = objectMapper.readTree(new ClassPathResource(
                "openapi/phase2-openapi-baseline.json").getContentAsByteArray());
        assertThat(docs)
                .as("OpenAPI methods, schemas, required fields and responses must match the approved baseline")
                .isEqualTo(baseline);
    }
}
