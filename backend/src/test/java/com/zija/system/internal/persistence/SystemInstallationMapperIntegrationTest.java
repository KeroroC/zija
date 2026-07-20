package com.zija.system.internal.persistence;

import com.zija.ZijaSessionInvalidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest
class SystemInstallationMapperIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    private SystemInstallationMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private ZijaSessionInvalidator sessionInvalidator;

    @Test
    void loadsSingletonInstallationAndDatabaseTime() {
        var migrationInstalled = jdbcTemplate.queryForObject(
                """
                SELECT success
                FROM flyway_schema_history
                WHERE version = '1'
                """,
                Boolean.class
        );
        var installation = mapper.selectById((short) 1);

        assertThat(migrationInstalled).isTrue();
        assertThat(installation).isNotNull();
        assertThat(installation.getSingletonKey()).isEqualTo((short) 1);
        assertThat(installation.getInstallationId()).isNotNull();
        assertThat(installation.getCreatedAt()).isNotNull();
        assertThat(mapper.selectDatabaseTime()).isNotNull();
    }
}
