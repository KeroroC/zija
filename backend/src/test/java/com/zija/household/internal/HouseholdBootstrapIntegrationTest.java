package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.household.internal.persistence.HouseholdMapper;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class HouseholdBootstrapIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired HouseholdService householdService;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE audit_log, owner_recovery_token, invitation, member, household, account
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void singleBootstrapSucceedsAndConcurrentFails() {
        householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null));

        assertThatThrownBy(() -> householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "第二个", "other", "Passw0rd!", "Other", null)))
                .isInstanceOf(HouseholdAlreadyInitializedException.class);

        assertThat(householdMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper())).isEqualTo(1);
    }

    @Test
    void concurrentBootstrapCreatesExactlyOneCompleteHousehold() throws Exception {
        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var success = new AtomicInteger();
        var conflict = new AtomicInteger();

        Callable<Void> first = bootstrapTask("家庭一", "owner-a", start, ready, success, conflict);
        Callable<Void> second = bootstrapTask("家庭二", "owner-b", start, ready, success, conflict);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var futures = List.of(executor.submit(first), executor.submit(second));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(conflict.get()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM household", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM account", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isEqualTo(1);
    }

    private Callable<Void> bootstrapTask(
            String householdName,
            String username,
            CountDownLatch start,
            CountDownLatch ready,
            AtomicInteger success,
            AtomicInteger conflict
    ) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                householdService.bootstrap(new HouseholdService.BootstrapCommand(
                        householdName, username, "Passw0rd!", username, null));
                success.incrementAndGet();
            } catch (HouseholdAlreadyInitializedException ex) {
                conflict.incrementAndGet();
            }
            return null;
        };
    }
}
