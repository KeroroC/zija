package com.zija.ai.internal;

import com.zija.file.FileApi;
import com.zija.inventory.InventoryApi;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 知识来源检索范围解析：按附件挂载点推导分块元数据中的物品/批次定位。
 *
 * <ul>
 *   <li>家庭挂载：全家庭范围，无物品/批次定位；</li>
 *   <li>物品挂载：该物品（及其批次经物品 id 关联）；</li>
 *   <li>批次挂载：该批次及其所属物品。</li>
 * </ul>
 */
@Component
class KnowledgeScopeResolver {

    private final InventoryApi inventoryApi;

    KnowledgeScopeResolver(InventoryApi inventoryApi) {
        this.inventoryApi = inventoryApi;
    }

    Scope resolve(UUID householdId, String mountType, UUID mountId) {
        return switch (mountType) {
            case FileApi.MOUNT_HOUSEHOLD -> new Scope(null, null);
            case FileApi.MOUNT_ITEM -> new Scope(mountId, null);
            case FileApi.MOUNT_LOT -> {
                var lot = inventoryApi.findLot(householdId, mountId).orElse(null);
                yield new Scope(lot != null ? lot.itemId() : null, mountId);
            }
            default -> throw new IllegalArgumentException("未知挂载点类型: " + mountType);
        };
    }

    record Scope(UUID itemId, UUID lotId) {
    }
}
