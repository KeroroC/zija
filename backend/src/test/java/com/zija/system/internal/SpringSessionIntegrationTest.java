package com.zija.system.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.ZijaApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class SpringSessionIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    ZijaSessionInvalidator sessionInvalidator;

    @Autowired
    @SuppressWarnings("rawtypes")
    FindByIndexNameSessionRepository sessionRepository;

    @Test
    void sessionTablesExist() {
        var count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'spring_session'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void persistsAndReadsSessionThroughJdbcRepository() {
        Session session = (Session) sessionRepository.createSession();
        session.setAttribute("test-attribute", "stored-value");

        sessionRepository.save(session);

        Session loaded = (Session) sessionRepository.findById(session.getId());
        assertThat(loaded).isNotNull();
        assertThat((String) loaded.getAttribute("test-attribute")).isEqualTo("stored-value");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void persistedSessionIsReadableFromASecondApplicationContext() {
        Session session = (Session) sessionRepository.createSession();
        session.setAttribute("restart-proof", "available");
        sessionRepository.save(session);

        try (var secondContext = new SpringApplicationBuilder(ZijaApplication.class)
                .web(WebApplicationType.SERVLET)
                .run(
                        "--server.port=0",
                        "--spring.datasource.url=" + SharedPostgres.get().getJdbcUrl(),
                        "--spring.datasource.username=" + SharedPostgres.get().getUsername(),
                        "--spring.datasource.password=" + SharedPostgres.get().getPassword(),
                        "--spring.flyway.enabled=false",
                        "--spring.session.jdbc.initialize-schema=never"
                )) {
            var secondRepository = secondContext.getBean(FindByIndexNameSessionRepository.class);
            Session loaded = (Session) secondRepository.findById(session.getId());
            assertThat(loaded).isNotNull();
            assertThat((String) loaded.getAttribute("restart-proof")).isEqualTo("available");
        }
    }

    @Test
    void principalIndexSupportsAccountSessionInvalidation() {
        var accountId = UUID.randomUUID();
        Session first = (Session) sessionRepository.createSession();
        first.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                accountId.toString());
        Session second = (Session) sessionRepository.createSession();
        second.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
                accountId.toString());
        sessionRepository.save(first);
        sessionRepository.save(second);

        assertThat(sessionRepository.findByPrincipalName(accountId.toString()))
                .containsKeys(first.getId(), second.getId());

        sessionInvalidator.invalidateAllForAccount(accountId);

        assertThat(sessionRepository.findByPrincipalName(accountId.toString())).isEmpty();
        assertThat(sessionRepository.findById(first.getId())).isNull();
        assertThat(sessionRepository.findById(second.getId())).isNull();
    }
}
