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
    void blankSerialDoesNotMatchUnrelatedQuestion() {
        inventoryApi.lots = List.of(
                lot(LOT_A, "", "LOT-A"),
                lot(LOT_B, "SN-002", "LOT-B"));

        var plan = planner.plan(HOUSEHOLD_ID, request("厨房还有多少东西？", "HOUSEHOLD_FACT"));

        assertThat(plan.needsConfirmation()).isFalse();
        assertThat(plan.candidates()).isEmpty();
        assertThat(plan.target()).isNull();
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
    }

    private static CatalogApi.ItemInfo item(String name) {
        return new CatalogApi.ItemInfo(
                ITEM_ID, HOUSEHOLD_ID, name, "CONSUMABLE", null, null, UNIT_ID, null, "ACTIVE",
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
        Map<UUID, String> itemNames = Map.of();

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
            return items;
        }

        @Override
        public ItemDumpPage dumpItems(UUID householdId, OffsetDateTime cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubInventoryApi implements InventoryApi {
        List<LotFlat> lots = List.of();

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
            return lots.stream()
                    .map(lot -> new LotInfo(lot.lotId(), lot.itemId(), null, BigDecimal.ONE))
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
            return new LocationTree(roots);
        }

        @Override
        public LocationDumpPage dumpTree(UUID householdId, OffsetDateTime cursor, int limit) {
            throw new UnsupportedOperationException();
        }
    }
}
