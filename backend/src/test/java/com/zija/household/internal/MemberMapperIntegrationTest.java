package com.zija.household.internal;

import com.zija.TestDb;
import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class MemberMapperIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired AccountMapper accountMapper;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @BeforeEach
    void cleanTables() {
        TestDb.cleanAll(jdbc);
    }

    @Test
    @Transactional
    void uniqueOwnerIndexPreventsSecondOwner() {
        var householdId = seedHousehold("唯一所有者家庭");
        var ownerAccount = seedAccount("owner1", "所有者一");
        var otherAccount = seedAccount("owner2", "所有者二");

        memberMapper.insert(member(householdId, ownerAccount, "OWNER"));
        assertThat(memberMapper.selectOwner(householdId)).isPresent();
        assertThat(memberMapper.selectByHousehold(householdId)).hasSize(1);

        assertThatThrownBy(() -> memberMapper.insert(member(householdId, otherAccount, "OWNER")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void rejectsDuplicateHouseholdAccountPair() {
        var householdId = seedHousehold("成员唯一家庭");
        var accountId = seedAccount("alice", "Alice");
        memberMapper.insert(member(householdId, accountId, "MEMBER"));

        assertThatThrownBy(() -> memberMapper.insert(member(householdId, accountId, "ADMIN")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Transactional
    void optimisticLockRejectsStaleVersionOnRoleUpdate() {
        var householdId = seedHousehold("乐观锁家庭");
        var accountId = seedAccount("bob", "Bob");
        var entity = member(householdId, accountId, "MEMBER");
        memberMapper.insert(entity);

        var stored = memberMapper.selectById(entity.getId());
        assertThat(stored.getVersion()).isEqualTo(0);

        assertThat(memberMapper.updateRole(entity.getId(), "ADMIN", 0)).isEqualTo(1);
        assertThat(memberMapper.updateRole(entity.getId(), "MEMBER", 0)).isEqualTo(0);
        assertThat(memberMapper.selectById(entity.getId()).getRole()).isEqualTo("ADMIN");
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

    private MemberEntity member(UUID householdId, UUID accountId, String role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role);
        member.setStatus("ACTIVE");
        return member;
    }
}
