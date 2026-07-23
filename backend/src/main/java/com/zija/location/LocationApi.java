package com.zija.location;

import java.util.List;
import java.util.UUID;

/**
 * 位置模块公共 API，提供存放位置的查询、引用标记及树形结构获取能力。
 */
public interface LocationApi {

    /** 获取指定家庭下的存放位置信息，不存在则抛出异常。 */
    LocationInfo requireLocation(UUID householdId, UUID locationId);

    /** 标记指定存放位置已被引用（用于防止删除正在使用的位置）。 */
    void markReferenced(UUID householdId, UUID locationId);

    /** 获取指定家庭的存放位置树形结构。 */
    LocationTree tree(UUID householdId);

    /** 存放位置详细信息。 */
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

    /** 存放位置树形结构。 */
    record LocationTree(List<LocationNode> roots) {
    }

    /** 存放位置树节点，包含子节点列表。 */
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
