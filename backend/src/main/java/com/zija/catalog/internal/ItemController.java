package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.catalog.internal.persistence.ItemMapper;
import com.zija.file.FileApi;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import com.zija.system.SystemApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
 * 物品管理控制器，提供物品（Item）的增删改查及封面图管理 REST API。
 *
 * <p>所有端点均要求当前用户为家庭的活跃成员（{@link RequireMember}）。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code GET    /api/v1/items}           — 分页查询物品列表（支持搜索、筛选、排序）</li>
 *   <li>{@code POST   /api/v1/items}           — 创建物品</li>
 *   <li>{@code GET    /api/v1/items/{id}}       — 查询单个物品详情</li>
 *   <li>{@code PUT    /api/v1/items/{id}}       — 更新物品</li>
 *   <li>{@code POST   /api/v1/items/{id}/archive}  — 归档物品</li>
 *   <li>{@code POST   /api/v1/items/{id}/restore}  — 恢复已归档物品</li>
 *   <li>{@code POST   /api/v1/items/{id}/cover}    — 上传物品封面图</li>
 *   <li>{@code DELETE /api/v1/items/{id}/cover}    — 移除物品封面图</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/items")
class ItemController {

    private final ItemService itemService;
    private final HouseholdApi householdApi;
    private final FileApi fileApi;
    private final ItemMapper itemMapper;
    private final SystemApi systemApi;

    ItemController(ItemService itemService, HouseholdApi householdApi,
                   FileApi fileApi, ItemMapper itemMapper, SystemApi systemApi) {
        this.itemService = itemService;
        this.householdApi = householdApi;
        this.fileApi = fileApi;
        this.itemMapper = itemMapper;
        this.systemApi = systemApi;
    }

    /**
     * 分页查询物品列表，支持按关键词、管理类型、分类、品牌、标签、状态筛选及排序。
     *
     * @return 包含 items、total、page、pageSize 的分页结果
     */
    @RequireMember
    @GetMapping
    Map<String, Object> listItems(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String managementType,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) UUID tagId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort
    ) {
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = itemService.listItems(member.householdId(), q, managementType,
                categoryId, brandId, tagId, status, page, pageSize, sort);

        List<Map<String, Object>> items = result.getRecords().stream()
                .map(e -> toItemResponse(e, itemService.findItemTagIds(e.getId())))
                .toList();

        var response = new LinkedHashMap<String, Object>();
        response.put("items", items);
        response.put("total", result.getTotal());
        response.put("page", page);
        response.put("pageSize", pageSize);
        return response;
    }

    /**
     * 创建物品。
     *
     * @return 新创建的物品信息
     */
    @RequireMember
    @PostMapping
    Map<String, Object> createItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateItemRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.createItem(
                member.householdId(), request.name(), request.managementType(),
                request.categoryId(), request.brandId(), request.unitId(), request.memo(),
                request.expiryReminderMode(), request.expiryReminderDays(),
                request.lowStockMode(), request.lowStockThreshold(),
                request.tagIds()
        );
        return toItemResponse(entity, request.tagIds());
    }

    /**
     * 查询单个物品详情。
     *
     * @param id 物品 ID
     * @return 物品详细信息
     * @throws CatalogArchivedDictionaryException 物品不存在时抛出
     */
    @RequireMember
    @GetMapping("/{id}")
    Map<String, Object> getItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.findItem(member.householdId(), id);
        if (entity == null) {
            throw new CatalogArchivedDictionaryException("item", id);
        }
        var tagIds = itemService.findItemTagIds(id);
        return toItemResponse(entity, tagIds);
    }

    /**
     * 更新物品信息，支持乐观锁版本控制。
     *
     * @param id 物品 ID
     * @return 更新后的物品信息
     */
    @RequireMember
    @PutMapping("/{id}")
    Map<String, Object> updateItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateItemRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = itemService.updateItem(
                member.householdId(), id,
                request.name(), request.categoryId(), request.brandId(), request.unitId(),
                request.memo(), request.coverFileId(),
                request.expiryReminderMode(), request.expiryReminderDays(),
                request.lowStockMode(), request.lowStockThreshold(),
                request.tagIds(), request.version()
        );
        return toItemResponse(entity, itemService.findItemTagIds(id));
    }

    /**
     * 归档物品，归档后物品不再出现在默认列表中。
     *
     * @param id 物品 ID
     */
    @RequireMember
    @PostMapping("/{id}/archive")
    void archiveItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        itemService.archiveItem(member.householdId(), id, member.accountId(), request.version());
    }

    /**
     * 恢复已归档的物品。
     *
     * @param id 物品 ID
     */
    @RequireMember
    @PostMapping("/{id}/restore")
    void restoreItem(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        itemService.restoreItem(member.householdId(), id, request.version());
    }

    /**
     * 上传物品封面图。若已有封面图则替换旧文件。
     *
     * @param id 物品 ID
     * @return 新封面文件信息（id、url、媒体类型、文件名、大小、SHA-256）
     */
    @RequireMember
    @PostMapping("/{id}/cover")
    Map<String, Object> uploadCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @NotNull @RequestParam Integer version
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var item = itemService.findItem(member.householdId(), id);
        if (item == null) {
            throw new CatalogArchivedDictionaryException("item", id);
        }

        var newFileInfo = fileApi.store(
                member.householdId(), file.getBytes(),
                file.getOriginalFilename(), file.getContentType()
        );

        if (item.getCoverFileId() != null) {
            fileApi.release(member.householdId(), item.getCoverFileId());
        }

        item.setCoverFileId(newFileInfo.id());
        item.setVersion(version);
        if (itemMapper.updateById(item) == 0) {
            throw new CatalogVersionConflictException();
        }

        fileApi.retain(member.householdId(), newFileInfo.id());

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "ITEM_COVER_UPLOADED", "SUCCESS", member.householdId(),
                null, null, null, null,
                Map.of("id", id.toString(), "fileId", newFileInfo.id().toString())
        ));

        return Map.of(
                "id", newFileInfo.id(),
                "url", "/api/v1/files/" + newFileInfo.id() + "/content",
                "detectedMediaType", newFileInfo.detectedMediaType(),
                "originalFilename", newFileInfo.originalFilename(),
                "byteSize", newFileInfo.byteSize(),
                "sha256", newFileInfo.sha256(),
                "version", item.getVersion()
        );
    }

    /**
     * 移除物品封面图，同时释放对应的文件引用。
     *
     * @param id 物品 ID
     */
    @RequireMember
    @DeleteMapping("/{id}/cover")
    void removeCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var item = itemService.findItem(member.householdId(), id);
        if (item == null || item.getCoverFileId() == null) {
            throw new CatalogArchivedDictionaryException("item", id);
        }

        fileApi.release(member.householdId(), item.getCoverFileId());

        var wrapper = new UpdateWrapper<ItemEntity>()
                .eq("id", id)
                .eq("version", request.version())
                .set("cover_file_id", null)
                .set("version", request.version() + 1);
        if (itemMapper.update(null, wrapper) == 0) {
            throw new CatalogVersionConflictException();
        }

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "ITEM_COVER_REMOVED", "SUCCESS", member.householdId(),
                null, null, null, null,
                Map.of("id", id.toString())
        ));
    }

    private Map<String, Object> toItemResponse(ItemEntity entity, List<UUID> tagIds) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", entity.getId());
        map.put("householdId", entity.getHouseholdId());
        map.put("name", entity.getName());
        map.put("managementType", entity.getManagementType());
        map.put("categoryId", entity.getCategoryId());
        map.put("brandId", entity.getBrandId());
        map.put("unitId", entity.getUnitId());
        map.put("coverFileId", entity.getCoverFileId());
        if (entity.getCoverFileId() != null) {
            map.put("coverUrl", "/api/v1/files/" + entity.getCoverFileId() + "/content");
        }
        map.put("memo", entity.getMemo());
        map.put("expiryReminderMode", entity.getExpiryReminderMode());
        map.put("expiryReminderDays", entity.getExpiryReminderDays());
        map.put("lowStockMode", entity.getLowStockMode());
        map.put("lowStockThreshold", entity.getLowStockThreshold());
        map.put("status", entity.getStatus());
        map.put("tagIds", tagIds != null ? tagIds : List.of());
        map.put("version", entity.getVersion());
        map.put("createdAt", entity.getCreatedAt());
        map.put("updatedAt", entity.getUpdatedAt());
        return map;
    }

    // --- DTOs ---

    record CreateItemRequest(
            @NotBlank String name,
            @NotBlank String managementType,
            UUID categoryId, UUID brandId,
            @NotNull UUID unitId,
            String memo,
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            String lowStockMode,
            BigDecimal lowStockThreshold,
            List<UUID> tagIds
    ) {}

    record UpdateItemRequest(
            @NotBlank String name,
            UUID categoryId, UUID brandId, UUID unitId,
            String memo, UUID coverFileId,
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            String lowStockMode,
            BigDecimal lowStockThreshold,
            List<UUID> tagIds,
            @NotNull Integer version
    ) {}

    record VersionRequest(Integer version) {}
}
