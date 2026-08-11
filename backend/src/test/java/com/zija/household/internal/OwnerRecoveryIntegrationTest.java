package com.zija.household.internal;

import com.zija.TestDb;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.exception.InvalidInvitationException;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class OwnerRecoveryIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired OwnerRecoveryService ownerRecoveryService;
    @Autowired OwnerRecoveryTokenMapper tokenMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void cleanTables() {
        TestDb.cleanAll(jdbcTemplate);
        reset(sessionInvalidator);
    }

    @Test
    void generateInvalidatesPreviousPendingToken() {
        var householdId = seedHousehold("恢复家庭");
        var ownerAccountId = seedOwner(householdId, "recover-owner", "Passw0rd!");

        var first = ownerRecoveryService.generate(householdId, ownerAccountId);
        var second = ownerRecoveryService.generate(householdId, ownerAccountId);

        assertThatThrownBy(() -> ownerRecoveryService.resetPassword(first.rawToken(), "N3wPassw0rd!"))
                .isInstanceOf(InvalidInvitationException.class);

        ownerRecoveryService.resetPassword(second.rawToken(), "N3wPassw0rd!");
        var account = accountMapper.selectById(ownerAccountId);
        assertThat(passwordEncoder.matches("N3wPassw0rd!", account.getPasswordHash())).isTrue();
        // IdentityService.resetPassword invalidates sessions once on password change.
        verify(sessionInvalidator, times(1)).invalidateAllForAccount(ownerAccountId);
    }

    @Test
    void concurrentResetAllowsExactlyOneSuccess() throws Exception {
        var householdId = seedHousehold("并发恢复家庭");
        var ownerAccountId = seedOwner(householdId, "recover-race", "Passw0rd!");
        var generated = ownerRecoveryService.generate(householdId, ownerAccountId);

        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var success = new AtomicInteger();
        var failure = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Void>> tasks = List.of(
                    resetTask(generated.rawToken(), "RacePass1!", start, ready, success, failure),
                    resetTask(generated.rawToken(), "RacePass2!", start, ready, success, failure)
            );
            List<Future<Void>> futures = new ArrayList<>();
            for (var task : tasks) {
                futures.add(executor.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (var future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        }

        assertThat(success.get()).isEqualTo(1);
        assertThat(failure.get()).isEqualTo(1);

        var token = tokenMapper.selectByDigestForUpdate(
                InvitationService.sha256Hex(generated.rawToken())
        ).orElseThrow();
        assertThat(token.getConsumedAt()).isNotNull();
        verify(sessionInvalidator, times(1)).invalidateAllForAccount(ownerAccountId);
    }

    private Callable<Void> resetTask(
            String token,
            String password,
            CountDownLatch start,
            CountDownLatch ready,
            AtomicInteger success,
            AtomicInteger failure
    ) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                ownerRecoveryService.resetPassword(token, password);
                success.incrementAndGet();
            } catch (RuntimeException ex) {
                failure.incrementAndGet();
            }
            return null;
        };
    }

    private UUID seedHousehold(String name) {
        var household = new HouseholdEntity();
        household.setSingletonKey((short) 1);
        household.setId(UUID.randomUUID());
        household.setName(name);
        household.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(household);
        return household.getId();
    }

    private UUID seedOwner(UUID householdId, String username, String password) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername(username);
        account.setUsernameNormalized(username.toLowerCase());
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setDisplayName(username);
        account.setStatus("ACTIVE");
        accountMapper.insert(account);

        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(account.getId());
        member.setRole("OWNER");
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
        return account.getId();
    }
}
