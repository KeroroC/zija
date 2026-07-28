package com.zija.location.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.location.internal.event.LocationEventPublisher;
import com.zija.location.internal.exception.LocationCycleException;
import com.zija.location.internal.exception.LocationHasChildrenException;
import com.zija.location.internal.exception.LocationReferencedException;
import com.zija.location.internal.exception.LocationVersionConflictException;
import com.zija.location.internal.persistence.LocationDumpMapper;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LocationServiceTest {

    private LocationMapper locationMapper;
    private LocationDumpMapper locationDumpMapper;
    private SystemApi systemApi;
    private LocationEventPublisher eventPublisher;
    private LocationService service;

    private final UUID householdId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        locationMapper = mock(LocationMapper.class);
        locationDumpMapper = mock(LocationDumpMapper.class);
        systemApi = mock(SystemApi.class);
        eventPublisher = mock(LocationEventPublisher.class);
        service = new LocationService(locationMapper, locationDumpMapper, systemApi, eventPublisher);
    }

    @Test
    void createLocationSetsNormalizedName() {
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        var entity = service.createLocation(householdId, "  厨房  ", null, 0);

        var captor = org.mockito.ArgumentCaptor.forClass(LocationEntity.class);
        verify(locationMapper).insert(captor.capture());
        var inserted = captor.getValue();

        assertThat(inserted.getName()).isEqualTo("厨房");
        assertThat(inserted.getNameNormalized()).isEqualTo("厨房");
        assertThat(inserted.getHouseholdId()).isEqualTo(householdId);
        assertThat(inserted.getParentId()).isNull();
        assertThat(inserted.getEverReferenced()).isFalse();
    }

    @Test
    void renameLocationUpdatesNameWithOptimisticLock() {
        UUID locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setName("旧名字");
        entity.setNameNormalized("旧名字");
        entity.setVersion(1);
        when(locationMapper.selectById(locationId)).thenReturn(entity);
        when(locationMapper.updateById(any(LocationEntity.class))).thenReturn(1);

        var updated = service.renameLocation(householdId, locationId, "新名字", 1);

        assertThat(updated.getName()).isEqualTo("新名字");
        assertThat(updated.getNameNormalized()).isEqualTo("新名字");
        verify(locationMapper).updateById(entity);
    }

    @Test
    void moveLocationRejectsMovingToSelf() {
        UUID locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setVersion(0);
        when(locationMapper.selectById(locationId)).thenReturn(entity);

        assertThatThrownBy(() -> service.moveLocation(householdId, locationId, locationId, 0, 0))
                .isInstanceOf(LocationCycleException.class);

        verify(locationMapper, never()).updateById(any(LocationEntity.class));
    }

    @Test
    void moveLocationRejectsMovingToDescendant() {
        UUID locationId = UUID.randomUUID();
        UUID descendantId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setVersion(0);
        when(locationMapper.selectById(locationId)).thenReturn(entity);
        when(locationMapper.findDescendantIds(locationId, householdId))
                .thenReturn(List.of(descendantId));

        assertThatThrownBy(() -> service.moveLocation(householdId, locationId, descendantId, 0, 0))
                .isInstanceOf(LocationCycleException.class);

        verify(locationMapper, never()).updateById(any(LocationEntity.class));
    }

    @Test
    void deleteLocationRejectsWhenHasChildren() {
        UUID locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setEverReferenced(false);
        entity.setVersion(0);
        when(locationMapper.selectById(locationId)).thenReturn(entity);
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.deleteLocation(householdId, locationId, 0))
                .isInstanceOf(LocationHasChildrenException.class);

        verify(locationMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void deleteLocationRejectsWhenEverReferencedIsTrue() {
        UUID locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setEverReferenced(true);
        entity.setVersion(0);
        when(locationMapper.selectById(locationId)).thenReturn(entity);
        when(locationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> service.deleteLocation(householdId, locationId, 0))
                .isInstanceOf(LocationReferencedException.class);

        verify(locationMapper, never()).deleteById(any(UUID.class));
    }

    @Test
    void renameLocationWithStaleVersionThrowsConflict() {
        UUID locationId = UUID.randomUUID();
        var entity = new LocationEntity();
        entity.setId(locationId);
        entity.setHouseholdId(householdId);
        entity.setName("旧名字");
        entity.setVersion(5);
        when(locationMapper.selectById(locationId)).thenReturn(entity);
        when(locationMapper.updateById(any(LocationEntity.class))).thenReturn(0);

        assertThatThrownBy(() -> service.renameLocation(householdId, locationId, "新名字", 3))
                .isInstanceOf(LocationVersionConflictException.class);
    }
}
