package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.exception.InventoryMovementAlreadyReversedException;
import com.zija.inventory.internal.exception.InventoryReversalNotAllowedException;
import com.zija.inventory.internal.exception.InventoryReversalWouldNegativeException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class ReversalServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired StockPositionMapper stockPositionMapper;
    @Autowired MovementMapper movementMapper;
    @Autowired StockCommandService stockCommandService;
    @Autowired ReversalService reversalService;
    @Autowired PlatformTransactionManager txManager;

    private UUID householdId;
    private UUID itemId;
    private UUID unitId;
    private UUID locationId;
    private UUID accountId;

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
        accountId = seedAccount();
    }

    // --- Test point 1: CONSUME reversal ---
    // Stock position restored, new REVERSAL movement, original movement still exists;
    // countReversalOf becomes 1
    @Test
    void reverse_consumeMovement_restoresStockAndCreatesReversalMovement() {
        UUID lotId = seedLot(householdId, itemId);

        // Inbound 10 units
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, accountId,
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Consume 6 units (stock: 10 -> 4)
        var consumeResult = newTx().execute(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(6), "领用", null, null));
        UUID originalMovementId = consumeResult.movementId();

        // Verify stock is 4 before reversal
        var spBefore = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(spBefore.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));

        // Reverse the CONSUME movement
        var reversalResult = newTx().execute(s ->
                reversalService.reverse(householdId, accountId, originalMovementId,
                        "冲正", "测试冲正", null));

        // Verify reversal result
        assertThat(reversalResult).isNotNull();
        assertThat(reversalResult.reversalMovementId()).isNotNull();
        assertThat(reversalResult.lotId()).isEqualTo(lotId);

        // Verify stock position restored: 4 + 6 = 10
        var spAfter = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(spAfter).isNotNull();
        assertThat(spAfter.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);

        // Verify REVERSAL movement created
        var reversalMv = movementMapper.selectById(reversalResult.reversalMovementId());
        assertThat(reversalMv).isNotNull();
        assertThat(reversalMv.getType()).isEqualTo("REVERSAL");
        assertThat(reversalMv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(6));
        assertThat(reversalMv.getReversalOf()).isEqualTo(originalMovementId);
        assertThat(reversalMv.getLotId()).isEqualTo(lotId);
        assertThat(reversalMv.getReason()).isEqualTo("冲正");
        assertThat(reversalMv.getMemo()).isEqualTo("测试冲正");
        // CONSUME reversal: stock returns to from_location
        assertThat(reversalMv.getToLocationId()).isEqualTo(locationId);
        assertThat(reversalMv.getFromLocationId()).isNull();

        // Verify original movement still exists and is unchanged
        var originalMv = movementMapper.selectById(originalMovementId);
        assertThat(originalMv).isNotNull();
        assertThat(originalMv.getType()).isEqualTo("CONSUME");
        assertThat(originalMv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(6));

        // Verify countReversalOf = 1
        assertThat(movementMapper.countReversalOf(householdId, originalMovementId)).isEqualTo(1);

        // Verify total movements: INBOUND + CONSUME + REVERSAL = 3
        assertThat(movementMapper.selectList(null)).hasSize(3);
    }

    // --- Test point 2: Already reversed movement ---
    // → INVENTORY_MOVEMENT_ALREADY_REVERSED
    @Test
    void reverse_alreadyReversed_throwsAlreadyReversedException() {
        UUID lotId = seedLot(householdId, itemId);

        // Inbound 10 units, then consume 4 units
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, accountId,
                        locationId, lotId, BigDecimal.TEN, null, null));
        var consumeResult = newTx().execute(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(4), "领用", null, null));
        UUID originalMovementId = consumeResult.movementId();

        // First reversal succeeds
        newTx().execute(s ->
                reversalService.reverse(householdId, accountId, originalMovementId,
                        "冲正", null, null));

        // Second reversal of same movement → throws
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        reversalService.reverse(householdId, accountId, originalMovementId,
                                "再次冲正", null, null))
        ).isInstanceOf(InventoryMovementAlreadyReversedException.class);

        // Verify only one REVERSAL movement exists
        long reversalCount = movementMapper.selectList(null).stream()
                .filter(m -> m.getType().equals("REVERSAL"))
                .count();
        assertThat(reversalCount).isEqualTo(1);
    }

    // --- Test point 3: Reversal would cause negative stock ---
    // → INVENTORY_REVERSAL_WOULD_NEGATIVE, state unchanged
    // Note: CONSUME/LOSS reversal only adds stock (can't go negative).
    // INBOUND reversal subtracts from stock, so we test that path.
    @Test
    void reverse_wouldCauseNegativeStock_throwsWouldNegativeException() {
        UUID lotId = seedLot(householdId, itemId);

        // Inbound 10 units (stock: 10)
        var inboundResult = newTx().execute(s ->
                stockCommandService.inboundExistingLot(householdId, accountId,
                        locationId, lotId, BigDecimal.TEN, null, null));
        UUID inboundMovementId = inboundResult.movementId();

        // Consume 10 units (stock: 10 -> 0)
        newTx().executeWithoutResult(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.TEN, "全部领用", null, null));

        // Try to reverse the INBOUND (would subtract 10 from stock=0)
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        reversalService.reverse(householdId, accountId, inboundMovementId,
                                "冲正", null, null))
        ).isInstanceOf(InventoryReversalWouldNegativeException.class);

        // Stock position unchanged: 0
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        // No REVERSAL movement created
        long reversalCount = movementMapper.selectList(null).stream()
                .filter(m -> m.getType().equals("REVERSAL"))
                .count();
        assertThat(reversalCount).isEqualTo(0);

        // Original INBOUND movement still exists
        var originalMv = movementMapper.selectById(inboundMovementId);
        assertThat(originalMv).isNotNull();
        assertThat(originalMv.getType()).isEqualTo("INBOUND");

        // Total movements: INBOUND + CONSUME = 2
        assertThat(movementMapper.selectList(null)).hasSize(2);
    }

    // --- Test point 4: REVERSAL movement itself cannot be reversed ---
    // → INVENTORY_REVERSAL_NOT_ALLOWED
    @Test
    void reverse_reversalMovement_throwsReversalNotAllowedException() {
        UUID lotId = seedLot(householdId, itemId);

        // Inbound 10, consume 4, reverse the consume
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, accountId,
                        locationId, lotId, BigDecimal.TEN, null, null));
        var consumeResult = newTx().execute(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(4), "领用", null, null));
        var reversalResult = newTx().execute(s ->
                reversalService.reverse(householdId, accountId, consumeResult.movementId(),
                        "冲正", null, null));
        UUID reversalMovementId = reversalResult.reversalMovementId();

        // Try to reverse the REVERSAL movement → throws
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        reversalService.reverse(householdId, accountId, reversalMovementId,
                                "再次冲正", null, null))
        ).isInstanceOf(InventoryReversalNotAllowedException.class);

        // Total movements: INBOUND + CONSUME + REVERSAL = 3 (no new movement)
        assertThat(movementMapper.selectList(null)).hasSize(3);
    }

    // --- Test point 5: Original movement not found ---
    // → INVENTORY_REVERSAL_NOT_ALLOWED
    @Test
    void reverse_movementNotFound_throwsReversalNotAllowedException() {
        UUID nonExistentMovementId = UUID.randomUUID();

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        reversalService.reverse(householdId, accountId, nonExistentMovementId,
                                "冲正", null, null))
        ).isInstanceOf(InventoryReversalNotAllowedException.class);

        // No movements created
        assertThat(movementMapper.selectList(null)).isEmpty();
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
