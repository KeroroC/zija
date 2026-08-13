package com.zija.inventory.internal;

import com.zija.inventory.internal.persistence.StockPositionEntity;
import com.zija.inventory.internal.persistence.StockPositionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockPositionsTest {

    private final StockPositionMapper mapper = mock(StockPositionMapper.class);
    private final UUID householdId = UUID.randomUUID();
    private final UUID lotId = UUID.randomUUID();
    private final UUID locationId = UUID.randomUUID();

    @Test
    void lockOrCreate_returnsExistingRowWithoutInsert() {
        var existing = new StockPositionEntity();
        existing.setId(UUID.randomUUID());
        existing.setQuantity(new BigDecimal("3"));
        when(mapper.lockOne(householdId, lotId, locationId)).thenReturn(existing);

        StockPositionEntity result = StockPositions.lockOrCreate(mapper, householdId, lotId, locationId);

        assertThat(result).isSameAs(existing);
        verify(mapper, never()).insert(org.mockito.ArgumentMatchers.any(StockPositionEntity.class));
    }

    @Test
    void lockOrCreate_insertsZeroQuantityRowWhenMissing() {
        when(mapper.lockOne(householdId, lotId, locationId)).thenReturn(null);

        StockPositionEntity result = StockPositions.lockOrCreate(mapper, householdId, lotId, locationId);

        ArgumentCaptor<StockPositionEntity> captor = ArgumentCaptor.forClass(StockPositionEntity.class);
        verify(mapper).insert(captor.capture());
        StockPositionEntity inserted = captor.getValue();
        assertThat(result).isSameAs(inserted);
        assertThat(inserted.getHouseholdId()).isEqualTo(householdId);
        assertThat(inserted.getLotId()).isEqualTo(lotId);
        assertThat(inserted.getLocationId()).isEqualTo(locationId);
        assertThat(inserted.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(inserted.getRevision()).isZero();
        assertThat(inserted.getId()).isNotNull();
        assertThat(inserted.getCreatedAt()).isNotNull();
        assertThat(inserted.getUpdatedAt()).isNotNull();
    }
}
