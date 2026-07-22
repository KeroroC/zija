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

@RestController
@RequestMapping("/api/v1/locations")
class LocationController {

    private final LocationService locationService;
    private final HouseholdApi householdApi;

    LocationController(LocationService locationService, HouseholdApi householdApi) {
        this.locationService = locationService;
        this.householdApi = householdApi;
    }

    @RequireMember
    @GetMapping("/tree")
    LocationApi.LocationTree getTree(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return locationService.tree(member.householdId());
    }

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
                "version", info.version(),
                "inventorySummary", "库存将在阶段四启用"
        );
    }

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
