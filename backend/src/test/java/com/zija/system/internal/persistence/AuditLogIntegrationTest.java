package com.zija.system.internal.persistence;

import com.zija.ZijaSessionInvalidator;
import com.zija.system.SystemApi;
import com.zija.system.internal.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class AuditLogIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired
    AuditLogMapper mapper;

    @Autowired
    SystemApi systemApi;

    @Autowired
    PlatformTransactionManager transactionManager;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @Test
    @Transactional
    void recordsAndReadsAuditEvent() {
        var householdId = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var requestId = "req-123";
        var event = new AuditEvent(
                "LOGIN_SUCCESS",
                "SUCCESS",
                householdId,
                actor,
                null,
                requestId,
                "198.51.100.7",
                Map.of("username", "owner")
        );

        mapper.insert(event);
        var rows = mapper.findByActor(actor);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo("LOGIN_SUCCESS");
        assertThat(rows.get(0).getActorAccountId()).isEqualTo(actor);
        assertThat(rows.get(0).getRequestId()).isEqualTo(requestId);
        assertThat(rows.get(0).getDetail()).containsEntry("username", "owner");
    }

    @Test
    @Transactional
    void recordsNestedDetailAsJsonb() {
        var event = new AuditEvent(
                "ROLE_CHANGED", "SUCCESS",
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "req-456", "10.0.0.1",
                Map.of("oldRole", "MEMBER", "newRole", "ADMIN", "nested", Map.of("k", "v"))
        );
        mapper.insert(event);
        var rows = mapper.findByActor(event.actorAccountId());
        assertThat(rows.get(0).getDetail())
                .containsEntry("oldRole", "MEMBER")
                .containsEntry("nested", Map.of("k", "v"));
    }

    @Test
    void successfulAuditRollsBackWithOuterBusinessTransaction() {
        var actor = UUID.randomUUID();
        var event = new SystemApi.AuditEvent(
                "MEMBER_JOINED", "SUCCESS",
                UUID.randomUUID(), actor, actor,
                "req-rollback", "10.0.0.2", null
        );

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            systemApi.recordAudit(event);
            status.setRollbackOnly();
        });

        assertThat(mapper.findByActor(actor)).isEmpty();
    }

    @Test
    void auditWithoutOuterTransactionCommitsThroughSystemApi() {
        var actor = UUID.randomUUID();
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "LOGIN_FAILURE", "FAILURE", null,
                actor, null, "req-standalone", "10.0.0.3",
                Map.of("username", "owner")
        ));

        assertThat(mapper.findByActor(actor))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo("LOGIN_FAILURE");
                    assertThat(row.getRequestId()).isEqualTo("req-standalone");
                });
    }
}
