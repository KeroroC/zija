package com.zija.location;

import java.util.List;
import java.util.UUID;

public interface LocationApi {

    LocationInfo requireLocation(UUID householdId, UUID locationId);

    void markReferenced(UUID householdId, UUID locationId);

    LocationTree tree(UUID householdId);

    record LocationInfo(
            UUID id,
            UUID householdId,
            UUID parentId,
            String name,
            int sortOrder,
            boolean everReferenced,
            int version
    ) {
    }

    record LocationTree(List<LocationNode> roots) {
    }

    record LocationNode(
            UUID id,
            UUID parentId,
            String name,
            int sortOrder,
            boolean everReferenced,
            int version,
            List<LocationNode> children
    ) {
    }
}
