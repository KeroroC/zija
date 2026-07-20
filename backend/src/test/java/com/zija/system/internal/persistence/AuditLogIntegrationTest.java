package com.zija.system.internal.persistence;

import com.zija.ZijaSessionInvalidator;
import com.zija.system.internal.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class AuditLogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    AuditLogMapper mapper;

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
}
