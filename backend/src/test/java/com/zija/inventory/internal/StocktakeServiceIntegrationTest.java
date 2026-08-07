package com.zija.inventory.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.exception.InventoryLotVersionConflictException;
import com.zija.inventory.internal.exception.StocktakeNotDraftException;
import com.zija.inventory.internal.exception.StocktakeStaleException;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementEntity;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.inventory.internal.persistence.StocktakeItemMapper;
import com.zija.inventory.internal.persistence.StocktakeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class StocktakeServiceIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired StockPositionMapper stockPositionMapper;
    @Autowired StocktakeMapper stocktakeMapper;
    @Autowired StocktakeItemMapper stocktakeItemMapper;
    @Autowired MovementMapper movementMapper;
    @Autowired StocktakeService stocktakeService;
    @Autowired PlatformTransactionManager txManager;

    private UUID householdId;
    private UUID accountId;
    private UUID locationId;

    @BeforeEach
    void setUp() {
        jdbc.execute("""
                TRUNCATE TABLE inventory_stocktake_item, inventory_stocktake,
                             inventory_movement, inventory_stock_position,
                             inventory_lot, audit_log, catalog_item, catalog_unit,
                             location, household, account
                RESTART IDENTITY CASCADE
                """);

        householdId = seedHousehold();
        accountId = seedAccount();
        locationId = seedLocation(householdId);
    }

    // --- Test case 1: scope with 2 positions -> draft has 2 items ---
    @Test
    void createDraft_withTwoPositions_createsDraftWithTwoItems() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId1 = seedLot(householdId, itemId);
        UUID lotId2 = seedLot(householdId, itemId);

        // Seed two stock positions with different quantities and revisions
        UUID spId1 = UUID.randomUUID();
        UUID spId2 = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId1, householdId, lotId1, locationId);
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 25, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId2, householdId, lotId2, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        assertThat(stocktakeId).isNotNull();

        // Verify stocktake record
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake).isNotNull();
        assertThat(stocktake.getStatus()).isEqualTo("DRAFT");
        assertThat(stocktake.getHouseholdId()).isEqualTo(householdId);
        assertThat(stocktake.getCreatedBy()).isEqualTo(accountId);
        assertThat(stocktake.getVersion()).isEqualTo(0);

        // Verify draft items
        var items = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(items).hasSize(2);

        // Item for lot1: book=10, actual=10, positionRevision=1
        var item1 = items.stream().filter(i -> i.getLotId().equals(lotId1)).findFirst().orElseThrow();
        assertThat(item1.getBookQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(item1.getActualQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(item1.getPositionRevision()).isEqualTo(1L);
        assertThat(item1.getLocationId()).isEqualTo(locationId);

        // Item for lot2: book=25, actual=25, positionRevision=3
        var item2 = items.stream().filter(i -> i.getLotId().equals(lotId2)).findFirst().orElseThrow();
        assertThat(item2.getBookQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(item2.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(item2.getPositionRevision()).isEqualTo(3L);
    }

    // --- Test case 2: scope with no positions -> draft created, 0 items ---
    @Test
    void createDraft_noPositions_createsEmptyDraft() {
        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        assertThat(stocktakeId).isNotNull();

        // Verify stocktake record
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake).isNotNull();
        assertThat(stocktake.getStatus()).isEqualTo("DRAFT");

        // Verify 0 items
        var items = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(items).isEmpty();
    }

    // --- Test case 3: non-existent location -> error from LocationApi ---
    @Test
    void createDraft_nonExistentLocation_throwsException() {
        UUID nonExistentLocationId = UUID.randomUUID();

        assertThatThrownBy(() ->
                newTx().execute(s ->
                        stocktakeService.createDraft(householdId, accountId, nonExistentLocationId))
        ).isInstanceOf(Exception.class);
    }

    // --- updateDraft test case 1: update actualQuantity -> item actual changes, book unchanged ---
    @Test
    void updateDraft_existingItem_updatesActualQuantityOnly() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        var update = new StocktakeService.StocktakeItemUpdate(lotId, locationId, BigDecimal.valueOf(7), "盘点少了3个");
        newTx().executeWithoutResult(s ->
                stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)));

        var items = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(items).hasSize(1);
        var item = items.get(0);
        assertThat(item.getLotId()).isEqualTo(lotId);
        assertThat(item.getBookQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(item.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(7));
        assertThat(item.getReason()).isEqualTo("盘点少了3个");
    }

    // --- updateDraft test case 2: backfill zero-quantity lot -> draft item +1, book=0 ---
    @Test
    void updateDraft_backfillZeroQuantityLot_insertsNewItemWithBookZero() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        // No stock position for (lotId, locationId) -> backfill allowed

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        var update = new StocktakeService.StocktakeItemUpdate(lotId, locationId, BigDecimal.valueOf(5), "发现遗漏");
        newTx().executeWithoutResult(s ->
                stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)));

        var items = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(items).hasSize(1);
        var item = items.get(0);
        assertThat(item.getLotId()).isEqualTo(lotId);
        assertThat(item.getBookQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(item.getReason()).isEqualTo("发现遗漏");
        assertThat(item.getPositionRevision()).isEqualTo(0L);
    }

    // --- updateDraft test case 3: backfill lot with positive quantity -> StocktakeNotDraftException ---
    @Test
    void updateDraft_backfillLotWithPositiveQuantity_throwsStocktakeNotDraft() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 15, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Remove the item from draft to simulate a lot not in draft but with positive stock
        stocktakeItemMapper.deleteByStocktake(stocktakeId);

        var update = new StocktakeService.StocktakeItemUpdate(lotId, locationId, BigDecimal.valueOf(10), "补录");
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)))
        ).isInstanceOf(StocktakeNotDraftException.class);
    }

    // --- updateDraft test case 4: backfill non-existent lot -> StocktakeNotDraftException ---
    @Test
    void updateDraft_backfillNonExistentLot_throwsStocktakeNotDraft() {
        UUID nonExistentLotId = UUID.randomUUID();

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        var update = new StocktakeService.StocktakeItemUpdate(nonExistentLotId, locationId, BigDecimal.ONE, "补录");
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)))
        ).isInstanceOf(StocktakeNotDraftException.class);
    }

    // --- updateDraft test case 5: non-DRAFT status update -> StocktakeNotDraftException ---
    @Test
    void updateDraft_nonDraftStatus_throwsStocktakeNotDraft() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Change status to COMPLETED
        jdbc.update("UPDATE inventory_stocktake SET status = 'COMPLETED' WHERE id = ?", stocktakeId);

        var update = new StocktakeService.StocktakeItemUpdate(lotId, locationId, BigDecimal.valueOf(8), "调整");
        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)))
        ).isInstanceOf(StocktakeNotDraftException.class);
    }

    // --- refreshDraft test case 1: after refresh, items re-snapshot current quantity/revision ---
    @Test
    void refreshDraft_afterStockChanges_reSnapshotsCurrentPositions() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId1 = seedLot(householdId, itemId);
        UUID lotId2 = seedLot(householdId, itemId);

        // Seed two stock positions with initial quantities
        UUID spId1 = UUID.randomUUID();
        UUID spId2 = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId1, householdId, lotId1, locationId);
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 25, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId2, householdId, lotId2, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Verify initial draft items
        var itemsBefore = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(itemsBefore).hasSize(2);

        // Simulate stock changes: lot1 quantity 10->15 revision 1->2; add lot3
        UUID lotId3 = seedLot(householdId, itemId);
        UUID spId3 = UUID.randomUUID();
        jdbc.update("""
                UPDATE inventory_stock_position SET quantity = 15, revision = 2, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, spId1);
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 8, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId3, householdId, lotId3, locationId);

        // Refresh draft
        newTx().executeWithoutResult(s ->
                stocktakeService.refreshDraft(householdId, stocktakeId, 0, locationId));

        // Verify items re-snapshot
        var itemsAfter = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(itemsAfter).hasSize(3);

        var refreshedItem1 = itemsAfter.stream().filter(i -> i.getLotId().equals(lotId1)).findFirst().orElseThrow();
        assertThat(refreshedItem1.getBookQuantity()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(refreshedItem1.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(refreshedItem1.getPositionRevision()).isEqualTo(2L);

        var refreshedItem2 = itemsAfter.stream().filter(i -> i.getLotId().equals(lotId2)).findFirst().orElseThrow();
        assertThat(refreshedItem2.getBookQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(refreshedItem2.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(refreshedItem2.getPositionRevision()).isEqualTo(3L);

        var refreshedItem3 = itemsAfter.stream().filter(i -> i.getLotId().equals(lotId3)).findFirst().orElseThrow();
        assertThat(refreshedItem3.getBookQuantity()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(refreshedItem3.getActualQuantity()).isEqualByComparingTo(BigDecimal.valueOf(8));
        assertThat(refreshedItem3.getPositionRevision()).isEqualTo(1L);

        // Verify version bumped
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake.getVersion()).isEqualTo(1);
    }

    // --- refreshDraft test case 2: non-DRAFT rejected ---
    @Test
    void refreshDraft_nonDraftStatus_throwsStocktakeNotDraft() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Change status to COMPLETED
        jdbc.update("UPDATE inventory_stocktake SET status = 'COMPLETED' WHERE id = ?", stocktakeId);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.refreshDraft(householdId, stocktakeId, 0, locationId))
        ).isInstanceOf(StocktakeNotDraftException.class);
    }

    // --- confirm test case 1: no differences -> COMPLETED, no movements, adjustedCount=0 ---
    @Test
    void confirm_noDifferences_completesWithNoMovements() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        var result = newTx().execute(s ->
                stocktakeService.confirm(householdId, stocktakeId, 0, accountId));

        assertThat(result.stocktakeId()).isEqualTo(stocktakeId);
        assertThat(result.adjustedCount()).isEqualTo(0);

        // Verify stocktake is COMPLETED
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake.getStatus()).isEqualTo("COMPLETED");
        assertThat(stocktake.getCompletedAt()).isNotNull();

        // Verify no ADJUSTMENT movements
        Long movementCount = movementMapper.selectCount(
                new LambdaQueryWrapper<MovementEntity>()
                        .eq(MovementEntity::getHouseholdId, householdId)
                        .eq(MovementEntity::getType, "ADJUSTMENT"));
        assertThat(movementCount).isEqualTo(0);

        // Verify stock position unchanged
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(sp.getRevision()).isEqualTo(1L);
    }

    // --- confirm test case 2: with differences -> ADJUSTMENT movements, stock updated, revision+1 ---
    @Test
    void confirm_withDifferences_createsAdjustmentMovementsAndUpdatesStock() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId1 = seedLot(householdId, itemId);
        UUID lotId2 = seedLot(householdId, itemId);

        UUID spId1 = UUID.randomUUID();
        UUID spId2 = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId1, householdId, lotId1, locationId);
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 20, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId2, householdId, lotId2, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Update: lot1 actual=7 (少了3), lot2 actual=25 (多了5)
        var update1 = new StocktakeService.StocktakeItemUpdate(lotId1, locationId, BigDecimal.valueOf(7), "丢失3个");
        var update2 = new StocktakeService.StocktakeItemUpdate(lotId2, locationId, BigDecimal.valueOf(25), "多出5个");
        newTx().executeWithoutResult(s ->
                stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update1, update2)));

        var result = newTx().execute(s ->
                stocktakeService.confirm(householdId, stocktakeId, 1, accountId));

        assertThat(result.adjustedCount()).isEqualTo(2);

        // Verify stocktake is COMPLETED
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake.getStatus()).isEqualTo("COMPLETED");
        assertThat(stocktake.getCompletedAt()).isNotNull();

        // Verify 2 ADJUSTMENT movements
        List<MovementEntity> movements = movementMapper.selectList(
                new LambdaQueryWrapper<MovementEntity>()
                        .eq(MovementEntity::getHouseholdId, householdId)
                        .eq(MovementEntity::getType, "ADJUSTMENT")
                        .orderByAsc(MovementEntity::getCreatedAt));
        assertThat(movements).hasSize(2);

        // lot1: actual < book -> from_location=location, quantity=3
        var mov1 = movements.stream().filter(m -> m.getLotId().equals(lotId1)).findFirst().orElseThrow();
        assertThat(mov1.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(mov1.getFromLocationId()).isEqualTo(locationId);
        assertThat(mov1.getToLocationId()).isNull();
        assertThat(mov1.getReason()).isEqualTo("丢失3个");

        // lot2: actual > book -> to_location=location, quantity=5
        var mov2 = movements.stream().filter(m -> m.getLotId().equals(lotId2)).findFirst().orElseThrow();
        assertThat(mov2.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(mov2.getFromLocationId()).isNull();
        assertThat(mov2.getToLocationId()).isEqualTo(locationId);
        assertThat(mov2.getReason()).isEqualTo("多出5个");

        // Verify stock positions updated
        var sp1 = stockPositionMapper.lockOne(householdId, lotId1, locationId);
        assertThat(sp1.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(7));
        assertThat(sp1.getRevision()).isEqualTo(2L);

        var sp2 = stockPositionMapper.lockOne(householdId, lotId2, locationId);
        assertThat(sp2.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));
        assertThat(sp2.getRevision()).isEqualTo(3L);
    }

    // --- confirm test case 3: scope inventory changed after draft -> StocktakeStaleException ---
    @Test
    void confirm_scopeInventoryChanged_throwsStocktakeStale() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Simulate stock change after draft
        jdbc.update("""
                UPDATE inventory_stock_position SET quantity = 15, revision = 2, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, spId);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.confirm(householdId, stocktakeId, 0, accountId))
        ).isInstanceOf(StocktakeStaleException.class);

        // Verify still DRAFT (rolled back)
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake.getStatus()).isEqualTo("DRAFT");

        // Verify no ADJUSTMENT movements
        Long movementCount = movementMapper.selectCount(
                new LambdaQueryWrapper<MovementEntity>()
                        .eq(MovementEntity::getHouseholdId, householdId)
                        .eq(MovementEntity::getType, "ADJUSTMENT"));
        assertThat(movementCount).isEqualTo(0);
    }

    // --- confirm test case 4: difference reason missing -> IllegalArgumentException ---
    @Test
    void confirm_differenceWithoutReason_throwsIllegalArgument() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Update actual without reason
        var update = new StocktakeService.StocktakeItemUpdate(lotId, locationId, BigDecimal.valueOf(7), null);
        newTx().executeWithoutResult(s ->
                stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)));

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.confirm(householdId, stocktakeId, 1, accountId))
        ).isInstanceOf(IllegalArgumentException.class);

        // Verify still DRAFT
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake.getStatus()).isEqualTo("DRAFT");
    }

    // --- confirm test case 5: already COMPLETED -> StocktakeNotDraftException ---
    @Test
    void confirm_alreadyCompleted_throwsStocktakeNotDraft() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Change status to COMPLETED
        jdbc.update("UPDATE inventory_stocktake SET status = 'COMPLETED' WHERE id = ?", stocktakeId);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.confirm(householdId, stocktakeId, 0, accountId))
        ).isInstanceOf(StocktakeNotDraftException.class);
    }

    // --- confirm test case 7: backfilled overflow (book=0, actual>0) creates stock position ---
    // 复现 bug：补录账面为零的批次、确认盘盈时，库存位必须被创建出来，数量等于 actualQuantity，
    // 且 ADJUSTMENT 流水与库存位保持一致（不再出现"流水说加了 5、库存位不存在"的永久不一致）。
    @Test
    void confirm_backfilledOverflow_createsStockPosition() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        // 关键：(lotId, locationId) 不存在库存位 — 模拟 updateDraft 补录路径

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // 通过 updateDraft 补录（账面 0，实盘 5）
        var update = new StocktakeService.StocktakeItemUpdate(
                lotId, locationId, BigDecimal.valueOf(5), "发现遗漏批次");
        newTx().executeWithoutResult(s ->
                stocktakeService.updateDraft(householdId, stocktakeId, 0, List.of(update)));

        // 确认盘点
        var result = newTx().execute(s ->
                stocktakeService.confirm(householdId, stocktakeId, 1, accountId));
        assertThat(result.adjustedCount()).isEqualTo(1);

        // 库存位必须存在且数量 = 5（这是当前 bug 失败的关键断言）
        var sp = stockPositionMapper.lockOne(householdId, lotId, locationId);
        assertThat(sp).as("盘盈后库存位必须被创建").isNotNull();
        assertThat(sp.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(sp.getRevision()).isEqualTo(1L);

        // ADJUSTMENT 流水必须存在
        var movements = movementMapper.selectList(
                new LambdaQueryWrapper<MovementEntity>()
                        .eq(MovementEntity::getHouseholdId, householdId)
                        .eq(MovementEntity::getType, "ADJUSTMENT")
                        .eq(MovementEntity::getLotId, lotId));
        assertThat(movements).hasSize(1);
        var mov = movements.get(0);
        assertThat(mov.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(mov.getFromLocationId()).isNull();
        assertThat(mov.getToLocationId()).isEqualTo(locationId);
        assertThat(mov.getReason()).isEqualTo("发现遗漏批次");

        // 一致性检查：当前实现下，expectedMap 有 (lotId, locationId)→5 但 actualPositions 不含此 key，
        // ConsistencyCheckService.check() 只遍历 actualPositions，故漏报。修复后两者一致。
        // 这里不强加断言一致性检查的输出（属另一个 bug），仅断言 stockPosition 与 movement 不再分裂。
    }

    // --- confirm test case 6: version conflict -> InventoryLotVersionConflictException ---
    @Test
    void confirm_versionConflict_throwsVersionConflict() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Simulate version bump by another operation
        jdbc.update("UPDATE inventory_stocktake SET version = 5 WHERE id = ?", stocktakeId);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.confirm(householdId, stocktakeId, 0, accountId))
        ).isInstanceOf(InventoryLotVersionConflictException.class);
    }

    // --- cancel test case 1: cancel DRAFT -> CANCELLED, items deleted ---
    @Test
    void cancel_draftStocktake_cancelsAndDeletesItems() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId1 = seedLot(householdId, itemId);
        UUID lotId2 = seedLot(householdId, itemId);

        UUID spId1 = UUID.randomUUID();
        UUID spId2 = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId1, householdId, lotId1, locationId);
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision,  created_at, updated_at)
                VALUES (?, ?, ?, ?, 25, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId2, householdId, lotId2, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Verify draft has 2 items
        var itemsBefore = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(itemsBefore).hasSize(2);

        // Cancel
        newTx().executeWithoutResult(s ->
                stocktakeService.cancel(householdId, stocktakeId, 0));

        // Verify stocktake is CANCELLED
        var stocktake = stocktakeMapper.selectById(stocktakeId);
        assertThat(stocktake.getStatus()).isEqualTo("CANCELLED");
        assertThat(stocktake.getVersion()).isEqualTo(1);

        // Verify all items deleted
        var itemsAfter = newTx().execute(s ->
                stocktakeService.draftItems(householdId, stocktakeId));
        assertThat(itemsAfter).isEmpty();

        // Verify no ADJUSTMENT movements created
        Long movementCount = movementMapper.selectCount(
                new LambdaQueryWrapper<MovementEntity>()
                        .eq(MovementEntity::getHouseholdId, householdId)
                        .eq(MovementEntity::getType, "ADJUSTMENT"));
        assertThat(movementCount).isEqualTo(0);
    }

    // --- cancel test case 2: cancel non-DRAFT -> StocktakeNotDraftException ---
    @Test
    void cancel_nonDraftStatus_throwsStocktakeNotDraft() {
        UUID itemId = seedItem(householdId, seedUnit(householdId));
        UUID lotId = seedLot(householdId, itemId);
        UUID spId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_stock_position (id, household_id, lot_id, location_id, quantity, revision, created_at, updated_at)
                VALUES (?, ?, ?, ?, 10, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, spId, householdId, lotId, locationId);

        UUID stocktakeId = newTx().execute(s ->
                stocktakeService.createDraft(householdId, accountId, locationId));

        // Change status to COMPLETED
        jdbc.update("UPDATE inventory_stocktake SET status = 'COMPLETED' WHERE id = ?", stocktakeId);

        assertThatThrownBy(() ->
                newTx().executeWithoutResult(s ->
                        stocktakeService.cancel(householdId, stocktakeId, 0))
        ).isInstanceOf(StocktakeNotDraftException.class);
    }

    // Note: the deficit branch's subtractIfSufficient-return-zero path is mathematically
    // unreachable through normal API calls because of DB CHECK constraints
    // (ck_inventory_stocktake_actual_nonneg, ck_inventory_stock_position_nonneg), so it is
    // covered by StocktakeServiceDefensiveTest using a mocked mapper rather than an integration test.

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
        // Use UUID prefix to guarantee uniqueness across all 22 seedLot calls
        // sharing the same static Testcontainers container. Math.random-based
        // suffixes collided on uq_inventory_lot_number ~22% of the time when
        // run in the full suite (consumed from a different RNG offset).
        String lotNumber = "LOT-" + id.toString().substring(0, 8);
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
