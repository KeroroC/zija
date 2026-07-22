package com.zija.catalog.internal;

import com.zija.ZijaPrincipal;
import com.zija.catalog.internal.persistence.BrandEntity;
import com.zija.catalog.internal.persistence.CategoryEntity;
import com.zija.catalog.internal.persistence.TagEntity;
import com.zija.catalog.internal.persistence.UnitEntity;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class CatalogDictionaryController {

    private final CatalogDictionaryService dictionaryService;
    private final HouseholdApi householdApi;

    CatalogDictionaryController(CatalogDictionaryService dictionaryService, HouseholdApi householdApi) {
        this.dictionaryService = dictionaryService;
        this.householdApi = householdApi;
    }

    // --- Categories ---

    @RequireMember
    @GetMapping("/categories/tree")
    List<CategoryEntity> getCategoryTree(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findCategoryTree(member.householdId(), includeArchived);
    }

    @RequireAdmin
    @PostMapping("/categories")
    CategoryEntity createCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createCategory(member.householdId(), request.name(), request.parentId(), request.sortOrder());
    }

    @RequireAdmin
    @PostMapping("/categories/{id}/archive")
    void archiveCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveCategory(member.householdId(), id, request.version());
    }

    @RequireAdmin
    @PostMapping("/categories/{id}/restore")
    void restoreCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreCategory(member.householdId(), id, request.version());
    }

    // --- Brands ---

    @RequireMember
    @GetMapping("/brands")
    List<BrandEntity> getBrands(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findBrands(member.householdId(), includeArchived);
    }

    @RequireMember
    @PostMapping("/brands")
    BrandEntity createBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createBrand(member.householdId(), request.name());
    }

    @RequireAdmin
    @PostMapping("/brands/{id}/archive")
    void archiveBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveBrand(member.householdId(), id, request.version());
    }

    // --- Units ---

    @RequireMember
    @GetMapping("/units")
    List<UnitEntity> getUnits(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findUnits(member.householdId(), includeArchived);
    }

    @RequireAdmin
    @PostMapping("/units")
    UnitEntity createUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateUnitRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createUnit(member.householdId(), request.name(), request.decimalScale());
    }

    // --- Tags ---

    @RequireMember
    @GetMapping("/tags")
    List<TagEntity> getTags(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findTags(member.householdId(), includeArchived);
    }

    @RequireMember
    @PostMapping("/tags")
    TagEntity createTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createTag(member.householdId(), request.name());
    }

    @RequireAdmin
    @PostMapping("/tags/{id}/archive")
    void archiveTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveTag(member.householdId(), id, request.version());
    }

    // --- DTOs ---

    record CreateCategoryRequest(@NotBlank String name, UUID parentId, int sortOrder) {}
    record CreateNameRequest(@NotBlank String name) {}
    record CreateUnitRequest(@NotBlank String name, int decimalScale) {}
    record VersionRequest(Integer version) {}
}
