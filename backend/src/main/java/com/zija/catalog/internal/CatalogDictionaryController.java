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

/**
 * 目录字典控制器，提供分类（Category）、品牌（Brand）、计量单位（Unit）、标签（Tag）的 CRUD REST API。
 *
 * <p>查询接口要求用户为家庭成员（{@link RequireMember}），修改接口要求管理员权限（{@link RequireAdmin}）。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li><b>分类</b>：
 *     {@code GET /api/v1/categories/tree}（树形查询）、
 *     {@code POST /api/v1/categories}（创建）、
 *     {@code PUT /api/v1/categories/{id}}（重命名）、
 *     {@code PUT /api/v1/categories/{id}/position}（移动位置）、
 *     {@code POST /api/v1/categories/{id}/archive}（归档）、
 *     {@code POST /api/v1/categories/{id}/restore}（恢复）</li>
 *   <li><b>品牌</b>：
 *     {@code GET /api/v1/brands}（列表）、
 *     {@code POST /api/v1/brands}（创建）、
 *     {@code PUT /api/v1/brands/{id}}（更新）、
 *     {@code POST /api/v1/brands/{id}/archive}（归档）、
 *     {@code POST /api/v1/brands/{id}/restore}（恢复）</li>
 *   <li><b>计量单位</b>：
 *     {@code GET /api/v1/units}（列表）、
 *     {@code POST /api/v1/units}（创建）、
 *     {@code PUT /api/v1/units/{id}}（更新）、
 *     {@code POST /api/v1/units/{id}/archive}（归档）、
 *     {@code POST /api/v1/units/{id}/restore}（恢复）</li>
 *   <li><b>标签</b>：
 *     {@code GET /api/v1/tags}（列表）、
 *     {@code POST /api/v1/tags}（创建）、
 *     {@code PUT /api/v1/tags/{id}}（更新）、
 *     {@code POST /api/v1/tags/{id}/archive}（归档）、
 *     {@code POST /api/v1/tags/{id}/restore}（恢复）</li>
 * </ul>
 */
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

    /**
     * 查询分类树形结构，支持包含已归档分类。
     */
    @RequireMember
    @GetMapping("/categories/tree")
    List<CategoryEntity> getCategoryTree(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findCategoryTree(member.householdId(), includeArchived);
    }

    /**
     * 创建分类，可指定父分类和排序序号。
     */
    @RequireAdmin
    @PostMapping("/categories")
    CategoryEntity createCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createCategory(member.householdId(), request.name(), request.parentId(), request.sortOrder());
    }

    /**
     * 重命名分类，支持乐观锁版本控制。
     */
    @RequireAdmin
    @PutMapping("/categories/{id}")
    void updateCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.updateCategory(member.householdId(), id, request.name(), request.version());
    }

    /**
     * 移动分类位置，可调整父分类和排序序号。
     */
    @RequireAdmin
    @PutMapping("/categories/{id}/position")
    void moveCategory(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody MoveCategoryRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.moveCategory(member.householdId(), id, request.parentId(), request.sortOrder(), request.version());
    }

    /**
     * 归档分类。
     */
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

    /**
     * 恢复已归档的分类。
     */
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

    /**
     * 查询品牌列表，支持包含已归档品牌。
     */
    @RequireMember
    @GetMapping("/brands")
    List<BrandEntity> getBrands(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findBrands(member.householdId(), includeArchived);
    }

    /**
     * 创建品牌。
     */
    @RequireMember
    @PostMapping("/brands")
    BrandEntity createBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createBrand(member.householdId(), request.name());
    }

    /**
     * 更新品牌名称，支持乐观锁版本控制。
     */
    @RequireAdmin
    @PutMapping("/brands/{id}")
    void updateBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.updateBrand(member.householdId(), id, request.name(), request.version());
    }

    /**
     * 归档品牌。
     */
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

    /**
     * 恢复已归档的品牌。
     */
    @RequireAdmin
    @PostMapping("/brands/{id}/restore")
    void restoreBrand(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreBrand(member.householdId(), id, request.version());
    }

    // --- Units ---

    /**
     * 查询计量单位列表，支持包含已归档单位。
     */
    @RequireMember
    @GetMapping("/units")
    List<UnitEntity> getUnits(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findUnits(member.householdId(), includeArchived);
    }

    /**
     * 创建计量单位，可指定小数精度。
     */
    @RequireAdmin
    @PostMapping("/units")
    UnitEntity createUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateUnitRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createUnit(member.householdId(), request.name(), request.decimalScale());
    }

    /**
     * 更新计量单位名称，支持乐观锁版本控制。
     */
    @RequireAdmin
    @PutMapping("/units/{id}")
    void updateUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.updateUnit(member.householdId(), id, request.name(), request.version());
    }

    /**
     * 归档计量单位。
     */
    @RequireAdmin
    @PostMapping("/units/{id}/archive")
    void archiveUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.archiveUnit(member.householdId(), id, request.version());
    }

    /**
     * 恢复已归档的计量单位。
     */
    @RequireAdmin
    @PostMapping("/units/{id}/restore")
    void restoreUnit(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreUnit(member.householdId(), id, request.version());
    }

    // --- Tags ---

    /**
     * 查询标签列表，支持包含已归档标签。
     */
    @RequireMember
    @GetMapping("/tags")
    List<TagEntity> getTags(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "false") boolean includeArchived
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.findTags(member.householdId(), includeArchived);
    }

    /**
     * 创建标签。
     */
    @RequireMember
    @PostMapping("/tags")
    TagEntity createTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dictionaryService.createTag(member.householdId(), request.name());
    }

    /**
     * 归档标签。
     */
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

    /**
     * 恢复已归档的标签。
     */
    @RequireAdmin
    @PostMapping("/tags/{id}/restore")
    void restoreTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.restoreTag(member.householdId(), id, request.version());
    }

    /**
     * 更新标签名称，支持乐观锁版本控制。
     */
    @RequireAdmin
    @PutMapping("/tags/{id}")
    void updateTag(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNameRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        dictionaryService.updateTag(member.householdId(), id, request.name(), request.version());
    }

    // --- DTOs ---

    record CreateCategoryRequest(@NotBlank String name, UUID parentId, int sortOrder) {}
    record CreateNameRequest(@NotBlank String name) {}
    record CreateUnitRequest(@NotBlank String name, int decimalScale) {}
    record VersionRequest(Integer version) {}
    record UpdateNameRequest(@NotBlank String name, Integer version) {}
    record MoveCategoryRequest(UUID parentId, int sortOrder, Integer version) {}
}
