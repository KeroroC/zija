package com.zija.ai.internal;

import com.zija.catalog.CatalogApi;
import com.zija.inventory.InventoryApi;
import com.zija.location.LocationApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QaScopePlannerTest {

    private static final UUID HOUSEHOLD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ITEM_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID LOT_A = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID LOT_B = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID UNIT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");

    private StubCatalogApi catalogApi;
    private StubInventoryApi inventoryApi;
    private StubLocationApi locationApi;
    private QaScopePlanner planner;

    @BeforeEach
    void setUp() {
        catalogApi = new StubCatalogApi();
        inventoryApi = new StubInventoryApi();
        locationApi = new StubLocationApi();
        planner = new QaScopePlanner(catalogApi, inventoryApi, locationApi);

        catalogApi.items = List.of(item("牛奶"));
        catalogApi.itemNames = Map.of(ITEM_ID, "牛奶");
        locationApi.roots = List.of();
    }

    @Test
    void recommendTypicalFactQuestionAsHouseholdFact() {
        assertThat(planner.recommend("牛奶还有多少？", null)).isEqualTo("HOUSEHOLD_FACT");
    }

    @Test
    void recommendTypicalKnowledgeQuestionAsKnowledgeSource() {
        assertThat(planner.recommend("滤网怎么清洁？", null)).isEqualTo("KNOWLEDGE_SOURCE");
    }

    @Test
    void recommendMixedFactAndKnowledgeAsBoth() {
        assertThat(planner.recommend("过期了怎么处理", null)).isEqualTo("BOTH");
    }

    @Test
    void recommendNoHitWithoutPageItemContextAsHouseholdFact() {
        assertThat(planner.recommend("这个呢？", null)).isEqualTo("HOUSEHOLD_FACT");
    }

    @Test
    void recommendNoHitWithItemPageContextAsBoth() {
        var pageTarget = new HouseholdFactQaModels.QaTarget("ITEM", ITEM_ID, "牛奶");
        assertThat(planner.recommend("这个呢？", pageTarget)).isEqualTo("BOTH");
    }

    @Test
    void recommendNoHitWithLocationPageContextAsHouseholdFact() {
        UUID locationId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        var pageTarget = new HouseholdFactQaModels.QaTarget("LOCATION", locationId, "厨房");
        assertThat(planner.recommend("这个呢？", pageTarget)).isEqualTo("HOUSEHOLD_FACT");
    }

    @Test
    void previewDoesNotScanItemsLotsOrLocations() {
        var pageTarget = new HouseholdFactQaModels.QaTarget("ITEM", ITEM_ID);
        String recommended = planner.previewRecommendedScope(HOUSEHOLD_ID, "这个呢？", pageTarget);

        assertThat(recommended).isEqualTo("BOTH");
        assertThat(catalogApi.listActiveItemsCalls).isZero();
        assertThat(catalogApi.findActiveItemsNamedInQuestionCalls).isZero();
        assertThat(catalogApi.findActiveItemsMatchingBrandOrTagInQuestionCalls).isZero();
        assertThat(inventoryApi.lotsOfItemCalls).isZero();
        assertThat(inventoryApi.findLotsMatchingQuestionCalls).isZero();
        assertThat(locationApi.treeCalls).isZero();
    }

    @Test
    void previewTreatsUnauthorizedPageContextAsAbsent() {
        UUID foreignItem = UUID.fromString("00000000-0000-0000-0000-000000000099");
        var pageTarget = new HouseholdFactQaModels.QaTarget("ITEM", foreignItem);

        String recommended = planner.previewRecommendedScope(HOUSEHOLD_ID, "这个呢？", pageTarget);

        assertThat(recommended).isEqualTo("HOUSEHOLD_FACT");
        assertThat(catalogApi.listActiveItemsCalls).isZero();
        assertThat(catalogApi.findActiveItemsNamedInQuestionCalls).isZero();
        assertThat(catalogApi.findActiveItemsMatchingBrandOrTagInQuestionCalls).isZero();
    }

    @Test
    void blankSerialDoesNotMatchUnrelatedQuestion() {
        inventoryApi.lots = List.of(
                lot(LOT_A, "", "LOT-A"),
                lot(LOT_B, "SN-002", "LOT-B"));

        var plan = planner.plan(HOUSEHOLD_ID, request("厨房还有多少东西？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.target()).isNull();
        assertThat(catalogApi.listActiveItemsCalls).isZero();
        assertThat(inventoryApi.lotsOfItemCalls).isZero();
    }

    @Test
    void twoLotsWithBlankSerialDoNotCauseAmbiguityOnUnrelatedQuestion() {
        inventoryApi.lots = List.of(
                lot(LOT_A, "", "LOT-A"),
                lot(LOT_B, "", "LOT-B"));

        var plan = planner.plan(HOUSEHOLD_ID, request("今天天气怎么样？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.target()).isNull();
    }

    @Test
    void blankLotNumberDoesNotMatchUnrelatedQuestion() {
        inventoryApi.lots = List.of(lot(LOT_A, null, ""));

        var plan = planner.plan(HOUSEHOLD_ID, request("提醒规则怎么设置？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.target()).isNull();
    }

    @Test
    void blankItemNameDoesNotMatchEveryQuestion() {
        catalogApi.items = List.of(item(""));
        catalogApi.itemNames = Map.of(ITEM_ID, "");

        var plan = planner.plan(HOUSEHOLD_ID, request("库存还有多少？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.target()).isNull();
    }

    @Test
    void blankLocationNameDoesNotMatchEveryQuestion() {
        UUID locationId = UUID.fromString("00000000-0000-0000-0000-000000000030");
        locationApi.roots = List.of(new LocationApi.LocationNode(
                locationId, null, "", 0, false, 0, List.of()));

        var plan = planner.plan(HOUSEHOLD_ID, request("低库存有哪些？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.target()).isNull();
    }

    @Test
    void nonBlankSerialStillMatchesQuestion() {
        inventoryApi.lots = List.of(lot(LOT_A, "SN-COFFEE", "LOT-A"));

        var plan = planner.plan(HOUSEHOLD_ID, request("序列号 SN-COFFEE 在哪？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.target()).isNotNull();
        assertThat(plan.target().type()).isEqualTo("LOT");
        assertThat(plan.target().id()).isEqualTo(LOT_A);
        assertThat(catalogApi.listActiveItemsCalls).isZero();
        assertThat(inventoryApi.lotsOfItemCalls).isZero();
        assertThat(inventoryApi.findLotsMatchingQuestionCalls).isEqualTo(1);
    }

    @Test
    void knowledgeQuestionWithoutItemOrLotUsesHouseholdMountedSourcesWithoutConfirmation() {
        var plan = planner.plan(HOUSEHOLD_ID, request("家里的维护约定怎么写？", "KNOWLEDGE_SOURCE"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.usedAnswerScope()).isEqualTo("KNOWLEDGE_SOURCE");
        assertThat(plan.target()).isNull();
        assertThat(plan.knowledgeTarget()).isNull();
        assertThat(plan.candidates()).isEmpty();
    }

    @Test
    void mixedQuestionWithoutKnowledgeTargetKeepsHouseholdFactsAndHouseholdKnowledge() {
        var plan = planner.plan(HOUSEHOLD_ID, request("家里的维护约定怎么写？", "BOTH"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.usedAnswerScope()).isEqualTo("BOTH");
        assertThat(plan.target()).isNull();
        assertThat(plan.knowledgeTarget()).isNull();
        assertThat(plan.candidates()).isEmpty();
    }

    @Test
    void knowledgeQuestionStillConfirmsWhenMultipleSameNameItemsAreEligible() {
        UUID secondItemId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        catalogApi.items = List.of(item("牛奶"), item(secondItemId, "牛奶"));
        catalogApi.itemNames = Map.of(ITEM_ID, "牛奶", secondItemId, "牛奶");

        var plan = planner.plan(HOUSEHOLD_ID, request("牛奶怎么维护？", "KNOWLEDGE_SOURCE"));

        assertThat(plan.needsConfirmation()).isTrue();
        assertThat(plan.candidates()).hasSize(2);
        assertThat(plan.candidates()).allMatch(candidate -> "ITEM".equals(candidate.type()));
        assertThat(plan.knowledgeTarget()).isNull();
        assertThat(catalogApi.listActiveItemsCalls).isZero();
        assertThat(catalogApi.findActiveItemsNamedInQuestionCalls).isGreaterThan(0);
    }

    @Test
    void brandNameInQuestionResolvesToTheMatchingActiveItem() {
        catalogApi.items = List.of(item("酸奶"));
        catalogApi.itemNames = Map.of(ITEM_ID, "酸奶");
        catalogApi.brandOrTagMatches = List.of(brandMatch(ITEM_ID, "酸奶", "伊利"));

        var plan = planner.plan(HOUSEHOLD_ID, request("伊利还有多少？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.target()).isNotNull();
        assertThat(plan.target().type()).isEqualTo("ITEM");
        assertThat(plan.target().id()).isEqualTo(ITEM_ID);
        assertThat(plan.target().label()).isEqualTo("酸奶");
        assertThat(catalogApi.findActiveItemsMatchingBrandOrTagInQuestionCalls).isGreaterThan(0);
    }

    @Test
    void multipleItemsWithTheSameBrandRequireConfirmation() {
        UUID secondItemId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        catalogApi.items = List.of(item("酸奶"), item(secondItemId, "纯牛奶"));
        catalogApi.itemNames = Map.of(ITEM_ID, "酸奶", secondItemId, "纯牛奶");
        catalogApi.brandOrTagMatches = List.of(
                brandMatch(ITEM_ID, "酸奶", "伊利"),
                brandMatch(secondItemId, "纯牛奶", "伊利"));

        var plan = planner.plan(HOUSEHOLD_ID, request("伊利还有多少？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isTrue();
        assertThat(plan.candidates()).hasSize(2);
        assertThat(plan.candidates()).allMatch(candidate -> "ITEM".equals(candidate.type()));
        assertThat(plan.candidates()).allMatch(candidate -> candidate.detail().contains("品牌 · 伊利"));
        assertThat(plan.target()).isNull();
    }

    @Test
    void tagNameInQuestionResolvesToTheMatchingActiveItem() {
        catalogApi.items = List.of(item("洗衣液"));
        catalogApi.itemNames = Map.of(ITEM_ID, "洗衣液");
        catalogApi.brandOrTagMatches = List.of(tagMatch(ITEM_ID, "洗衣液", "日用品"));

        var plan = planner.plan(HOUSEHOLD_ID, request("日用品放在哪？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.target()).isNotNull();
        assertThat(plan.target().type()).isEqualTo("ITEM");
        assertThat(plan.target().id()).isEqualTo(ITEM_ID);
    }

    @Test
    void multipleItemsWithTheSameTagRequireConfirmation() {
        UUID secondItemId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        catalogApi.items = List.of(item("酸奶"), item(secondItemId, "纯牛奶"));
        catalogApi.itemNames = Map.of(ITEM_ID, "酸奶", secondItemId, "纯牛奶");
        catalogApi.brandOrTagMatches = List.of(
                tagMatch(ITEM_ID, "酸奶", "乳制品"),
                tagMatch(secondItemId, "纯牛奶", "乳制品"));

        var plan = planner.plan(HOUSEHOLD_ID, request("乳制品还有多少？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isTrue();
        assertThat(plan.candidates()).hasSize(2);
        assertThat(plan.candidates()).allMatch(candidate -> candidate.detail().contains("标签 · 乳制品"));
    }

    @Test
    void brandMatchDoesNotDuplicateAnItemAlreadyMatchedByName() {
        catalogApi.brandOrTagMatches = List.of(brandMatch(ITEM_ID, "牛奶", "伊利"));

        var plan = planner.plan(HOUSEHOLD_ID, request("伊利牛奶还有多少？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.target()).isNotNull();
        assertThat(plan.target().id()).isEqualTo(ITEM_ID);
        assertThat(plan.candidates()).isEmpty();
    }

    private static CatalogApi.ItemBrandOrTagMatch brandMatch(UUID itemId, String itemName, String brandName) {
        return new CatalogApi.ItemBrandOrTagMatch(itemId, itemName, brandName, null);
    }

    private static CatalogApi.ItemBrandOrTagMatch tagMatch(UUID itemId, String itemName, String tagName) {
        return new CatalogApi.ItemBrandOrTagMatch(itemId, itemName, null, tagName);
    }

    private static CatalogApi.ItemInfo item(String name) {
        return item(ITEM_ID, name);
    }

    private static CatalogApi.ItemInfo item(UUID itemId, String name) {
        return new CatalogApi.ItemInfo(
                itemId, HOUSEHOLD_ID, name, "CONSUMABLE", null, null, UNIT_ID, null, "ACTIVE",
                null, null, null, null);
    }

    private static InventoryApi.LotFlat lot(UUID lotId, String serialNumber, String lotNumber) {
        return new InventoryApi.LotFlat(lotId, ITEM_ID, lotNumber, serialNumber, null);
    }

    private static HouseholdFactQaModels.QaRequest request(String question, String answerScope) {
        return new HouseholdFactQaModels.QaRequest(question, null, answerScope, null, List.of());
    }

    private static final class StubCatalogApi implements CatalogApi {
        List<ItemInfo> items = List.of();
        List<ItemBrandOrTagMatch> brandOrTagMatches = List.of();
        Map<UUID, String> itemNames = Map.of();
        int listActiveItemsCalls;
        int findActiveItemsNamedInQuestionCalls;
        int findActiveItemsMatchingBrandOrTagInQuestionCalls;

        @Override
        public ItemInfo requireItem(UUID householdId, UUID itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ItemInfo requireActiveItem(UUID householdId, UUID itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public UnitInfo requireUnit(UUID householdId, UUID unitId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<UUID, String> itemNames(UUID householdId, Collection<UUID> itemIds) {
            return itemNames;
        }

        @Override
        public List<ItemInfo> listActiveItems(UUID householdId) {
            listActiveItemsCalls++;
            return items;
        }

        @Override
        public List<ItemInfo> searchActiveItemsByName(
                UUID householdId, String nameContains, UUID itemId, int limit
        ) {
            String needle = nameContains == null ? "" : nameContains.trim().toLowerCase(java.util.Locale.ROOT);
            return items.stream()
                    .filter(item -> itemId == null || itemId.equals(item.id()))
                    .filter(item -> needle.isEmpty()
                            || (item.name() != null && item.name().toLowerCase(java.util.Locale.ROOT).contains(needle)))
                    .limit(Math.max(0, limit))
                    .toList();
        }

        @Override
        public List<ItemInfo> findActiveItemsNamedInQuestion(UUID householdId, String question, int limit) {
            findActiveItemsNamedInQuestionCalls++;
            String normalized = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
            return items.stream()
                    .filter(item -> item.name() != null && !item.name().isBlank()
                            && normalized.contains(item.name().toLowerCase(java.util.Locale.ROOT)))
                    .limit(Math.max(0, limit))
                    .toList();
        }

        @Override
        public List<ItemBrandOrTagMatch> findActiveItemsMatchingBrandOrTagInQuestion(
                UUID householdId, String question, int limit
        ) {
            findActiveItemsMatchingBrandOrTagInQuestionCalls++;
            String normalized = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
            return brandOrTagMatches.stream()
                    .filter(match -> {
                        boolean brand = match.matchedBrandName() != null && !match.matchedBrandName().isBlank()
                                && normalized.contains(match.matchedBrandName().toLowerCase(java.util.Locale.ROOT));
                        boolean tag = match.matchedTagName() != null && !match.matchedTagName().isBlank()
                                && normalized.contains(match.matchedTagName().toLowerCase(java.util.Locale.ROOT));
                        return brand || tag;
                    })
                    .limit(Math.max(0, limit))
                    .toList();
        }

        @Override
        public Map<UUID, String> unitNames(UUID householdId, Collection<UUID> unitIds) {
            return Map.of();
        }

        @Override
        public ItemDumpPage dumpItems(UUID householdId, OffsetDateTime cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubInventoryApi implements InventoryApi {
        List<LotFlat> lots = List.of();
        int lotsOfItemCalls;
        int findLotsMatchingQuestionCalls;

        @Override
        public Optional<StockPositionInfo> findStockPosition(UUID householdId, UUID lotId, UUID locationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<StockPositionInfo> stockPositionsOfItem(UUID householdId, UUID itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MovementInfo> movementsOfLot(UUID householdId, UUID lotId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LotInfo> lotsOfItem(UUID householdId, UUID itemId) {
            lotsOfItemCalls++;
            return lots.stream()
                    .map(lot -> new LotInfo(
                            lot.lotId(), lot.itemId(), null, BigDecimal.ONE,
                            lot.lotNumber(), lot.serialNumber()))
                    .toList();
        }

        @Override
        public Optional<LotFlat> findLot(UUID householdId, UUID lotId) {
            return lots.stream().filter(lot -> lot.lotId().equals(lotId)).findFirst();
        }

        @Override
        public BigDecimal currentTotalStockOfItem(UUID householdId, UUID itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LotQuestionMatch> findLotsMatchingQuestion(UUID householdId, String question, int limit) {
            findLotsMatchingQuestionCalls++;
            String normalized = question == null ? "" : question.toLowerCase(java.util.Locale.ROOT);
            return lots.stream()
                    .filter(lot -> {
                        boolean lotNumberMatches = lot.lotNumber() != null && !lot.lotNumber().isBlank()
                                && normalized.contains(lot.lotNumber().toLowerCase(java.util.Locale.ROOT));
                        boolean serialMatches = lot.serialNumber() != null && !lot.serialNumber().isBlank()
                                && normalized.contains(lot.serialNumber().toLowerCase(java.util.Locale.ROOT));
                        return lotNumberMatches || serialMatches;
                    })
                    .limit(Math.max(0, limit))
                    .map(lot -> new LotQuestionMatch(
                            lot.lotId(), lot.itemId(), "牛奶", lot.lotNumber(), lot.serialNumber()))
                    .toList();
        }

        @Override
        public List<LotQuantitySnapshot> findExpiringLots(
                UUID householdId, java.time.LocalDate today, java.time.LocalDate horizon,
                UUID itemId, UUID lotId, int limit
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LotQuantitySnapshot> findExpiredLots(
                UUID householdId, java.time.LocalDate today, UUID itemId, UUID lotId, int limit
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LowStockSnapshot> findLowStockItems(UUID householdId, UUID itemId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<LocationStockPositionSnapshot> findStockPositionsInLocations(
                UUID householdId, Collection<UUID> locationIds, String itemNameContains, int limit
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<MovementInfo> findRecentMovementsOfItem(
                UUID householdId, UUID itemId, UUID lotId, UUID locationId, int limit
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageDump<StockPositionDump> dumpStockPositions(UUID householdId, OffsetDateTime cursor, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PageDump<MovementDump> dumpMovements(UUID householdId, OffsetDateTime cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubLocationApi implements LocationApi {
        List<LocationNode> roots = List.of();
        int treeCalls;

        @Override
        public LocationInfo requireLocation(UUID householdId, UUID locationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markReferenced(UUID householdId, UUID locationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public LocationTree tree(UUID householdId) {
            treeCalls++;
            return new LocationTree(roots);
        }

        @Override
        public LocationDumpPage dumpTree(UUID householdId, OffsetDateTime cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
