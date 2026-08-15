package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OpenApiContractTest extends AbstractMockMvcIntegrationTest {

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
            "/api/v1/system/info",
            // Phase 3: File, Catalog, Location
            "/api/v1/files",
            "/api/v1/files/{fileId}",
            "/api/v1/items",
            "/api/v1/categories/tree",
            "/api/v1/brands",
            "/api/v1/units",
            "/api/v1/tags",
            "/api/v1/locations/tree"
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
    }
}
