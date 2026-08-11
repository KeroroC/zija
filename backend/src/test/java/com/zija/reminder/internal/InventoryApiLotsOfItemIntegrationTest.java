package com.zija.reminder.internal;

import com.zija.TestDb;
import com.zija.inventory.InventoryApi;
import com.zija.inventory.internal.StockCommandService;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.catalog.internal.persistence.ItemMapper;
import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.catalog.internal.persistence.UnitMapper;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class InventoryApiLotsOfItemIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired InventoryApi inventoryApi;
    @Autowired StockCommandService stockCommandService;
    @Autowired HouseholdMapper householdMapper;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired LocationMapper locationMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private UUID householdId, itemId, unitId, locA, locB, accountId;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);

        var hh = new HouseholdEntity();
        hh.setSingletonKey((short) 1);
        hh.setId(UUID.randomUUID());
        hh.setName("测试家");
        hh.setTimezone("Asia/Shanghai");
        householdMapper.insertSingleton(hh);
        householdId = hh.getId();

        var u = new UnitEntity();
        u.setId(UUID.randomUUID());
        u.setHouseholdId(householdId);
        u.setName("个");
        u.setNameNormalized("个");
        u.setDecimalScale((short) 0);
        u.setStatus("ACTIVE");
        unitMapper.insert(u);
        unitId = u.getId();

        var it = new ItemEntity();
        it.setId(UUID.randomUUID());
        it.setHouseholdId(householdId);
        it.setName("牛奶");
        it.setManagementType("CONSUMABLE");
        it.setUnitId(unitId);
        it.setStatus("ACTIVE");
        it.setExpiryReminderMode("INHERIT");
        it.setLowStockMode("INHERIT");
        itemMapper.insert(it);
        itemId = it.getId();

        var a = new LocationEntity();
        a.setId(UUID.randomUUID());
        a.setHouseholdId(householdId);
        a.setName("A");
        a.setNameNormalized("A");
        a.setSortOrder(0);
        locationMapper.insert(a);
        locA = a.getId();

        var b = new LocationEntity();
        b.setId(UUID.randomUUID());
        b.setHouseholdId(householdId);
        b.setName("B");
        b.setNameNormalized("B");
        b.setSortOrder(1);
        locationMapper.insert(b);
        locB = b.getId();

        accountId = UUID.randomUUID();
        // Seed account for stock commands
        jdbc.update("""
                INSERT INTO account (id, username, username_normalized, password_hash, display_name)
                VALUES (?, ?, ?, 'hash', '测试用户')
                """, accountId, "user" + accountId.toString().substring(0, 6), "user" + accountId.toString().substring(0, 6));
    }

    @Test
    void lotsOfItemAggregatesAcrossLocations() {
        var expiry = LocalDate.now().plusDays(30);
        var cmd = new StockCommandService.InboundNewLotCommand(
                itemId, new BigDecimal("4"), LocalDate.now(), null, expiry, null, null, null);

        var inboundResult = new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd));
        assertThat(inboundResult).isNotNull();
        UUID lotId = inboundResult.lotId();

        // Transfer 2 from A to B
        new TransactionTemplate(txManager).executeWithoutResult(s ->
                stockCommandService.transfer(householdId, accountId, lotId, locA, locB,
                        new BigDecimal("2"), null, UUID.randomUUID().toString()));

        // Total across both locations should still be 4
        var lots = inventoryApi.lotsOfItem(householdId, itemId);
        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).lotId()).isEqualTo(lotId);
        assertThat(lots.get(0).expiryDate()).isEqualTo(expiry);
        assertThat(lots.get(0).totalQuantity()).isEqualByComparingTo("4");

        assertThat(inventoryApi.currentTotalStockOfItem(householdId, itemId)).isEqualByComparingTo("4");
    }

    @Test
    void lotsOfItemMultipleLots() {
        // Create two lots
        var cmd1 = new StockCommandService.InboundNewLotCommand(
                itemId, new BigDecimal("3"), LocalDate.now(), null, LocalDate.now().plusDays(10), null, null, null);
        var cmd2 = new StockCommandService.InboundNewLotCommand(
                itemId, new BigDecimal("5"), LocalDate.now(), null, LocalDate.now().plusDays(20), null, null, null);

        new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd1));
        new TransactionTemplate(txManager).execute(s ->
                stockCommandService.inboundNewLot(householdId, accountId, locA, cmd2));

        var lots = inventoryApi.lotsOfItem(householdId, itemId);
        assertThat(lots).hasSize(2);

        var totalQty = lots.stream().map(InventoryApi.LotInfo::totalQuantity).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalQty).isEqualByComparingTo("8");

        assertThat(inventoryApi.currentTotalStockOfItem(householdId, itemId)).isEqualByComparingTo("8");
    }

    @Test
    void currentTotalStockOfItemNoLotsReturnsZero() {
        assertThat(inventoryApi.currentTotalStockOfItem(householdId, itemId)).isEqualByComparingTo("0");
    }

    @Test
    void lotsOfItemNoLotsReturnsEmpty() {
        assertThat(inventoryApi.lotsOfItem(householdId, itemId)).isEmpty();
    }
}
