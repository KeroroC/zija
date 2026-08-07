package com.zija.inventory.internal;

import com.zija.inventory.internal.event.InventoryEventPublisher;
import com.zija.inventory.internal.exception.InventoryInsufficientStockException;
import com.zija.inventory.internal.persistence.LotEntity;
import com.zija.inventory.internal.persistence.LotMapper;
import com.zija.inventory.internal.persistence.MovementEntity;
import com.zija.inventory.internal.persistence.MovementMapper;
import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import com.zija.inventory.internal.persistence.StocktakeEntity;
import com.zija.inventory.internal.persistence.StocktakeItemEntity;
import com.zija.inventory.internal.persistence.StocktakeItemMapper;
import com.zija.inventory.internal.persistence.StocktakeMapper;
import com.zija.location.LocationApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Defensive unit tests for {@link StocktakeService#confirm} that simulate failure modes not reachable
 * through normal API calls because of DB CHECK constraints (ck_inventory_stocktake_actual_nonneg,
 * ck_inventory_stock_position_nonneg). Each test uses a mocked {@link StockPositionMapper} so the
 * otherwise-blocked subtraction failure can be exercised.
 *
 * <p>These guard the contract that {@code subtractIfSufficient} returning 0 must produce an
 * exception and leave no {@code ADJUSTMENT} movement behind — matching the pattern used in
 * {@link StockCommandService} (consume/loss/transfer) and {@link ReversalService}.
 */
class StocktakeServiceDefensiveTest {

    private StocktakeMapper stocktakeMapper;
    private StocktakeItemMapper stocktakeItemMapper;
    private StockPositionMapper stockPositionMapper;
    private LotService lotService;
    private LotMapper lotMapper;
    private MovementMapper movementMapper;
    private LocationApi locationApi;
    private SystemApi systemApi;
    private InventoryEventPublisher eventPublisher;

    private StocktakeService service;

    private final UUID householdId = UUID.randomUUID();
    private final UUID stocktakeId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        stocktakeMapper = mock(StocktakeMapper.class);
        stocktakeItemMapper = mock(StocktakeItemMapper.class);
        stockPositionMapper = mock(StockPositionMapper.class);
        lotService = mock(LotService.class);
        lotMapper = mock(LotMapper.class);
        movementMapper = mock(MovementMapper.class);
        locationApi = mock(LocationApi.class);
        systemApi = mock(SystemApi.class);
        eventPublisher = mock(InventoryEventPublisher.class);

        service = new StocktakeService(
                stocktakeMapper, stocktakeItemMapper, stockPositionMapper,
                lotService, lotMapper, movementMapper,
                locationApi, systemApi, eventPublisher);

        // Step 1-2: lock stocktake + version check pass
        StocktakeEntity stocktake = new StocktakeEntity();
        stocktake.setId(stocktakeId);
        stocktake.setHouseholdId(householdId);
        stocktake.setStatus("DRAFT");
        stocktake.setVersion(1);
        when(stocktakeMapper.lockById(householdId, stocktakeId)).thenReturn(stocktake);
        when(stocktakeMapper.updateById(any(StocktakeEntity.class))).thenReturn(1);

        // Step 3: one item returned from lockByStocktake
        StocktakeItemEntity item = new StocktakeItemEntity();
        item.setId(UUID.randomUUID());
        item.setStocktakeId(stocktakeId);
        item.setHouseholdId(householdId);
        item.setLotId(lotId);
        item.setLocationId(locationId);
        item.setBookQuantity(BigDecimal.TEN);
        item.setActualQuantity(BigDecimal.valueOf(7));    // deficit: delta=-3
        item.setPositionRevision(1L);
        item.setReason("盘点少了3个");
        when(stocktakeItemMapper.lockByStocktake(householdId, stocktakeId))
                .thenReturn(List.of(item));

        // Step 4: staleness check passes (sp.quantity == bookQuantity, revision matches)
        StockPositionEntity sp = new StockPositionEntity();
        sp.setId(UUID.randomUUID());
        sp.setHouseholdId(householdId);
        sp.setLotId(lotId);
        sp.setLocationId(locationId);
        sp.setQuantity(BigDecimal.TEN);
        sp.setRevision(1L);
        when(stockPositionMapper.lockOne(householdId, lotId, locationId)).thenReturn(sp);

        // Lot lookup for movement generation
        LotEntity lot = new LotEntity();
        lot.setId(lotId);
        lot.setItemId(itemId);
        when(lotMapper.selectById(lotId)).thenReturn(lot);
    }

    /**
     * Core regression: when {@code subtractIfSufficient} returns 0, the service MUST throw
     * {@link InventoryInsufficientStockException} and MUST NOT insert an {@code ADJUSTMENT} movement.
     * Without this check the stocktake ledger permanently diverges from inventory_stock_position.
     */
    @Test
    void confirm_deficitSubtractReturnsZero_throwsAndInsertsNoMovement() {
        when(stockPositionMapper.subtractIfSufficient(eq(householdId), eq(lotId), eq(locationId), any()))
                .thenReturn(0);

        assertThatThrownBy(() -> service.confirm(householdId, stocktakeId, 1, accountId))
                .isInstanceOf(InventoryInsufficientStockException.class);

        // The whole point of the fix: no ADJUSTMENT movement is created when subtraction fails.
        verify(movementMapper, never()).insert(any(MovementEntity.class));
        verify(eventPublisher, never()).publish(any());
    }

    /**
     * Sanity / symmetry: when the subtraction succeeds, an {@code ADJUSTMENT} movement IS inserted.
     * Locks in the happy path so the defensive throw doesn't regress the normal flow.
     */
    @Test
    void confirm_deficitSubtractSucceeds_insertsAdjustmentMovement() {
        when(stockPositionMapper.subtractIfSufficient(eq(householdId), eq(lotId), eq(locationId), any()))
                .thenReturn(1);

        service.confirm(householdId, stocktakeId, 1, accountId);

        verify(movementMapper, times(1)).insert(any(MovementEntity.class));
        verify(eventPublisher, times(1)).publish(any());
    }
}