package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
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
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class StockCommandServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired StockPositionMapper stockPositionMapper;
    @Autowired MovementMapper movementMapper;
    @Autowired StockCommandService stockCommandService;
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

    // --- Test point 1: inbound new lot — stock position correct, movement created ---
    @Test
    void inboundNewLot_createsStockPositionAndMovement() {
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, BigDecimal.TEN, LocalDate.now(), null, null,
                "LOT-001", null, null, null);

        var result = newTx().execute(s ->
                stockCommandService.inboundNewLot(householdId, UUID.randomUUID(), locationId, cmd));

        assertThat(result).isNotNull();
        assertThat(result.lotId()).isNotNull();
        assertThat(result.locationId()).isEqualTo(locationId);
        assertThat(result.movementId()).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.TEN);

        // Verify stock position
        var sp = stockPositionMapper.lockOne(householdId, result.lotId(), locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(sp.getRevision()).isEqualTo(1L);

        // Verify INBOUND movement
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        var mv = movements.get(0);
        assertThat(mv.getType()).isEqualTo("INBOUND");
        assertThat(mv.getToLocationId()).isEqualTo(locationId);
        assertThat(mv.getFromLocationId()).isNull();
        assertThat(mv.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(mv.getLotId()).isEqualTo(result.lotId());
    }

    // --- Test point 2: archived item inbound → exception, no stock position, no movement ---
    @Test
    void inboundNewLot_archivedItem_throwsArchivedItemException() {
        UUID archivedItemId = seedArchivedItem(householdId, unitId);

        var cmd = new StockCommandService.InboundNewLotCommand(
                archivedItemId, BigDecimal.TEN, LocalDate.now(), null, null,
                "LOT-ARC", null, null, null);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stockCommandService.inboundNewLot(householdId, UUID.randomUUID(), locationId, cmd))
        ).isInstanceOf(InventoryArchivedItemException.class);

        // No stock position created
        assertThat(stockPositionMapper.selectList(null)).isEmpty();
        // No movement created
        assertThat(movementMapper.selectList(null)).isEmpty();
    }

    // --- Test point 3: LocationApi.markReferenced is called (verify via DB) ---
    @Test
    void inboundNewLot_marksLocationReferenced() {
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, BigDecimal.ONE, LocalDate.now(), null, null,
                "LOT-REF", null, null, null);

        newTx().executeWithoutResult(s ->
                stockCommandService.inboundNewLot(householdId, UUID.randomUUID(), locationId, cmd));

        // Verify location was marked as referenced via direct DB query
        Boolean everReferenced = jdbc.queryForObject(
                "SELECT ever_referenced FROM location WHERE id = ?",
                Boolean.class, locationId);
        assertThat(everReferenced).isTrue();
    }

    // --- Test point 1 (existing lot): inbound to existing lot accumulates quantity, revision+1 ---
    @Test
    void inboundExistingLot_accumulatesQuantityAndIncrementsRevision() {
        UUID lotId = seedLot(householdId, itemId);

        // First inbound
        var result1 = newTx().execute(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        assertThat(result1).isNotNull();
        assertThat(result1.lotId()).isEqualTo(lotId);
        assertThat(result1.quantityAfter()).isEqualByComparingTo(BigDecimal.TEN);

        // Second inbound to same lot
        var result2 = newTx().execute(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.valueOf(5), null, null));

        assertThat(result2.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(5));

        // Verify stock position accumulated
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(sp.getRevision()).isEqualTo(2L);

        // Verify two INBOUND movements
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(2);
        assertThat(movements).allMatch(m -> m.getType().equals("INBOUND"));
        assertThat(movements).allMatch(m -> m.getLotId().equals(lotId));
    }

    // --- Test point 2 (existing lot): archived item inbound to existing lot is rejected ---
    @Test
    void inboundExistingLot_archivedItem_throwsArchivedItemException() {
        UUID archivedItemId = seedArchivedItem(householdId, unitId);
        UUID lotId = seedLot(householdId, archivedItemId);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                                locationId, lotId, BigDecimal.TEN, null, null))
        ).isInstanceOf(InventoryArchivedItemException.class);

        // No stock position created
        assertThat(stockPositionMapper.selectList(null)).isEmpty();
        // No movement created
        assertThat(movementMapper.selectList(null)).isEmpty();
    }

    // --- Test point 4: repeated inbound to new lots yields two different lots and stock positions ---
    @Test
    void inboundNewLot_twice_sameItem_twoDifferentLotsAndStockPositions() {
        var cmd1 = new StockCommandService.InboundNewLotCommand(
                itemId, BigDecimal.TEN, LocalDate.now(), null, null,
                "LOT-A", null, null, null);
        var cmd2 = new StockCommandService.InboundNewLotCommand(
                itemId, BigDecimal.valueOf(5), LocalDate.now(), null, null,
                "LOT-B", null, null, null);

        var result1 = newTx().execute(s ->
                stockCommandService.inboundNewLot(householdId, UUID.randomUUID(), locationId, cmd1));
        var result2 = newTx().execute(s ->
                stockCommandService.inboundNewLot(householdId, UUID.randomUUID(), locationId, cmd2));

        // Two different lots
        assertThat(result1.lotId()).isNotEqualTo(result2.lotId());

        // Two different stock positions
        var sp1 = stockPositionMapper.lockOne(householdId, result1.lotId(), locationId);
        var sp2 = stockPositionMapper.lockOne(householdId, result2.lotId(), locationId);
        assertThat(sp1).isNotNull();
        assertThat(sp2).isNotNull();
        assertThat(sp1.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(sp2.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(sp1.getId()).isNotEqualTo(sp2.getId());

        // Two movements
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(2);
    }

    // --- Test point 1 (consume): consume deducts stock, revision+1; deduction to 0 allowed ---
    @Test
    void consume_deductsStockAndIncrementsRevision() {
        UUID lotId = seedLot(householdId, itemId);

        // First: inbound some stock
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Consume 4 units
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(4), "日常领用", null, null));

        assertThat(result).isNotNull();
        assertThat(result.lotId()).isEqualTo(lotId);
        assertThat(result.locationId()).isEqualTo(locationId);
        assertThat(result.movementId()).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(6));

        // Verify stock position: 10 - 4 = 6, revision = 2
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(6));
        assertThat(sp.getRevision()).isEqualTo(2L);

        // Verify CONSUME movement
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(2); // INBOUND + CONSUME
        var consumeMv = movements.stream()
                .filter(m -> m.getType().equals("CONSUME"))
                .findFirst().orElseThrow();
        assertThat(consumeMv.getFromLocationId()).isEqualTo(locationId);
        assertThat(consumeMv.getToLocationId()).isNull();
        assertThat(consumeMv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(consumeMv.getLotId()).isEqualTo(lotId);
    }

    // --- Test point 2 (consume): over-consume throws INVENTORY_INSUFFICIENT_STOCK, no movement, stock unchanged ---
    @Test
    void consume_overConsume_throwsInsufficientStockException() {
        UUID lotId = seedLot(householdId, itemId);

        // Inbound 3 units
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.valueOf(3), null, null));

        // Try to consume 5 units (more than available)
        UUID accountId = seedAccount();
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stockCommandService.consume(householdId, accountId, lotId, locationId,
                                BigDecimal.valueOf(5), "超额领用", null, null))
        ).isInstanceOf(InventoryInsufficientStockException.class);

        // No CONSUME movement created (only the initial INBOUND)
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo("INBOUND");

        // Stock position unchanged
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(sp.getRevision()).isEqualTo(1L);
    }

    // --- Test point 3 (consume): archived items can be consumed (uses requireItem, not requireActiveItem) ---
    @Test
    void consume_archivedItem_succeeds() {
        UUID archivedItemId = seedArchivedItem(householdId, unitId);
        UUID lotId = seedLot(householdId, archivedItemId);

        // Seed stock position directly (can't use inboundExistingLot since it rejects archived items)
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        // Consume from archived item should succeed
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.consume(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(3), "归档物品领用", null, null));

        assertThat(result).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(7));

        // Verify CONSUME movement exists
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo("CONSUME");
    }

    // --- Test point 1 (loss): loss deducts stock, revision+1 ---
    @Test
    void loss_deductsStockAndIncrementsRevision() {
        UUID lotId = seedLot(householdId, itemId);

        // First: inbound some stock
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Lose 4 units
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.loss(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(4), "过期报废", null, null));

        assertThat(result).isNotNull();
        assertThat(result.lotId()).isEqualTo(lotId);
        assertThat(result.locationId()).isEqualTo(locationId);
        assertThat(result.movementId()).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(6));

        // Verify stock position: 10 - 4 = 6, revision = 2
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(6));
        assertThat(sp.getRevision()).isEqualTo(2L);

        // Verify LOSS movement
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(2); // INBOUND + LOSS
        var lossMv = movements.stream()
                .filter(m -> m.getType().equals("LOSS"))
                .findFirst().orElseThrow();
        assertThat(lossMv.getFromLocationId()).isEqualTo(locationId);
        assertThat(lossMv.getToLocationId()).isNull();
        assertThat(lossMv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(lossMv.getLotId()).isEqualTo(lotId);
        assertThat(lossMv.getReason()).isEqualTo("过期报废");
    }

    // --- Test point 2 (loss): archived items can be lost (uses requireItem, not requireActiveItem) ---
    @Test
    void loss_archivedItem_succeeds() {
        UUID archivedItemId = seedArchivedItem(householdId, unitId);
        UUID lotId = seedLot(householdId, archivedItemId);

        // Seed stock position directly (can't use inboundExistingLot since it rejects archived items)
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        // Lose from archived item should succeed
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.loss(householdId, accountId, lotId, locationId,
                        BigDecimal.valueOf(3), "归档物品报废", null, null));

        assertThat(result).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(7));

        // Verify LOSS movement exists
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo("LOSS");
    }

    // --- Test point 1 (transfer): source decreases, target increases, both revision+1; one TRANSFER movement ---
    @Test
    void transfer_movesStockBetweenLocations() {
        UUID lotId = seedLot(householdId, itemId);
        UUID toLocationId = seedLocation(householdId);

        // Inbound 10 units at source location
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Transfer 4 units from source to target
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.transfer(householdId, accountId, lotId,
                        locationId, toLocationId, BigDecimal.valueOf(4), "移位", null));

        assertThat(result).isNotNull();
        assertThat(result.lotId()).isEqualTo(lotId);
        assertThat(result.locationId()).isEqualTo(toLocationId);
        assertThat(result.movementId()).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(4));

        // Verify source stock position: 10 - 4 = 6, revision = 2
        var fromSp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(fromSp).isNotNull();
        assertThat(fromSp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(6));
        assertThat(fromSp.getRevision()).isEqualTo(2L);

        // Verify target stock position: 4, revision = 1
        var toSp = stockPositionMapper.lockOne(householdId, lotId, toLocationId);
        assertThat(toSp).isNotNull();
        assertThat(toSp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(toSp.getRevision()).isEqualTo(1L);

        // Verify one TRANSFER movement
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(2); // INBOUND + TRANSFER
        var transferMv = movements.stream()
                .filter(m -> m.getType().equals("TRANSFER"))
                .findFirst().orElseThrow();
        assertThat(transferMv.getFromLocationId()).isEqualTo(locationId);
        assertThat(transferMv.getToLocationId()).isEqualTo(toLocationId);
        assertThat(transferMv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(transferMv.getLotId()).isEqualTo(lotId);
    }

    // --- Test point 2 (transfer): insufficient stock → full rollback ---
    @Test
    void transfer_insufficientStock_throwsInsufficientStockException() {
        UUID lotId = seedLot(householdId, itemId);
        UUID toLocationId = seedLocation(householdId);

        // Inbound 3 units at source location
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.valueOf(3), null, null));

        // Try to transfer 5 units (more than available)
        UUID accountId = seedAccount();
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stockCommandService.transfer(householdId, accountId, lotId,
                                locationId, toLocationId, BigDecimal.valueOf(5), "超额移位", null))
        ).isInstanceOf(InventoryInsufficientStockException.class);

        // Source stock position unchanged
        var fromSp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(fromSp).isNotNull();
        assertThat(fromSp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(fromSp.getRevision()).isEqualTo(1L);

        // No target stock position created
        var toSp = stockPositionMapper.lockOne(householdId, lotId, toLocationId);
        assertThat(toSp).isNull();

        // No TRANSFER movement created (only the initial INBOUND)
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        assertThat(movements.get(0).getType()).isEqualTo("INBOUND");
    }

    // --- Test point 3 (transfer): target stock position doesn't exist → created in same transaction ---
    @Test
    void transfer_targetPositionDoesNotExist_createsNewPosition() {
        UUID lotId = seedLot(householdId, itemId);
        UUID toLocationId = seedLocation(householdId);

        // Inbound 10 units at source location
        newTx().executeWithoutResult(s ->
                stockCommandService.inboundExistingLot(householdId, UUID.randomUUID(),
                        locationId, lotId, BigDecimal.TEN, null, null));

        // Verify no target position exists before transfer
        assertThat(stockPositionMapper.lockOne(householdId, lotId, toLocationId)).isNull();

        // Transfer 3 units
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.transfer(householdId, accountId, lotId,
                        locationId, toLocationId, BigDecimal.valueOf(3), null, null));

        // Target position should now exist with 3 units
        var toSp = stockPositionMapper.lockOne(householdId, lotId, toLocationId);
        assertThat(toSp).isNotNull();
        assertThat(toSp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(toSp.getRevision()).isEqualTo(1L);
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(3));
    }

    // --- Test point 4 (transfer): source=Target → defensive IllegalStateException ---
    @Test
    void transfer_sameLocation_throwsIllegalStateException() {
        UUID lotId = seedLot(householdId, itemId);

        UUID accountId = seedAccount();
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stockCommandService.transfer(householdId, accountId, lotId,
                                locationId, locationId, BigDecimal.TEN, null, null))
        ).isInstanceOf(IllegalStateException.class);
    }

    // --- Test point 5 (transfer): archived items can be transferred (uses requireItem) ---
    @Test
    void transfer_archivedItem_succeeds() {
        UUID archivedItemId = seedArchivedItem(householdId, unitId);
        UUID lotId = seedLot(householdId, archivedItemId);
        UUID toLocationId = seedLocation(householdId);

        // Seed stock position directly (can't use inboundExistingLot since it rejects archived items)
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        // Transfer from archived item should succeed
        UUID accountId = seedAccount();
        var result = newTx().execute(s ->
                stockCommandService.transfer(householdId, accountId, lotId,
                        locationId, toLocationId, BigDecimal.valueOf(3), "归档物品移位", null));

        assertThat(result).isNotNull();
        assertThat(result.quantityAfter()).isEqualByComparingTo(BigDecimal.valueOf(3));

        // Verify TRANSFER movement exists
        var movements = movementMapper.selectList(null);
        assertThat(movements).hasSize(1);
        var transferMv = movements.get(0);
        assertThat(transferMv.getType()).isEqualTo("TRANSFER");
        assertThat(transferMv.getFromLocationId()).isEqualTo(locationId);
        assertThat(transferMv.getToLocationId()).isEqualTo(toLocationId);
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

    private UUID seedArchivedItem(UUID householdId, UUID unitId) {
        UUID id = UUID.randomUUID();
        UUID accountId = seedAccount();
        jdbc.update("""
                INSERT INTO catalog_item (id, household_id, name, management_type, unit_id,
                                          status, archived_at, archived_by)
                VALUES (?, ?, ?, 'DURABLE', ?, 'ARCHIVED', CURRENT_TIMESTAMP, ?)
                """, id, householdId, "归档物品" + id.toString().substring(0, 6), unitId, accountId);
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
        jdbc.update("""
                INSERT INTO inventory_lot (id, household_id, item_id, created_at, updated_at, version)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, householdId, itemId);
        return id;
    }

    private TransactionTemplate newTx() {
        return new TransactionTemplate(txManager);
    }
}
