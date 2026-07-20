package com.zija.identity.internal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
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
class AccountMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    AccountMapper mapper;

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
}
