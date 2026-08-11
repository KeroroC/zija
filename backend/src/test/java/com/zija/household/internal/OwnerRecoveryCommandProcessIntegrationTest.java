package com.zija.household.internal;

import com.zija.TestDb;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class OwnerRecoveryCommandProcessIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        TestDb.cleanAll(jdbcTemplate);
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
                    "--spring.datasource.url=" + SharedPostgres.get().getJdbcUrl(),
                    "--spring.datasource.username=" + SharedPostgres.get().getUsername(),
                    "--spring.datasource.password=" + SharedPostgres.get().getPassword(),
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
