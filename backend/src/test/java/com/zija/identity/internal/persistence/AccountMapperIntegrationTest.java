package com.zija.identity.internal.persistence;

import com.zija.ZijaSessionInvalidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
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
class AccountMapperIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired
    AccountMapper mapper;

    @MockitoBean
    ZijaSessionInvalidator sessionInvalidator;

    @Test
    @Transactional
    void insertsAndFindsByNormalizedUsername() {
        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("Owner");
        entity.setUsernameNormalized("owner");
        entity.setPasswordHash("{bcrypt}$2a$10$examplehash");
        entity.setDisplayName("所有者");
        entity.setStatus("ACTIVE");

        mapper.insert(entity);
        var found = mapper.selectByNormalizedUsername("owner");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("Owner");
    }

    @Test
    @Transactional
    void rejectsDuplicateNormalizedUsername() {
        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("Alice");
        entity.setUsernameNormalized("alice");
        entity.setPasswordHash("{bcrypt}$2a$10$examplehash");
        entity.setDisplayName("Alice");
        entity.setStatus("ACTIVE");
        mapper.insert(entity);

        var dup = new AccountEntity();
        dup.setId(UUID.randomUUID());
        dup.setUsername("ALICE");
        dup.setUsernameNormalized("alice");
        dup.setPasswordHash("{bcrypt}$2a$10$examplehash");
        dup.setDisplayName("ALICE");
        dup.setStatus("ACTIVE");

        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    @Transactional
    void updatesDisplayNameAndBumpsVersion() {
        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("alice");
        entity.setUsernameNormalized("alice");
        entity.setPasswordHash("{bcrypt}$2a$10$examplehash");
        entity.setDisplayName("Alice");
        entity.setStatus("ACTIVE");
        entity.setVersion(0);
        mapper.insert(entity);

        var affected = mapper.updateDisplayName(entity.getId(), "Alice 2", entity.getVersion());

        assertThat(affected).isEqualTo(1);
        var found = mapper.selectById(entity.getId());
        assertThat(found.getDisplayName()).isEqualTo("Alice 2");
        assertThat(found.getVersion()).isEqualTo(entity.getVersion() + 1);
    }
}
