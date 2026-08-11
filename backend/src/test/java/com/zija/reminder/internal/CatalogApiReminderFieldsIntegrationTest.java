package com.zija.reminder.internal;

import com.zija.TestDb;
import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.catalog.internal.persistence.ItemMapper;
import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.catalog.internal.persistence.UnitMapper;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.zija.SharedPostgres;

@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class CatalogApiReminderFieldsIntegrationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired CatalogApi catalogApi;
    @Autowired ItemMapper itemMapper;
    @Autowired UnitMapper unitMapper;
    @Autowired HouseholdMapper householdMapper;
    @Autowired JdbcTemplate jdbc;

    private UUID householdId, unitId, itemId;

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
        UUID id = UUID.randomUUID();
        it.setId(id);
        it.setHouseholdId(householdId);
        it.setName("牛奶");
        it.setManagementType("CONSUMABLE");
        it.setUnitId(unitId);
        it.setStatus("ACTIVE");
        it.setExpiryReminderMode("CUSTOM");
        it.setExpiryReminderDays(List.of((short) 30, (short) 7, (short) 1));
        it.setLowStockMode("CUSTOM");
        it.setLowStockThreshold(new BigDecimal("2"));
        itemMapper.insert(it);
        itemId = id;
    }

    @Test
    void requireItemReturnsReminderFields() {
        var info = catalogApi.requireItem(householdId, itemId);
        assertThat(info.expiryReminderMode()).isEqualTo("CUSTOM");
        assertThat(info.expiryReminderDays()).containsExactly((short) 30, (short) 7, (short) 1);
        assertThat(info.lowStockMode()).isEqualTo("CUSTOM");
        assertThat(info.lowStockThreshold()).isEqualByComparingTo("2");
    }

    @Test
    void requireItemBackwardCompatExistingFieldsStillPresent() {
        var info = catalogApi.requireItem(householdId, itemId);
        assertThat(info.id()).isEqualTo(itemId);
        assertThat(info.name()).isEqualTo("牛奶");
        assertThat(info.status()).isEqualTo("ACTIVE");
        assertThat(info.unitId()).isEqualTo(unitId);
    }
}
