package com.zija.location.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.location.LocationApi;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
class LocationService implements LocationApi {

    private final LocationMapper locationMapper;
    private final SystemApi systemApi;

    LocationService(LocationMapper locationMapper, SystemApi systemApi) {
        this.locationMapper = locationMapper;
        this.systemApi = systemApi;
    }

    @Override
    @Transactional(readOnly = true)
    public LocationInfo requireLocation(UUID householdId, UUID locationId) {
        var entity = locationMapper.selectById(locationId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new LocationReferencedException();
        }
        return toInfo(entity);
    }

    @Override
    @Transactional
    public void markReferenced(UUID householdId, UUID locationId) {
        locationMapper.markReferenced(locationId, householdId);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationTree tree(UUID householdId) {
        List<LocationEntity> all = locationMapper.findTree(householdId);
        return buildTree(all);
    }

    @Transactional
    public LocationEntity createLocation(UUID householdId, String name, UUID parentId, int sortOrder) {
        if (parentId != null) {
            var parent = locationMapper.selectById(parentId);
            if (parent == null || !parent.getHouseholdId().equals(householdId)) {
                throw new LocationReferencedException();
            }
        }
        String normalized = normalizeName(name);
        var entity = new LocationEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setParentId(parentId);
        entity.setName(name.trim());
        entity.setNameNormalized(normalized);
        entity.setSortOrder(sortOrder);
        entity.setEverReferenced(false);
        entity.setVersion(0);
        locationMapper.insert(entity);
        audit(householdId, "LOCATION_CREATED", entity.getId());
        return entity;
    }

    @Transactional
    public LocationEntity renameLocation(UUID householdId, UUID id, String name, Integer version) {
        var entity = requireLocationEntity(householdId, id);
        entity.setName(name.trim());
        entity.setNameNormalized(normalizeName(name));
        if (locationMapper.updateById(entity) == 0) {
            throw new LocationVersionConflictException();
        }
        audit(householdId, "LOCATION_RENAMED", id);
        return entity;
    }

    @Transactional
    public void moveLocation(UUID householdId, UUID id, UUID targetParentId, int targetSortOrder, Integer version) {
        var entity = requireLocationEntity(householdId, id);
        if (targetParentId != null) {
            if (targetParentId.equals(id)) {
                throw new LocationCycleException();
            }
            var descendants = locationMapper.findDescendantIds(id, householdId);
            if (descendants.contains(targetParentId)) {
                throw new LocationCycleException();
            }
        }
        entity.setParentId(targetParentId);
        entity.setSortOrder(targetSortOrder);
        if (locationMapper.updateById(entity) == 0) {
            throw new LocationVersionConflictException();
        }
        audit(householdId, "LOCATION_MOVED", id);
    }

    @Transactional
    public void deleteLocation(UUID householdId, UUID id, Integer version) {
        var entity = requireLocationEntity(householdId, id);
        long childCount = locationMapper.selectCount(new LambdaQueryWrapper<LocationEntity>()
                .eq(LocationEntity::getParentId, id));
        if (childCount > 0) {
            throw new LocationHasChildrenException();
        }
        if (entity.getEverReferenced()) {
            throw new LocationReferencedException();
        }
        locationMapper.deleteById(id);
        audit(householdId, "LOCATION_DELETED", id);
    }

    // --- Helpers ---

    private LocationEntity requireLocationEntity(UUID householdId, UUID id) {
        var entity = locationMapper.selectById(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new LocationReferencedException();
        }
        return entity;
    }

    private LocationTree buildTree(List<LocationEntity> all) {
        Map<UUID, List<LocationEntity>> byParent = all.stream()
                .filter(e -> e.getParentId() != null)
                .collect(Collectors.groupingBy(LocationEntity::getParentId));

        List<LocationNode> roots = all.stream()
                .filter(e -> e.getParentId() == null)
                .map(e -> toNode(e, byParent))
                .toList();

        return new LocationTree(roots);
    }

    private LocationNode toNode(LocationEntity entity, Map<UUID, List<LocationEntity>> byParent) {
        List<LocationNode> children = byParent.getOrDefault(entity.getId(), List.of()).stream()
                .map(e -> toNode(e, byParent))
                .toList();
        return new LocationNode(
                entity.getId(), entity.getParentId(), entity.getName(),
                entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion(), children
        );
    }

    private LocationInfo toInfo(LocationEntity entity) {
        return new LocationInfo(
                entity.getId(), entity.getHouseholdId(), entity.getParentId(),
                entity.getName(), entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion()
        );
    }

    static String normalizeName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        String nfkc = java.text.Normalizer.normalize(trimmed, java.text.Normalizer.Form.NFKC);
        return nfkc.toLowerCase(Locale.ROOT);
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}
