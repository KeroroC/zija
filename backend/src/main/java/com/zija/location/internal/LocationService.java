package com.zija.location.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.location.LocationApi;
import com.zija.location.internal.event.LocationEventPublisher;
import com.zija.location.internal.exception.LocationCycleException;
import com.zija.location.internal.exception.LocationHasChildrenException;
import com.zija.location.internal.exception.LocationReferencedException;
import com.zija.location.internal.exception.LocationVersionConflictException;
import com.zija.location.internal.persistence.LocationDumpMapper;
import com.zija.location.internal.persistence.LocationEntity;
import com.zija.location.internal.persistence.LocationMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 存储位置管理服务，实现 {@link LocationApi} 接口。
 * <p>
 * 负责管理家庭物品的存储位置树形结构，支持位置的创建、重命名、移动和删除。
 * 位置支持多级父子关系，移动操作包含循环引用检测，删除操作要求无子节点且未被引用。
 * 通过 {@code everReferenced} 标记追踪位置是否曾被库存记录引用，防止误删活跃位置。
 */
@Service
class LocationService implements LocationApi {

    private final LocationMapper locationMapper;
    private final LocationDumpMapper locationDumpMapper;
    private final SystemApi systemApi;
    private final LocationEventPublisher eventPublisher;

    LocationService(LocationMapper locationMapper, LocationDumpMapper locationDumpMapper,
                    SystemApi systemApi, LocationEventPublisher eventPublisher) {
        this.locationMapper = locationMapper;
        this.locationDumpMapper = locationDumpMapper;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询指定位置，不存在或不属于该家庭时抛出异常。
     */
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

    /**
     * 获取指定家庭的完整位置树结构。
     */
    @Override
    @Transactional(readOnly = true)
    public LocationTree tree(UUID householdId) {
        List<LocationEntity> all = locationMapper.findTree(householdId);
        return buildTree(all);
    }

    @Override
    @Transactional(readOnly = true)
    public LocationDumpPage dumpTree(UUID householdId, OffsetDateTime cursor, int limit) {
        var items = locationDumpMapper.dumpTree(householdId, cursor, limit);
        OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).updatedAt();
        boolean hasMore = items.size() == limit;
        return new LocationDumpPage(items, nextCursor, hasMore);
    }

    /**
     * 创建存储位置，校验父位置的有效性。
     */
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
        eventPublisher.publishLocationChanged(householdId, entity.getId(), "CREATED", parentId);
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
        eventPublisher.publishLocationChanged(householdId, id, "RENAMED", entity.getParentId());
        return entity;
    }

    /**
     * 移动位置到新的父节点下，包含循环引用检测，使用乐观锁。
     */
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
        eventPublisher.publishLocationChanged(householdId, id, "MOVED", targetParentId);
    }

    /**
     * 删除位置，要求无子节点且从未被库存记录引用。
     */
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
        eventPublisher.publishLocationChanged(householdId, id, "DELETED", entity.getParentId());
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
                action, ZijaAuditOutcome.SUCCESS, householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}
