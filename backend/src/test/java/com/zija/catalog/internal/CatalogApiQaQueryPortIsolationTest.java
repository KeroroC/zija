package com.zija.catalog.internal;

import com.zija.SharedPostgres;
import com.zija.TestDb;
import com.zija.catalog.CatalogApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Catalog 有界问答查询端口：家庭 A 的查询不得返回家庭 B 的物品/单位。
 */
@SpringBootTest
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class CatalogApiQaQueryPortIsolationTest {

    @DynamicPropertySource
    static void pgProps(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> SharedPostgres.get().getJdbcUrl());
        r.add("spring.datasource.username", () -> SharedPostgres.get().getUsername());
        r.add("spring.datasource.password", () -> SharedPostgres.get().getPassword());
    }

    @Autowired CatalogApi catalogApi;
    @Autowired JdbcTemplate jdbc;

    private UUID householdA;
    private UUID householdB;
    private UUID unitA;
    private UUID unitB;
    private UUID milkA;
    private UUID yogurtA;
    private UUID blankA;
    private UUID blankBrandItemA;
    private UUID archivedA;
    private UUID milkB;
    private UUID accountId;
    private UUID brandA;
    private UUID brandB;
    private UUID blankBrandA;
    private UUID tagA;
    private UUID tagB;
    private UUID blankTagA;

    @BeforeEach
    void setUp() {
        TestDb.cleanAll(jdbc);
        jdbc.execute("ALTER TABLE household DROP CONSTRAINT IF EXISTS ck_household_singleton");

        householdA = UUID.fromString("10000000-0000-0000-0000-00000000000a");
        householdB = UUID.fromString("10000000-0000-0000-0000-00000000000b");
        unitA = UUID.fromString("30000000-0000-0000-0000-00000000000a");
        unitB = UUID.fromString("30000000-0000-0000-0000-00000000000b");
        milkA = UUID.fromString("40000000-0000-0000-0000-00000000000a");
        yogurtA = UUID.fromString("40000000-0000-0000-0000-00000000000c");
        blankA = UUID.fromString("40000000-0000-0000-0000-00000000000d");
        blankBrandItemA = UUID.fromString("40000000-0000-0000-0000-00000000000f");
        archivedA = UUID.fromString("40000000-0000-0000-0000-00000000000e");
        milkB = UUID.fromString("40000000-0000-0000-0000-00000000000b");
        accountId = UUID.fromString("20000000-0000-0000-0000-00000000000a");
        brandA = UUID.fromString("50000000-0000-0000-0000-00000000000a");
        brandB = UUID.fromString("50000000-0000-0000-0000-00000000000b");
        blankBrandA = UUID.fromString("50000000-0000-0000-0000-00000000000c");
        tagA = UUID.fromString("60000000-0000-0000-0000-00000000000a");
        tagB = UUID.fromString("60000000-0000-0000-0000-00000000000b");
        blankTagA = UUID.fromString("60000000-0000-0000-0000-00000000000c");

        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (1, ?, 'A家', 'Asia/Shanghai')
                """, householdA);
        jdbc.update("""
                INSERT INTO household(singleton_key, id, name, timezone)
                VALUES (2, ?, 'B家', 'Asia/Shanghai')
                """, householdB);
        jdbc.update("""
                INSERT INTO account(id, username, username_normalized, password_hash, display_name, status)
                VALUES (?, 'owner-a', 'OWNER-A', 'hash', '户主A', 'ACTIVE')
                """, accountId);

        insertUnit(unitA, householdA, "瓶");
        insertUnit(unitB, householdB, "盒");
        insertBrand(brandA, householdA, "伊利");
        insertBrand(brandB, householdB, "伊利");
        insertBrand(blankBrandA, householdA, " ");
        insertTag(tagA, householdA, "乳制品");
        insertTag(tagB, householdB, "乳制品");
        insertTag(blankTagA, householdA, " ");
        insertItem(milkA, householdA, unitA, "牛奶", "ACTIVE", brandA);
        insertItem(yogurtA, householdA, unitA, "酸奶", "ACTIVE", null);
        insertItem(blankA, householdA, unitA, " ", "ACTIVE", null);
        insertItem(blankBrandItemA, householdA, unitA, "白牌水", "ACTIVE", blankBrandA);
        insertArchivedItem(archivedA, householdA, unitA, "归档牛奶", brandA);
        insertItem(milkB, householdB, unitB, "牛奶", "ACTIVE", brandB);
        insertItemTag(householdA, milkA, tagA);
        insertItemTag(householdA, yogurtA, tagA);
        insertItemTag(householdA, blankBrandItemA, blankTagA);
        insertItemTag(householdB, milkB, tagB);
    }

    @AfterEach
    void restoreHouseholdSingleton() {
        TestDb.cleanAll(jdbc);
        jdbc.execute("ALTER TABLE household DROP CONSTRAINT IF EXISTS ck_household_singleton");
        jdbc.execute("ALTER TABLE household ADD CONSTRAINT ck_household_singleton CHECK (singleton_key = 1)");
    }

    @Test
    void searchActiveItemsByNameDoesNotReturnOtherHousehold() {
        var hits = catalogApi.searchActiveItemsByName(householdA, "牛奶", null, 10);

        assertThat(hits).extracting(CatalogApi.ItemInfo::id).containsExactly(milkA);
        assertThat(hits).extracting(CatalogApi.ItemInfo::householdId).containsOnly(householdA);
    }

    @Test
    void searchActiveItemsByNameSkipsArchivedAndHonorsItemId() {
        assertThat(catalogApi.searchActiveItemsByName(householdA, "牛奶", null, 10))
                .extracting(CatalogApi.ItemInfo::id)
                .doesNotContain(archivedA);

        assertThat(catalogApi.searchActiveItemsByName(householdA, "奶", yogurtA, 10))
                .extracting(CatalogApi.ItemInfo::id)
                .containsExactly(yogurtA);
        assertThat(catalogApi.searchActiveItemsByName(householdA, "酸奶", milkA, 10)).isEmpty();
    }

    @Test
    void findActiveItemsNamedInQuestionMatchesNameInsideQuestionOnly() {
        var hits = catalogApi.findActiveItemsNamedInQuestion(householdA, "家里牛奶还有多少", 10);

        assertThat(hits).extracting(CatalogApi.ItemInfo::id).containsExactly(milkA);
        assertThat(catalogApi.findActiveItemsNamedInQuestion(householdB, "家里牛奶还有多少", 10))
                .extracting(CatalogApi.ItemInfo::id)
                .containsExactly(milkB);
    }

    @Test
    void findActiveItemsNamedInQuestionIgnoresBlankNamesAndArchivedItems() {
        var hits = catalogApi.findActiveItemsNamedInQuestion(householdA, "库存还有多少？", 10);

        assertThat(hits).extracting(CatalogApi.ItemInfo::id)
                .doesNotContain(blankA, archivedA, milkB);
    }

    @Test
    void unitNamesDoesNotReturnOtherHousehold() {
        var names = catalogApi.unitNames(householdA, List.of(unitA, unitB));

        assertThat(names).containsEntry(unitA, "瓶");
        assertThat(names).doesNotContainKey(unitB);
    }

    @Test
    void findActiveItemsMatchingBrandOrTagInQuestionMatchesBrandInsideQuestionOnly() {
        var hits = catalogApi.findActiveItemsMatchingBrandOrTagInQuestion(householdA, "家里伊利还有多少", 10);

        assertThat(hits).extracting(CatalogApi.ItemBrandOrTagMatch::itemId).containsExactly(milkA);
        assertThat(hits).extracting(CatalogApi.ItemBrandOrTagMatch::itemName).containsExactly("牛奶");
        assertThat(hits).extracting(CatalogApi.ItemBrandOrTagMatch::matchedBrandName).containsExactly("伊利");
        assertThat(catalogApi.findActiveItemsMatchingBrandOrTagInQuestion(householdB, "家里伊利还有多少", 10))
                .extracting(CatalogApi.ItemBrandOrTagMatch::itemId)
                .containsExactly(milkB);
    }

    @Test
    void findActiveItemsMatchingBrandOrTagInQuestionMatchesTagInsideQuestionOnly() {
        var hits = catalogApi.findActiveItemsMatchingBrandOrTagInQuestion(householdA, "乳制品放在哪", 10);

        assertThat(hits).extracting(CatalogApi.ItemBrandOrTagMatch::itemId).containsExactly(milkA, yogurtA);
        assertThat(hits).extracting(CatalogApi.ItemBrandOrTagMatch::matchedTagName).containsOnly("乳制品");
        assertThat(catalogApi.findActiveItemsMatchingBrandOrTagInQuestion(householdB, "乳制品放在哪", 10))
                .extracting(CatalogApi.ItemBrandOrTagMatch::itemId)
                .containsExactly(milkB);
    }

    @Test
    void findActiveItemsMatchingBrandOrTagInQuestionIgnoresBlankNamesAndArchivedItems() {
        var hits = catalogApi.findActiveItemsMatchingBrandOrTagInQuestion(householdA, "库存还有多少？", 10);

        assertThat(hits).extracting(CatalogApi.ItemBrandOrTagMatch::itemId)
                .doesNotContain(blankA, blankBrandItemA, archivedA, milkB);
    }

    private void insertUnit(UUID id, UUID householdId, String name) {
        jdbc.update("""
                INSERT INTO catalog_unit(id, household_id, name, name_normalized, decimal_scale, status)
                VALUES (?, ?, ?, ?, 0, 'ACTIVE')
                """, id, householdId, name, name);
    }

    private void insertBrand(UUID id, UUID householdId, String name) {
        jdbc.update("""
                INSERT INTO catalog_brand(id, household_id, name, name_normalized, status, version)
                VALUES (?, ?, ?, ?, 'ACTIVE', 1)
                """, id, householdId, name, name);
    }

    private void insertTag(UUID id, UUID householdId, String name) {
        jdbc.update("""
                INSERT INTO catalog_tag(id, household_id, name, name_normalized, status, version)
                VALUES (?, ?, ?, ?, 'ACTIVE', 1)
                """, id, householdId, name, name);
    }

    private void insertItemTag(UUID householdId, UUID itemId, UUID tagId) {
        jdbc.update("""
                INSERT INTO catalog_item_tag(household_id, item_id, tag_id)
                VALUES (?, ?, ?)
                """, householdId, itemId, tagId);
    }

    private void insertItem(UUID id, UUID householdId, UUID unitId, String name, String status, UUID brandId) {
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, brand_id, status, version)
                VALUES (?, ?, ?, 'CONSUMABLE', ?, ?, ?, 1)
                """, id, householdId, name, unitId, brandId, status);
    }

    private void insertArchivedItem(UUID id, UUID householdId, UUID unitId, String name, UUID brandId) {
        jdbc.update("""
                INSERT INTO catalog_item
                    (id, household_id, name, management_type, unit_id, brand_id, status,
                     archived_at, archived_by, version)
                VALUES (?, ?, ?, 'CONSUMABLE', ?, ?, 'ARCHIVED', CURRENT_TIMESTAMP, ?, 1)
                """, id, householdId, name, unitId, brandId, accountId);
    }
}
