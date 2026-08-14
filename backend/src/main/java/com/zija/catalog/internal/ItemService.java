package com.zija.catalog.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.shared.ZijaChangeType;
import com.zija.shared.ZijaRecordStatus;
import com.zija.shared.ZijaReminderMode;
import com.zija.catalog.CatalogApi;
import com.zija.catalog.internal.event.CatalogEventPublisher;
import com.zija.catalog.internal.exception.CatalogArchivedDictionaryException;
import com.zija.catalog.internal.exception.CatalogUnitPrecisionInvalidException;
import com.zija.catalog.internal.exception.CatalogVersionConflictException;
import com.zija.catalog.internal.persistence.*;
import com.zija.file.FileApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 物品（Item）管理服务，实现 {@link CatalogApi} 接口。
 * <p>
 * 负责家庭资产物品的完整生命周期管理，包括创建、归档、恢复和更新操作。
 * 支持物品与分类、品牌、单位、标签的关联，以及过期提醒和低库存预警配置。
 * 所有操作均限定在指定家庭（householdId）范围内，并通过乐观锁保证并发安全。
 */
@Service
class ItemService implements CatalogApi {

    private final ItemMapper itemMapper;
    private final ItemDumpMapper itemDumpMapper;
    private final UnitMapper unitMapper;
    private final CategoryMapper categoryMapper;
    private final BrandMapper brandMapper;
    private final TagMapper tagMapper;
    private final FileApi fileApi;
    private final SystemApi systemApi;
    private final CatalogEventPublisher eventPublisher;

    ItemService(
            ItemMapper itemMapper,
            ItemDumpMapper itemDumpMapper,
            UnitMapper unitMapper,
            CategoryMapper categoryMapper,
            BrandMapper brandMapper,
            TagMapper tagMapper,
            FileApi fileApi,
            SystemApi systemApi,
            CatalogEventPublisher eventPublisher
    ) {
        this.itemMapper = itemMapper;
        this.itemDumpMapper = itemDumpMapper;
        this.unitMapper = unitMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.tagMapper = tagMapper;
        this.fileApi = fileApi;
        this.systemApi = systemApi;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 查询指定物品，不存在或不属于该家庭时抛出异常。
     */
    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.findByIdFull(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    /**
     * 查询指定物品且要求状态为 ACTIVE，归档物品会抛出异常。
     */
    @Override
    @Transactional(readOnly = true)
    public ItemInfo requireActiveItem(UUID householdId, UUID itemId) {
        var entity = itemMapper.findByIdFull(itemId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        if (!ZijaRecordStatus.ACTIVE.equals(entity.getStatus())) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }
        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public UnitInfo requireUnit(UUID householdId, UUID unitId) {
        var entity = unitMapper.selectById(unitId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        return new UnitInfo(entity.getId(), entity.getHouseholdId(), entity.getName(),
                entity.getDecimalScale(), entity.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemInfo> listActiveItems(UUID householdId) {
        var wrapper = new LambdaQueryWrapper<ItemEntity>()
                .eq(ItemEntity::getHouseholdId, householdId)
                .eq(ItemEntity::getStatus, ZijaRecordStatus.ACTIVE);
        return itemMapper.selectList(wrapper).stream().map(this::toInfo).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> itemNames(UUID householdId, Collection<UUID> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Map.of();
        var wrapper = new LambdaQueryWrapper<ItemEntity>()
                .eq(ItemEntity::getHouseholdId, householdId)
                .in(ItemEntity::getId, itemIds);
        Map<UUID, String> result = new HashMap<>();
        for (var entity : itemMapper.selectList(wrapper)) {
            result.put(entity.getId(), entity.getName());
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ItemDumpPage dumpItems(UUID householdId, OffsetDateTime cursor, int limit) {
        var items = itemDumpMapper.dumpItems(householdId, cursor, limit);
        OffsetDateTime nextCursor = items.isEmpty() ? cursor : items.get(items.size() - 1).updatedAt();
        boolean hasMore = items.size() == limit;
        return new ItemDumpPage(items, nextCursor, hasMore);
    }

    /**
     * 创建新物品，校验单位、分类、品牌、标签的有效性，并检查数量精度。
     */
    @Transactional
    public ItemEntity createItem(
            UUID householdId, String name, String managementType,
            UUID categoryId, UUID brandId, UUID unitId, String memo,
            String expiryReminderMode, List<Short> expiryReminderDays,
            String lowStockMode, BigDecimal lowStockThreshold,
            List<UUID> tagIds
    ) {
        var unit = requireActiveUnit(householdId, unitId);
        if (categoryId != null) {
            requireActiveDictionary(categoryMapper, categoryId, householdId, "category");
        }
        if (brandId != null) {
            requireActiveDictionary(brandMapper, brandId, householdId, "brand");
        }
        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                requireActiveDictionary(tagMapper, tagId, householdId, "tag");
            }
        }
        if (ZijaReminderMode.CUSTOM.equals(lowStockMode) && lowStockThreshold != null) {
            int scale = lowStockThreshold.stripTrailingZeros().scale();
            if (scale > unit.getDecimalScale()) {
                throw new CatalogUnitPrecisionInvalidException(scale, unit.getDecimalScale());
            }
        }

        var entity = new ItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setName(name);
        entity.setManagementType(managementType);
        entity.setCategoryId(categoryId);
        entity.setBrandId(brandId);
        entity.setUnitId(unitId);
        entity.setMemo(memo);
        entity.setExpiryReminderMode(expiryReminderMode);
        entity.setExpiryReminderDays(expiryReminderDays);
        entity.setLowStockMode(lowStockMode);
        entity.setLowStockThreshold(lowStockThreshold);
        entity.setStatus(ZijaRecordStatus.ACTIVE);
        entity.setVersion(0);
        itemMapper.insert(entity);

        if (tagIds != null) {
            for (UUID tagId : tagIds) {
                itemMapper.insertItemTag(householdId, entity.getId(), tagId);
            }
        }

        audit(householdId, SystemApi.AuditAction.ITEM_CREATED, entity.getId());
        eventPublisher.publishItemChanged(householdId, entity.getId(), ZijaChangeType.CREATED);
        return entity;
    }

    /**
     * 归档物品，记录归档时间和操作人。
     */
    @Transactional
    public void archiveItem(UUID householdId, UUID id, UUID accountId, Integer version) {
        var entity = requireItemEntity(householdId, id);
        entity.setStatus(ZijaRecordStatus.ARCHIVED);
        entity.setArchivedAt(OffsetDateTime.now());
        entity.setArchivedBy(accountId);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, SystemApi.AuditAction.ITEM_ARCHIVED, id);
        eventPublisher.publishItemChanged(householdId, id, ZijaRecordStatus.ARCHIVED);
    }

    @Transactional
    public void restoreItem(UUID householdId, UUID id, Integer version) {
        var entity = requireItemEntity(householdId, id);
        entity.setStatus(ZijaRecordStatus.ACTIVE);
        entity.setArchivedAt(null);
        entity.setArchivedBy(null);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        audit(householdId, SystemApi.AuditAction.ITEM_RESTORED, id);
        eventPublisher.publishItemChanged(householdId, id, ZijaChangeType.RESTORED);
    }

    @Transactional(readOnly = true)
    public ItemEntity findItem(UUID householdId, UUID id) {
        var entity = itemMapper.findByIdFull(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            return null;
        }
        return entity;
    }

    @Transactional(readOnly = true)
    public List<UUID> findItemTagIds(UUID itemId) {
        return itemMapper.findTagIdsByItemId(itemId);
    }

    /**
     * 分页查询物品列表，支持按名称、管理类型、分类、品牌、标签、状态筛选和排序。
     */
    @Transactional(readOnly = true)
    public IPage<ItemEntity> listItems(
            UUID householdId, String q, String managementType,
            UUID categoryId, UUID brandId, UUID tagId, String status,
            int page, int pageSize, String sort
    ) {
        return itemMapper.findPage(
                new Page<>(page, pageSize), householdId,
                q, managementType, categoryId, brandId, tagId, status, resolveSort(sort));
    }

    private String resolveSort(String sort) {
        if (sort == null) return "updated_at DESC, id DESC";
        return switch (sort) {
            case "name" -> "name ASC, id ASC";
            case "-name" -> "name DESC, id DESC";
            default -> "updated_at DESC, id DESC";
        };
    }

    /**
     * 更新物品信息，支持部分更新（仅更新非 null 字段），使用乐观锁防止并发冲突。
     */
    @Transactional
    public ItemEntity updateItem(
            UUID householdId, UUID id,
            String name, UUID categoryId, UUID brandId, UUID unitId,
            String memo, UUID coverFileId,
            String expiryReminderMode, List<Short> expiryReminderDays,
            String lowStockMode, BigDecimal lowStockThreshold,
            List<UUID> tagIds, Integer version
    ) {
        var entity = requireItemEntity(householdId, id);
        if (name != null) entity.setName(name.trim());
        if (memo != null) entity.setMemo(memo);
        if (coverFileId != null) entity.setCoverFileId(coverFileId);
        if (unitId != null) {
            requireActiveUnit(householdId, unitId);
            entity.setUnitId(unitId);
        }
        if (categoryId != null) {
            requireActiveDictionary(categoryMapper, categoryId, householdId, "category");
            entity.setCategoryId(categoryId);
        }
        if (brandId != null) {
            requireActiveDictionary(brandMapper, brandId, householdId, "brand");
            entity.setBrandId(brandId);
        }
        if (expiryReminderMode != null) {
            entity.setExpiryReminderMode(expiryReminderMode);
            entity.setExpiryReminderDays(expiryReminderDays);
        }
        if (lowStockMode != null) {
            if (ZijaReminderMode.CUSTOM.equals(lowStockMode) && lowStockThreshold != null) {
                var unit = requireActiveUnit(householdId, entity.getUnitId());
                int scale = lowStockThreshold.stripTrailingZeros().scale();
                if (scale > unit.getDecimalScale()) {
                    throw new CatalogUnitPrecisionInvalidException(scale, unit.getDecimalScale());
                }
            }
            entity.setLowStockMode(lowStockMode);
            entity.setLowStockThreshold(lowStockThreshold);
        }
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setVersion(version);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }
        if (tagIds != null) {
            itemMapper.deleteItemTags(id);
            for (UUID tagId : tagIds) {
                requireActiveDictionary(tagMapper, tagId, householdId, "tag");
                itemMapper.insertItemTag(householdId, id, tagId);
            }
        }
        audit(householdId, SystemApi.AuditAction.ITEM_UPDATED, id);
        eventPublisher.publishItemChanged(householdId, id, ZijaChangeType.UPDATED);
        return itemMapper.findByIdFull(id);
    }

    /**
     * 上传物品封面图，替换旧封面（如有）。整个操作在同一事务中，确保版本冲突时不会出现孤儿文件或封面丢失。
     *
     * <p>操作顺序：1. 存储新文件 → 2. 更新 item（乐观锁） → 3. retain 新文件 + release 旧文件。
     * 若步骤 2 版本冲突，事务回滚，新文件的 insert 也会回滚，不会产生孤儿。</p>
     *
     * @param householdId 家庭 ID
     * @param itemId 物品 ID
     * @param fileContent 文件内容
     * @param originalFilename 原始文件名
     * @param contentType 声明的媒体类型
     * @param version 乐观锁版本号
     * @return 新封面文件信息和更新后的版本
     */
    @Transactional
    public CoverResult uploadCover(
            UUID householdId, UUID itemId,
            byte[] fileContent, String originalFilename, String contentType,
            Integer version
    ) {
        var entity = requireItemEntity(householdId, itemId);
        UUID oldCoverFileId = entity.getCoverFileId();

        // 1. 存储新文件（refcount=0，在本事务中，若后续失败会回滚）
        var newFileInfo = fileApi.store(householdId, fileContent, originalFilename, contentType);

        // 2. 更新 item（乐观锁）
        entity.setCoverFileId(newFileInfo.id());
        entity.setVersion(version);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }

        // 3. 更新成功后，retain 新文件，release 旧文件
        fileApi.retain(householdId, newFileInfo.id());
        if (oldCoverFileId != null) {
            fileApi.release(householdId, oldCoverFileId);
        }

        audit(householdId, SystemApi.AuditAction.ITEM_COVER_UPLOADED, itemId);
        return new CoverResult(newFileInfo, version + 1);
    }

    /**
     * 上传封面结果。
     */
    public record CoverResult(FileApi.StoredFileInfo fileInfo, Integer newVersion) {}

    /**
     * 移除物品封面图，释放文件引用。整个操作在同一事务中，确保版本冲突时不会丢失文件。
     *
     * @param householdId 家庭 ID
     * @param itemId 物品 ID
     * @param version 乐观锁版本号
     */
    @Transactional
    public void removeCover(UUID householdId, UUID itemId, Integer version) {
        var entity = requireItemEntity(householdId, itemId);
        UUID oldCoverFileId = entity.getCoverFileId();
        if (oldCoverFileId == null) {
            throw new CatalogArchivedDictionaryException("item", itemId);
        }

        // 1. 先更新 item（乐观锁）
        entity.setCoverFileId(null);
        entity.setVersion(version);
        if (itemMapper.updateById(entity) == 0) {
            throw new CatalogVersionConflictException();
        }

        // 2. 更新成功后，释放旧文件
        fileApi.release(householdId, oldCoverFileId);

        audit(householdId, SystemApi.AuditAction.ITEM_COVER_REMOVED, itemId);
    }

    // --- Private helpers ---

    private ItemEntity requireItemEntity(UUID householdId, UUID id) {
        var entity = itemMapper.findByIdFull(id);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("item", id);
        }
        return entity;
    }

    private UnitEntity requireActiveUnit(UUID householdId, UUID unitId) {
        var entity = unitMapper.selectById(unitId);
        if (entity == null || !entity.getHouseholdId().equals(householdId)) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        if (!ZijaRecordStatus.ACTIVE.equals(entity.getStatus())) {
            throw new CatalogArchivedDictionaryException("unit", unitId);
        }
        return entity;
    }

    private <T> void requireActiveDictionary(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
                                              UUID id, UUID householdId, String type) {
        var entity = mapper.selectById(id);
        if (entity == null) {
            throw new CatalogArchivedDictionaryException(type, id);
        }
    }

    private ItemInfo toInfo(ItemEntity entity) {
        return new ItemInfo(
                entity.getId(), entity.getHouseholdId(), entity.getName(),
                entity.getManagementType(), entity.getCategoryId(), entity.getBrandId(),
                entity.getUnitId(), entity.getCoverFileId(), entity.getStatus(),
                entity.getExpiryReminderMode(),
                entity.getExpiryReminderDays(),
                entity.getLowStockMode(),
                entity.getLowStockThreshold()
        );
    }

    private void audit(UUID householdId, String action, UUID resourceId) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, ZijaAuditOutcome.SUCCESS, householdId, null, null, null, null,
                Map.of("id", resourceId.toString())
        ));
    }
}
