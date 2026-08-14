package com.zija.reporting.internal;

import com.zija.location.LocationApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 把用户选中的位置展开为「自身 + 全部后代」的 id 集合（即位置子树范围）。
 * 报表与导出共用，保证筛选语义一致：选中父级位置（如厨房）时，
 * 其下所有子位置（如冰箱、橱柜）的数据也会一并命中。
 */
@Component
public class LocationScopeResolver {

    private final LocationApi locationApi;

    public LocationScopeResolver(LocationApi locationApi) {
        this.locationApi = locationApi;
    }

    /**
     * 返回选中位置自身及其全部后代位置 id。若该位置不在当前家庭树中，退化为仅含该 id。
     */
    public List<UUID> expandWithDescendants(UUID householdId, UUID locationId) {
        List<UUID> result = new ArrayList<>();
        collectSelfAndDescendants(locationApi.tree(householdId).roots(), locationId, result);
        return result.isEmpty() ? List.of(locationId) : result;
    }

    /** 递归收集 target 节点及其全部后代 id。命中 target 后整棵子树都会被加入。 */
    private boolean collectSelfAndDescendants(List<LocationApi.LocationNode> nodes, UUID target,
                                              List<UUID> out) {
        for (var node : nodes) {
            if (node.id().equals(target)) {
                out.add(node.id());
                collectAll(node.children(), out);
                return true;
            }
            if (collectSelfAndDescendants(node.children(), target, out)) {
                return true;
            }
        }
        return false;
    }

    private static void collectAll(List<LocationApi.LocationNode> nodes, List<UUID> out) {
        for (var node : nodes) {
            out.add(node.id());
            collectAll(node.children(), out);
        }
    }
}
