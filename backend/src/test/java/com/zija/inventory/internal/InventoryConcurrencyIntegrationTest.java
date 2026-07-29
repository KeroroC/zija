package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.exception.InventoryIdempotencyConflictException;
import com.zija.inventory.internal.exception.InventoryInsufficientStockException;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发场景 Testcontainers 集成测试。
 * <p>
 * 使用 {@code CountDownLatch} + {@code ExecutorService} 模拟并发访问，
 * 验证：不出现负库存、不产生重复流水、移位原子性、幂等键唯一性。
 */
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class InventoryConcurrencyIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired StockPositionMapper stockPositionMapper;
    @Autowired MovementMapper movementMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired ConsistencyCheckService consistencyCheckService;
    @Autowired ReversalService reversalService;
    @Autowired PlatformTransactionManager txManager;

    private UUID householdId;
    private UUID itemId;
    private UUID unitId;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                TRUNCATE TABLE inventory_movement, inventory_stock_position,
                             inventory_lot, audit_log, catalog_item, catalog_unit,
                             location, household, account
                RESTART IDENTITY CASCADE
                """);

        householdId = seedHousehold();
        unitId = seedUnit(householdId);
        itemId = seedItem(householdId, unitId);
        locationId = seedLocation(householdId);
    }

    // =====================================================================
    // Test 1: Two consumers over-consume same stock position
    // =====================================================================

    @Test
    void concurrentConsume_overConsume_onlyOneSucceeds() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        UUID accountId = seedAccount();

        // Seed: inbound quantity=2
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TWO, null, null));

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        // Two threads each try to consume 2
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                latch.await();
                try {
                    newTx().executeWithoutResult(s ->
                            stockCommandService.consume(householdId, accountId, lotId,
                                    locationId, BigDecimal.TWO, null, null, null));
                    return "SUCCESS";
                } catch (InventoryInsufficientStockException ex) {
                    return "INSUFFICIENT";
                }
            });
        }

        latch.countDown();
        List<Future<String>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        long successCount = futures.stream().map(f -> {
            try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        }).filter("SUCCESS"::equals).count();

        // Exactly one succeeds, one gets INSUFFICIENT
        assertThat(successCount).isEqualTo(1);

        // Final: quantity=0
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        // Exactly 1 CONSUME movement (+ 1 initial INBOUND = 2 total)
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(2);
        long consumeCount = movements.stream()
                .filter(m -> "CONSUME".equals(m.getType()))
                .count();
        assertThat(consumeCount).isEqualTo(1);

        // Stock never goes negative (CHECK constraint enforces this; if it did, transaction would fail)
        // Additional explicit check:
        BigDecimal qty = stockPositionMapper.lockOne(householdId, lotId, locationId).getQuantity();
        assertThat(qty.signum()).isGreaterThanOrEqualTo(0);
    }

    // =====================================================================
    // Test 2: Transfer atomicity — one side cannot commit
    // =====================================================================

    @Test
    void concurrentTransfer_atomicity_sourceTargetConserved() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        UUID accountId = seedAccount();
        UUID locA = seedLocation(householdId);
        UUID locB = seedLocation(householdId);

        // Seed: source quantity=1
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.ONE, null, null));

        // First: transfer 1 from locationId to locA — succeeds, source=0, locA=1
        newTx().executeWithoutResult(s ->
                stockCommandService.transfer(householdId, accountId, lotId,
                        locationId, locA, BigDecimal.ONE, null, null));

        var sourceAfterFirst = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sourceAfterFirst.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        var targetA = stockPositionMapper.lockOne(householdId, lotId, locA);
        assertThat(targetA.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);

        // Concurrent: two transfers of 1 each from locA → locB
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                latch.await();
                try {
                    newTx().executeWithoutResult(s ->
                            stockCommandService.transfer(householdId, accountId, lotId,
                                    locA, locB, BigDecimal.ONE, null, null));
                    return "SUCCESS";
                } catch (InventoryInsufficientStockException ex) {
                    return "INSUFFICIENT";
                }
            });
        }

        latch.countDown();
        List<Future<String>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        long successCount = futures.stream().map(f -> {
            try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        }).filter("SUCCESS"::equals).count();

        // Exactly one succeeds, one gets INSUFFICIENT
        assertThat(successCount).isEqualTo(1);

        // Source/target state is conserved: locA=0, locB=1, locationId=0
        var locASp = stockPositionMapper.lockOne(householdId, lotId, locA);
        var locBSp = stockPositionMapper.lockOne(householdId, lotId, locB);
        var locOrigSp = stockPositionMapper.lockOne(householdId, lotId, locationId);

        assertThat(locASp).isNotNull();
        assertThat(locASp.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(locBSp).isNotNull();
        assertThat(locBSp.getQuantity()).isEqualByComparingTo(BigDecimal.ONE);
        assertThat(locOrigSp).isNotNull();
        assertThat(locOrigSp.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        // Total quantity conserved: 0 + 1 + 0 = 1
        BigDecimal total = locASp.getQuantity()
                .add(locBSp.getQuantity())
                .add(locOrigSp.getQuantity());
        assertThat(total).isEqualByComparingTo(BigDecimal.ONE);
    }

    // =====================================================================
    // Test 3: Same idempotency key produces only one movement
    // =====================================================================

    @Test
    void concurrentInbound_sameIdempotencyKey_onlyOneMovement() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        UUID accountId = seedAccount();
        String idempotencyKey = UUID.randomUUID().toString();

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                latch.await();
                try {
                    newTx().executeWithoutResult(s ->
                            stockCommandService.inboundExistingLot(householdId, accountId,
                                    locationId, lotId, BigDecimal.TEN, null, idempotencyKey));
                    return "SUCCESS";
                } catch (InventoryIdempotencyConflictException ex) {
                    return "CONFLICT";
                }
            });
        }

        latch.countDown();
        List<Future<String>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<String> results = futures.stream().map(f -> {
            try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
        }).toList();

        // Both should succeed (replay) — no CONFLICT for same hash
        assertThat(results).allMatch(r -> "SUCCESS".equals(r));

        // Exactly 1 INBOUND movement for this idempotency key
        Long movementCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movement WHERE household_id = ? AND idempotency_key = ?",
                Long.class, householdId, idempotencyKey);
        assertThat(movementCount).isEqualTo(1);

        // Stock position reflects single inbound of 10
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
    }

    // =====================================================================
    // Test 4: Two concurrent reverses of the same movement with the same
    // Idempotency-Key must produce only one REVERSAL row and return the
    // same reversalMovementId from both callers (replay semantics).
    // =====================================================================

    @Test
    void concurrentReverse_sameIdempotencyKey_onlyOneReversal() throws Exception {
        UUID lotId = seedLot(householdId, itemId);
        UUID accountId = seedAccount();

        // Seed: inbound 10, consume 6 → stock = 4
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, accountId,
                        locationId, lotId, BigDecimal.TEN, null, null));
        var consumeResult = newTx().execute(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(6), "领用", null, null));
        UUID originalMovementId = consumeResult.movementId();

        var spBefore = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(spBefore.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));

        // Two concurrent reverses, same key, same target
        String idempotencyKey = UUID.randomUUID().toString();
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        List<Callable<ReversalService.ReversalResult>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                latch.await();
                return newTx().execute(s ->
                        reversalService.reverse(householdId, accountId, originalMovementId,
                                "冲正", "memo", idempotencyKey));
            });
        }

        latch.countDown();
        List<Future<ReversalService.ReversalResult>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        // Both calls succeed
        List<ReversalService.ReversalResult> results = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { throw new RuntimeException(e); }
                })
                .toList();
        assertThat(results).hasSize(2);
        assertThat(results.get(0).reversalMovementId()).isNotNull();
        assertThat(results.get(1).reversalMovementId()).isNotNull();

        // Replay: both callers see the SAME reversalMovementId
        assertThat(results.get(1).reversalMovementId())
                .isEqualTo(results.get(0).reversalMovementId());
        assertThat(results.get(1).lotId())
                .isEqualTo(results.get(0).lotId());

        // Exactly 1 REVERSAL row (no double reversal)
        Long reversalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_movement " +
                "WHERE household_id = ? AND type = 'REVERSAL' AND reversal_of = ?",
                Long.class, householdId, originalMovementId);
        assertThat(reversalCount).isEqualTo(1);

        // Stock position: 4 + 6 = 10 (NOT 16, which would indicate double-reversal)
        var spAfter = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(spAfter).isNotNull();
        assertThat(spAfter.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);

        // Total movements: INBOUND + CONSUME + REVERSAL = 3
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(3);

        // Exactly 1 idempotency record for this key
        Long idemCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_idempotency_record " +
                "WHERE household_id = ? AND idempotency_key = ?",
                Long.class, householdId, idempotencyKey);
        assertThat(idemCount).isEqualTo(1);
    }

    // =====================================================================
    // Test 4: Movements are immutable / rebuildable
    // =====================================================================

    @Test
    void movementsAreRebuildable_afterInboundConsumeLoss_consistencyCheckClean() {
        UUID lotId = seedLot(householdId, itemId);
        UUID accountId = seedAccount();

        // Inbound 5
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.valueOf(5), null, null));

        // Consume 2
        newTx().executeWithoutResult(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.TWO, null, null, null));

        // Loss 1
        newTx().executeWithoutResult(s ->
                stockCommandService.loss(householdId, accountId, lotId, locationId,
                        BigDecimal.ONE, "过期", null, null));

        // Stock position: 5 - 2 - 1 = 2
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(2));

        // Each stock position quantity equals expectedFromMovements
        var discrepancies = newTx().execute(s ->
                consistencyCheckService.check(householdId, null));
        assertThat(discrepancies).isEmpty();

        // 3 movements total (INBOUND, CONSUME, LOSS)
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(3);
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private UUID seedHousehold() {
        var h = new HouseholdEntity();
        h.setSingletonKey((short) 1);
        h.setId(UUID.randomUUID());
        h.setName("测试家");
        h.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(h);
        return h.getId();
    }

    private UUID seedUnit(UUID householdId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_unit (id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, "个" + id.toString().substring(0, 6), "个" + id.toString().substring(0, 6));
        return id;
    }

    private UUID seedItem(UUID householdId, UUID unitId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id, status)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ACTIVE')
                """, id, householdId, "物品" + id.toString().substring(0, 6), unitId);
        return id;
    }

    private UUID seedAccount() {
        UUID id = UUID.randomUUID();
        String username = "user" + id.toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name)
                VALUES (?, ?, ?, 'hash', '测试用户')
                """, id, username, username);
        return id;
    }

    private UUID seedLocation(UUID householdId) {
        UUID id = UUID.randomUUID();
        String name = "位置" + id.toString().substring(0, 6);
        jdbc.update("""
                INSERT INTO location (id, household_id, name, name_normalized, sort_order, ever_referenced, version)
                VALUES (?, ?, ?, ?, 0, false, 0)
                """, id, householdId, name, name);
        return id;
    }

    private UUID seedLot(UUID householdId, UUID itemId) {
        UUID id = UUID.randomUUID();
        String lotNumber = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"))
                + String.format("%03d", (int) (Math.random() * 900) + 100);
        jdbc.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, lot_number, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, householdId, itemId, lotNumber);
        return id;
    }

    private TransactionTemplate newTx() {
        return new TransactionTemplate(txManager);
    }
}
