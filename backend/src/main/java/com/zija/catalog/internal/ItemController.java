package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.catalog.internal.persistence.ItemEntity;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/v1/items")
class ItemController {

    private final ItemService itemService;
    private final HouseholdApi householdApi;

    ItemController(ItemService itemService, HouseholdApi householdApi) {
        this.itemService = itemService;
        this.householdApi = householdApi;
    }

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

    record VersionRequest(Integer version) {}
}
