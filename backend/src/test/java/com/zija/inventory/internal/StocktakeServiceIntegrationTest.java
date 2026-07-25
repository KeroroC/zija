package com.zija.inventory.internal;

import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.inventory.internal.persistence.StocktakeItemMapper;
import com.zija.inventory.internal.persistence.StocktakeMapper;
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
class StocktakeServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbc;
    @Autowired HouseholdMapper householdMapper;
    @Autowired LotMapper lotMapper;
    @Autowired StockPositionMapper stockPositionMapper;
    @Autowired StocktakeMapper stocktakeMapper;
    @Autowired StocktakeItemMapper stocktakeItemMapper;
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
