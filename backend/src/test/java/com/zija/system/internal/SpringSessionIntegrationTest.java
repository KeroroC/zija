package com.zija.system.internal;

import com.zija.ZijaSessionInvalidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class SpringSessionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @Test
    void sessionTablesExist() {
        var count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'spring_session'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
