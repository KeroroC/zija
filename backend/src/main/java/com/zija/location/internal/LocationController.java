package com.zija.location.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import com.zija.location.LocationApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 存放位置控制器，提供位置（Location）的增删改查 REST API，支持树形结构管理。
 *
 * <p>所有端点均要求当前用户为家庭的活跃成员（{@link RequireMember}）。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code GET    /api/v1/locations/tree}        — 查询位置树形结构</li>
 *   <li>{@code GET    /api/v1/locations/{id}}         — 查询单个位置详情</li>
 *   <li>{@code POST   /api/v1/locations}              — 创建位置</li>
 *   <li>{@code PUT    /api/v1/locations/{id}}         — 重命名位置</li>
 *   <li>{@code PUT    /api/v1/locations/{id}/position} — 移动位置（调整父节点和排序）</li>
 *   <li>{@code DELETE /api/v1/locations/{id}}         — 删除位置</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/locations")
class LocationController {

    private final LocationService locationService;
    private final HouseholdApi householdApi;

    LocationController(LocationService locationService, HouseholdApi householdApi) {
        this.locationService = locationService;
        this.householdApi = householdApi;
    }

    /**
     * 查询当前家庭的位置树形结构。
     */
    @RequireMember
    @GetMapping("/tree")
    LocationApi.LocationTree getTree(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return locationService.tree(member.householdId());
    }

    /**
     * 查询单个位置详情，包含库存摘要占位字段。
     *
     * @param id 位置 ID
     * @return 位置详细信息
     */
    @RequireMember
    @GetMapping("/{id}")
    Map<String, Object> getLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var info = locationService.requireLocation(member.householdId(), id);
        return Map.of(
                "id", info.id(),
                "householdId", info.householdId(),
                "parentId", info.parentId() != null ? info.parentId() : "",
                "name", info.name(),
                "sortOrder", info.sortOrder(),
                "everReferenced", info.everReferenced(),
                "version", info.version()
        );
    }

    /**
     * 创建存放位置，可指定父位置和排序序号。
     */
    @RequireMember
    @PostMapping
    LocationApi.LocationInfo createLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody CreateLocationRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = locationService.createLocation(member.householdId(), request.name(), request.parentId(), request.sortOrder());
        return new LocationApi.LocationInfo(
                entity.getId(), entity.getHouseholdId(), entity.getParentId(),
                entity.getName(), entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion()
        );
    }

    /**
     * 重命名存放位置，支持乐观锁版本控制。
     *
     * @param id 位置 ID
     * @return 更新后的位置信息
     */
    @RequireMember
    @PutMapping("/{id}")
    LocationApi.LocationInfo renameLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RenameLocationRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var entity = locationService.renameLocation(member.householdId(), id, request.name(), request.version());
        return new LocationApi.LocationInfo(
                entity.getId(), entity.getHouseholdId(), entity.getParentId(),
                entity.getName(), entity.getSortOrder(), entity.getEverReferenced(),
                entity.getVersion()
        );
    }

    /**
     * 移动存放位置，可调整父位置和排序序号。
     *
     * @param id 位置 ID
     */
    @RequireMember
    @PutMapping("/{id}/position")
    void moveLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody MoveLocationRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        locationService.moveLocation(member.householdId(), id, request.parentId(), request.sortOrder(), request.version());
    }

    /**
     * 删除存放位置。
     *
     * @param id 位置 ID
     */
    @RequireMember
    @DeleteMapping("/{id}")
    void deleteLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        locationService.deleteLocation(member.householdId(), id, request.version());
    }

    // --- DTOs ---

    record CreateLocationRequest(@NotBlank String name, UUID parentId, int sortOrder) {}
    record RenameLocationRequest(@NotBlank String name, Integer version) {}
    record MoveLocationRequest(UUID parentId, int sortOrder, Integer version) {}
    record VersionRequest(Integer version) {}
}
