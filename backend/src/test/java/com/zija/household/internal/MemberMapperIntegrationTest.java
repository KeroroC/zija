package com.zija.household.internal;

import com.zija.ZijaSessionInvalidator;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class MemberMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired HouseholdMapper householdMapper;
    @Autowired MemberMapper memberMapper;
    @Autowired AccountMapper accountMapper;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

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
