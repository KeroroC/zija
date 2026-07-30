package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.exception.InventoryIdempotencyConflictException;
import com.zija.inventory.internal.persistence.IdempotencyRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class IdempotencyServiceIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired HouseholdMapper householdMapper;
    @Autowired IdempotencyRecordMapper mapper;
    @Autowired IdempotencyService svc;
    @Autowired PlatformTransactionManager txManager;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("""
                TRUNCATE TABLE inventory_idempotency_record, household
                RESTART IDENTITY CASCADE
                """);
    }

    @Test
    void firstCallReturnsEmptyThenRecordSuccessMakesSecondHit() {
        var hh = seedHousehold();
        var tx = newTx();

        // First call: lockOrFind returns empty, then recordSuccess
        tx.executeWithoutResult(status -> {
            assertThat(svc.lockOrFind(hh, "k1", "HASH_A")).isEmpty();
            svc.recordSuccess(hh, "k1", "HASH_A", UUID.randomUUID(), java.util.Map.of("ok", true));
        });

        // Second call: lockOrFind returns the recorded hit
        tx.executeWithoutResult(status -> {
            var hit = svc.lockOrFind(hh, "k1", "HASH_A");
            assertThat(hit).isPresent();
            assertThat(hit.get().getRequestHash()).isEqualTo("HASH_A");
        });
    }

    @Test
    void sameKeyDifferentHashThrowsConflict() {
        var hh = seedHousehold();
        var tx = newTx();

        // Record a successful result with HASH_A
        tx.executeWithoutResult(status ->
                svc.recordSuccess(hh, "k2", "HASH_A", UUID.randomUUID(), java.util.Map.of())
        );

        // Try to lockOrFind with same key but different hash — should throw conflict
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(status -> svc.lockOrFind(hh, "k2", "HASH_B"))
        ).isInstanceOf(InventoryIdempotencyConflictException.class);
    }

    @Test
    void rollbackLeavesNoRecord() {
        var hh = seedHousehold();

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(status -> {
                    svc.lockOrFind(hh, "k3", "HASH_C");
                    throw new IllegalStateException("boom");
                })
        ).isInstanceOf(IllegalStateException.class);

        newTx().executeWithoutResult(status ->
                assertThat(mapper.selectList(null).stream()
                        .noneMatch(r -> "k3".equals(r.getIdempotencyKey()))).isTrue()
        );
    }

    // --- Helpers ---

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家");
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private TransactionTemplate newTx() {
        return new TransactionTemplate(txManager);
    }
}
