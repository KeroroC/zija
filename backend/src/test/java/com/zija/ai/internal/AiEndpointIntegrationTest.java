package com.zija.ai.internal;

import com.zija.AbstractMockMvcIntegrationTest;
import com.zija.TestDb;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionInvalidator;
import com.zija.ai.AiApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Import(AiEndpointIntegrationTest.FakeProviderConfiguration.class)
class AiEndpointIntegrationTest extends AbstractMockMvcIntegrationTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID MEMBER_ACCOUNT_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final String CREDENTIAL = "test-provider-secret";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ZijaSessionInvalidator sessionInvalidator;

    @Autowired
    private AiApi aiApi;

    @Autowired
    private AiSettingsService aiSettingsService;

    @Autowired
    private AiRequestGuard requestGuard;

    @Autowired
    private BlockingAiModelProvider blockingProvider;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        requestGuard.reset();
        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (1, ?, '测试家庭', 'Asia/Shanghai')
                """, HOUSEHOLD_ID);
        insertMember(OWNER_ACCOUNT_ID, "owner", "OWNER");
        insertMember(MEMBER_ACCOUNT_ID, "member", "MEMBER");
    }

    @Test
    void administratorCanReadDisabledDefaultWithoutContactingProvider() throws Exception {
        mvc.perform(get("/api/v1/ai/settings").with(auth(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.providerId").value("ollama"))
                .andExpect(jsonPath("$.credentialConfigured").value(false))
                .andExpect(jsonPath("$.outboundEnabled").value(false))
                .andExpect(jsonPath("$.version").value(0));

        mvc.perform(get("/api/v1/ai/status").with(auth(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasonCode").value("AI_DISABLED"))
                .andExpect(jsonPath("$.providerId").value("ollama"));
    }

    @Test
    void administratorCanConfigureProviderAndSeeAvailableStatusWithoutReadingCredential() throws Exception {
        var response = mvc.perform(put("/api/v1/ai/settings")
                        .with(auth(owner()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.providerId").value("deterministic"))
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andExpect(jsonPath("$.requestsPerMinute").value(24))
                .andExpect(jsonPath("$.memberRequestsPerMinute").value(12))
                .andExpect(jsonPath("$.maxContextTokens").value(4096))
                .andExpect(jsonPath("$.maxConcurrentRequests").value(2))
                .andExpect(jsonPath("$.requestTimeoutSeconds").value(20))
                .andExpect(jsonPath("$.version").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(response).doesNotContain(CREDENTIAL);

        var settingsResponse = mvc.perform(get("/api/v1/ai/settings").with(auth(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialConfigured").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(settingsResponse).doesNotContain(CREDENTIAL);

        mvc.perform(get("/api/v1/ai/status").with(auth(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reasonCode").value("AVAILABLE"))
                .andExpect(jsonPath("$.providerId").value("deterministic"))
                .andExpect(jsonPath("$.chatModel").value("fixed-chat"))
                .andExpect(jsonPath("$.embeddingModel").value("fixed-embedding"));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'AI_SETTING_UPDATED'",
                Integer.class)).isOne();
        assertThat(jdbc.queryForObject(
                "SELECT detail::text FROM audit_log WHERE action = 'AI_SETTING_UPDATED'",
                String.class)).doesNotContain(CREDENTIAL);
    }

    @Test
    void staleConfigurationUpdateReturnsStableConflict() throws Exception {
        mvc.perform(put("/api/v1/ai/settings")
                        .with(auth(owner()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/v1/ai/settings")
                        .with(auth(owner()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errorCode").value("AI_CONFIGURATION_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    void enabledButUnreachableProviderIsExplicitlyUnavailable() throws Exception {
        aiSettingsService.update(HOUSEHOLD_ID, OWNER_ACCOUNT_ID, new AiSettingsService.UpdateCommand(
                true, "unavailable", null, false, false, 20, 10, 8192, 2, 30, 0));

        mvc.perform(get("/api/v1/ai/status").with(auth(owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reasonCode").value("PROVIDER_UNREACHABLE"));
    }

    @Test
    void ordinaryMemberCannotReadOrChangeAiConfiguration() throws Exception {
        mvc.perform(get("/api/v1/ai/settings").with(auth(member())))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/ai/status").with(auth(member())))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/v1/ai/settings")
                        .with(auth(member()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody(0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void deterministicProviderCompletesAndEmbedsThroughProjectApi() {
        aiSettingsService.update(HOUSEHOLD_ID, OWNER_ACCOUNT_ID, new AiSettingsService.UpdateCommand(
                true, "deterministic", CREDENTIAL, false, false,
                24, 12, 4096, 2, 20, 0));

        assertThat(aiApi.complete(new AiApi.ChatRequest("where is it?")).content())
                .isEqualTo("fixed:where is it?");
        List<float[]> vectors = aiApi.embed(new AiApi.EmbeddingRequest(List.of("manual"))).vectors();
        assertThat(vectors).hasSize(1);
        assertThat(vectors.getFirst()).hasSize(AiService.EMBEDDING_DIMENSIONS);
        assertThat(vectors.getFirst()[0]).isEqualTo(1.0f);
    }

    @Test
    void rejectsProviderEmbeddingsThatDoNotMatchTheVectorStoreDimension() {
        aiSettingsService.update(HOUSEHOLD_ID, OWNER_ACCOUNT_ID, new AiSettingsService.UpdateCommand(
                true, "invalid-dimension", null, false, false,
                24, 12, 4096, 2, 20, 0));

        assertThatThrownBy(() -> aiApi.embed(new AiApi.EmbeddingRequest(List.of("manual"))))
                .isInstanceOf(AiProviderUnavailableException.class)
                .hasMessage("provider returned invalid embedding dimensions");
    }

    @Test
    void timeoutKeepsConcurrencyPermitUntilProviderTaskActuallyFinishes() {
        aiSettingsService.update(HOUSEHOLD_ID, OWNER_ACCOUNT_ID, new AiSettingsService.UpdateCommand(
                true, "blocking", null, false, false,
                24, 12, 4096, 1, 1, 0));

        assertThatThrownBy(() -> aiApi.complete(new AiApi.ChatRequest("first")))
                .isInstanceOf(AiProviderUnavailableException.class)
                .hasMessage("provider call timed out");
        assertThatThrownBy(() -> aiApi.complete(new AiApi.ChatRequest("second")))
                .isInstanceOf(AiRequestLimitException.class)
                .hasMessage("AI concurrency limit exceeded");

        blockingProvider.release();
    }

    private String updateBody(int version) {
        return """
                {
                  "enabled": true,
                  "providerId": "deterministic",
                  "credential": "%s",
                  "clearCredential": false,
                  "outboundEnabled": false,
                  "requestsPerMinute": 24,
                  "memberRequestsPerMinute": 12,
                  "maxContextTokens": 4096,
                  "maxConcurrentRequests": 2,
                  "requestTimeoutSeconds": 20,
                  "version": %d
                }
                """.formatted(CREDENTIAL, version);
    }

    private void insertMember(UUID accountId, String username, String role) {
        jdbc.update("""
                INSERT INTO account(id, username, username_normalized, password_hash, display_name)
                VALUES (?, ?, ?, '{bcrypt}test', ?)
                """, accountId, username, username, username);
        jdbc.update("""
                INSERT INTO member(id, household_id, account_id, role, status)
                VALUES (?, ?, ?, ?, 'ACTIVE')
                """, UUID.randomUUID(), HOUSEHOLD_ID, accountId, role);
    }

    private ZijaPrincipal owner() {
        return new ZijaPrincipal(OWNER_ACCOUNT_ID, "owner", "所有者", "{bcrypt}test", true);
    }

    private ZijaPrincipal member() {
        return new ZijaPrincipal(MEMBER_ACCOUNT_ID, "member", "成员", "{bcrypt}test", true);
    }

    private RequestPostProcessor auth(ZijaPrincipal principal) {
        return authentication(new UsernamePasswordAuthenticationToken(
                principal, principal.getPassword(), principal.getAuthorities()));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeProviderConfiguration {

        @Bean
        BlockingAiModelProvider blockingAiModelProvider() {
            return new BlockingAiModelProvider();
        }

        @Bean
        AiModelProvider deterministicAiModelProvider() {
            return new AiModelProvider() {
                @Override
                public String id() {
                    return "deterministic";
                }

                @Override
                public boolean requiresOutboundAccess() {
                    return false;
                }

                @Override
                public boolean requiresCredential() {
                    return false;
                }

                @Override
                public ProbeResult probe(AiProviderConfiguration configuration) {
                    return ProbeResult.available("fixed-chat", "fixed-embedding");
                }

                @Override
                public AiApi.ChatReply complete(
                        AiApi.ChatRequest request,
                        AiProviderConfiguration configuration
                ) {
                    return new AiApi.ChatReply("fixed:" + request.prompt());
                }

                @Override
                public AiApi.EmbeddingReply embed(
                        AiApi.EmbeddingRequest request,
                        AiProviderConfiguration configuration
                ) {
                    return new AiApi.EmbeddingReply(request.inputs().stream()
                            .map(ignored -> fixedVector())
                            .toList());
                }

                private float[] fixedVector() {
                    var vector = new float[AiService.EMBEDDING_DIMENSIONS];
                    vector[0] = 1.0f;
                    return vector;
                }
            };
        }

        @Bean
        AiModelProvider unavailableAiModelProvider() {
            return new AiModelProvider() {
                @Override
                public String id() {
                    return "unavailable";
                }

                @Override
                public boolean requiresOutboundAccess() {
                    return false;
                }

                @Override
                public boolean requiresCredential() {
                    return false;
                }

                @Override
                public ProbeResult probe(AiProviderConfiguration configuration) {
                    return ProbeResult.unavailable("PROVIDER_UNREACHABLE", "provider is unavailable",
                            "fixed-chat", "fixed-embedding");
                }

                @Override
                public AiApi.ChatReply complete(
                        AiApi.ChatRequest request,
                        AiProviderConfiguration configuration
                ) {
                    throw new IllegalStateException("unavailable");
                }

                @Override
                public AiApi.EmbeddingReply embed(
                        AiApi.EmbeddingRequest request,
                        AiProviderConfiguration configuration
                ) {
                    throw new IllegalStateException("unavailable");
                }
            };
        }

        @Bean
        AiModelProvider invalidDimensionAiModelProvider() {
            return new AiModelProvider() {
                @Override
                public String id() {
                    return "invalid-dimension";
                }

                @Override
                public boolean requiresOutboundAccess() {
                    return false;
                }

                @Override
                public boolean requiresCredential() {
                    return false;
                }

                @Override
                public ProbeResult probe(AiProviderConfiguration configuration) {
                    return ProbeResult.available("fixed-chat", "invalid-embedding");
                }

                @Override
                public AiApi.ChatReply complete(
                        AiApi.ChatRequest request,
                        AiProviderConfiguration configuration
                ) {
                    return new AiApi.ChatReply("fixed:" + request.prompt());
                }

                @Override
                public AiApi.EmbeddingReply embed(
                        AiApi.EmbeddingRequest request,
                        AiProviderConfiguration configuration
                ) {
                    return new AiApi.EmbeddingReply(List.of(new float[]{1.0f, 0.0f}));
                }
            };
        }
    }

    static final class BlockingAiModelProvider implements AiModelProvider {

        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public String id() {
            return "blocking";
        }

        @Override
        public boolean requiresOutboundAccess() {
            return false;
        }

        @Override
        public boolean requiresCredential() {
            return false;
        }

        @Override
        public ProbeResult probe(AiProviderConfiguration configuration) {
            return ProbeResult.available("blocking-chat", "blocking-embedding");
        }

        @Override
        public AiApi.ChatReply complete(AiApi.ChatRequest request, AiProviderConfiguration configuration) {
            for (;;) {
                try {
                    release.await();
                    return new AiApi.ChatReply("released:" + request.prompt());
                } catch (InterruptedException ignored) {
                    // Deliberately keep the provider task alive to verify permit ownership.
                }
            }
        }

        @Override
        public AiApi.EmbeddingReply embed(
                AiApi.EmbeddingRequest request,
                AiProviderConfiguration configuration
        ) {
            return new AiApi.EmbeddingReply(request.inputs().stream()
                    .map(ignored -> new float[AiService.EMBEDDING_DIMENSIONS])
                    .toList());
        }

        void release() {
            release.countDown();
        }
    }
}
