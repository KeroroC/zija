package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.catalog.internal.exception.CatalogArchivedDictionaryException;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
 *   <li>{@code POST   /api/v1/items/{id}/cover}    — 上传并指定封面</li>
 *   <li>{@code PUT    /api/v1/items/{id}/cover}    — 指定已有合格附件为封面</li>
 *   <li>{@code DELETE /api/v1/items/{id}/cover}    — 取消封面指定（附件保留）</li>
 *   <li>{@code GET    /api/v1/items/{id}/attachments} — 列出物品附件</li>
 *   <li>{@code POST   /api/v1/items/{id}/attachments} — 上传附件挂到物品</li>
 *   <li>{@code PATCH  /api/v1/items/{id}/attachments/{fileId}/mount} — 改挂附件到物品</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/items")
class ItemController {

    private final ItemService itemService;
    private final HouseholdApi householdApi;

    ItemController(ItemService itemService, HouseholdApi householdApi) {
        this.itemService = itemService;
        this.householdApi = householdApi;
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
                request.memo(),
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
     * 上传一张新图片并指定为物品封面（先当附件再指定）。
     *
     * <p>若物品已有封面，必须携带 {@code oldCoverAction}：{@code KEEP} 留作普通附件（缺省值），
     * {@code RECYCLE} 送进回收站。物品版本冲突返回 409。</p>
     *
     * @return 新封面附件信息（id、名字、url、媒体类型、大小、版本）
     */
    @RequireMember
    @PostMapping("/{id}/cover")
    Map<String, Object> uploadCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file,
            @NotNull @RequestParam Integer version,
            @RequestParam(required = false) String oldCoverAction
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());

        var result = itemService.uploadCover(
                member.householdId(), id,
                file.getBytes(), file.getOriginalFilename(), file.getContentType(),
                oldCoverAction, version
        );

        var map = toAttachmentResponse(result.attachment());
        map.put("version", result.newVersion());
        return map;
    }

    /**
     * 把本物品上一份合格图片附件指定为封面（不重新上传）。
     *
     * <p>换封面时若携带 {@code oldCoverAction=RECYCLE}，旧封面附件进入回收站；缺省或 {@code KEEP} 则留下。
     * 物品版本冲突返回 409。</p>
     */
    @RequireMember
    @PutMapping("/{id}/cover")
    Map<String, Object> designateCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody DesignateCoverRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = itemService.designateCover(
                member.householdId(), id, request.fileId(), request.oldCoverAction(), request.version());
        var map = toAttachmentResponse(result.attachment());
        map.put("version", result.newVersion());
        return map;
    }

    /**
     * 取消封面指定（只取消指定，附件仍留在物品上，不送回收站）。
     */
    @RequireMember
    @DeleteMapping("/{id}/cover")
    void removeCover(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        itemService.removeCover(member.householdId(), id, request.version());
    }

    /**
     * 列出物品上的未删除附件。
     */
    @RequireMember
    @GetMapping("/{id}/attachments")
    Map<String, Object> listAttachments(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var attachments = itemService.listAttachments(member.householdId(), id);
        var response = new LinkedHashMap<String, Object>();
        response.put("items", attachments.stream().map(this::toAttachmentResponse).toList());
        response.put("total", (long) attachments.size());
        return response;
    }

    /**
     * 上传附件并挂到物品上（说明书、小票等；归档物品仍可操作）。
     */
    @RequireMember
    @PostMapping("/{id}/attachments")
    Map<String, Object> uploadAttachment(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = itemService.uploadAttachment(
                member.householdId(), id,
                file.getBytes(), file.getOriginalFilename(), file.getContentType());
        return toAttachmentResponse(info);
    }

    /**
     * 把已有附件改挂到本物品（挂到物品走本入口）。
     * 若该附件曾是某物品的封面，原物品的封面指定会被同步清除。
     */
    @RequireMember
    @PatchMapping("/{id}/attachments/{fileId}/mount")
    Map<String, Object> mountAttachment(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = itemService.mountAttachment(member.householdId(), id, fileId);
        return toAttachmentResponse(info);
    }

    private Map<String, Object> toAttachmentResponse(com.zija.file.FileApi.AttachmentInfo info) {
        var map = new LinkedHashMap<String, Object>();
        map.put("id", info.id());
        map.put("name", info.name());
        map.put("mediaType", info.mediaType());
        map.put("byteSize", info.byteSize());
        map.put("mountType", info.mountType());
        map.put("mountId", info.mountId());
        map.put("createdAt", info.createdAt());
        map.put("url", "/api/v1/files/" + info.id() + "/content");
        return map;
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
            String memo,
            String expiryReminderMode,
            List<Short> expiryReminderDays,
            String lowStockMode,
            BigDecimal lowStockThreshold,
            List<UUID> tagIds,
            @NotNull Integer version
    ) {}

    record VersionRequest(Integer version) {}

    record DesignateCoverRequest(
            @NotNull UUID fileId,
            @NotNull Integer version,
            String oldCoverAction
    ) {}
}
