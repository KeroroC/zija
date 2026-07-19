package com.zija.system.internal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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

    @Test
    void loadsSingletonInstallationAndDatabaseTime() {
        var installation = mapper.selectById((short) 1);

        assertThat(installation).isNotNull();
        assertThat(installation.getInstallationId()).isNotNull();
        assertThat(installation.getCreatedAt()).isNotNull();
        assertThat(mapper.selectDatabaseTime()).isNotNull();
    }
}
