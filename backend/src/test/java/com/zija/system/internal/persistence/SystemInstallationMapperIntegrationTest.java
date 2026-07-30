package com.zija.system.internal.persistence;

import com.zija.ZijaSessionInvalidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
class SystemInstallationMapperIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

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
