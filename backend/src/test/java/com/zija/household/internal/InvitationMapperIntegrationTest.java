package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
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

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class InvitationMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired InvitationService invitationService;
    @Autowired InvitationMapper invitationMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired IdentityApi identityApi;
    @Autowired MemberService memberService;
    @Autowired JdbcTemplate jdbcTemplate;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE owner_recovery_token, invitation, member, household, account
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void concurrentRedeemAllowsExactlyOneSuccess() throws Exception {
        var householdId = seedHousehold("邀请家庭");
        var ownerAccountId = seedAccount("invite-owner", "邀请所有者");
        seedMember(householdId, ownerAccountId, "OWNER");

        var created = invitationService.create(
                householdId,
                ownerAccountId,
                HouseholdApi.MemberRole.MEMBER,
                24
        );

        var start = new CountDownLatch(1);
        var ready = new CountDownLatch(2);
        var success = new AtomicInteger();
        var failure = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Callable<Void>> tasks = List.of(
                    redeemTask(created.rawToken(), "member-a", start, ready, success, failure),
                    redeemTask(created.rawToken(), "member-b", start, ready, success, failure)
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

        var invitation = invitationMapper.selectByDigestForUpdate(created.digest()).orElseThrow();
        assertThat(invitation.getConsumedAt()).isNotNull();
        assertThat(invitation.getConsumedBy()).isNotNull();
        assertThat(memberMapper.selectByHousehold(householdId)).hasSize(2);
    }

    private Callable<Void> redeemTask(
            String token,
            String username,
            CountDownLatch start,
            CountDownLatch ready,
            AtomicInteger success,
            AtomicInteger failure
    ) {
        return () -> {
            ready.countDown();
            start.await(10, TimeUnit.SECONDS);
            try {
                invitationService.redeem(
                        token,
                        new InvitationService.RedeemCommand(username, "Passw0rd!", username, null),
                        identityApi,
                        memberService
                );
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

    private UUID seedAccount(String username, String displayName) {
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername(username);
        account.setUsernameNormalized(username.toLowerCase());
        account.setPasswordHash("{bcrypt}$2a$10$examplehash");
        account.setDisplayName(displayName);
        account.setStatus("ACTIVE");
        accountMapper.insert(account);
        return account.getId();
    }

    private void seedMember(UUID householdId, UUID accountId, String role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
    }
}
