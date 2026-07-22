package com.zija.household.internal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class OwnerRecoveryCommandProcessIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE audit_log, owner_recovery_token, invitation, member, household, account,
                    spring_session_attributes, spring_session
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void missingHouseholdExitsWithOneWithoutStartingWebServer() throws Exception {
        var result = runRecoveryCommand();

        assertThat(result.exitCode()).isEqualTo(1);
        assertThat(result.output()).contains("household not initialized");
        assertThat(result.output()).doesNotContain("Tomcat started");
    }

    @Test
    void existingOwnerExitsWithZeroAndPrintsRecoveryLink() throws Exception {
        var householdId = UUID.randomUUID();
        var accountId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO household (singleton_key, id, name, timezone)
                VALUES (1, ?, '恢复家庭', 'Asia/Shanghai')
                """, householdId);
        jdbcTemplate.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, 'owner', 'owner', '{bcrypt}placeholder', '所有者', 'ACTIVE')
                """, accountId);
        jdbcTemplate.update("""
                INSERT INTO member (id, household_id, account_id, role, status)
                VALUES (?, ?, ?, 'OWNER', 'ACTIVE')
                """, UUID.randomUUID(), householdId, accountId);

        var result = runRecoveryCommand();

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).contains("Recovery link: /owner-recovery#token=");
        assertThat(result.output()).contains("Expires at:");
        assertThat(result.output()).doesNotContain("Tomcat started");
    }

    private ProcessResult runRecoveryCommand() throws Exception {
        var output = Files.createTempFile("zija-owner-recovery-", ".log");
        try {
            var process = new ProcessBuilder(
                    System.getProperty("java.home") + "/bin/java",
                    "-cp", System.getProperty("java.class.path"),
                    "com.zija.ZijaApplication",
                    "--spring.main.web-application-type=none",
                    "--zija.command=recover-owner",
                    "--spring.datasource.url=" + postgres.getJdbcUrl(),
                    "--spring.datasource.username=" + postgres.getUsername(),
                    "--spring.datasource.password=" + postgres.getPassword(),
                    "--spring.flyway.enabled=false",
                    "--spring.session.jdbc.initialize-schema=never"
            )
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            assertThat(process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS))
                    .as("owner recovery command must terminate")
                    .isTrue();
            return new ProcessResult(process.exitValue(), Files.readString(output));
        } finally {
            Files.deleteIfExists(output);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }
}
