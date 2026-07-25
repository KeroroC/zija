package com.zija.inventory.internal;

import com.zija.inventory.internal.persistence.ConsistencyCheckMapper;
import com.zija.inventory.internal.persistence.StockPositionEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 一致性检查服务 —— 只读比对流水聚合与库存位。
 * <p>
 * 通过比对 {@code inventory_stock_position} 中的实际数量与
 * {@code inventory_movement} 聚合出的应有数量，发现不一致的库存位。
 * 本服务绝不会修改任何数据。
 */
@Service
public class ConsistencyCheckService {

    private final ConsistencyCheckMapper consistencyCheckMapper;

    public ConsistencyCheckService(ConsistencyCheckMapper consistencyCheckMapper) {
        this.consistencyCheckMapper = consistencyCheckMapper;
    }

    /**
     * 检查指定家庭（可选物品过滤）下库存位与流水的一致性。
     *
     * @param householdId  家庭 ID
     * @param itemIdFilter 物品 ID 过滤（null 表示检查全部物品）
     * @return 不一致的库存位列表；一致时返回空列表
     */
    @Transactional(readOnly = true)
    public List<Discrepancy> check(UUID householdId, UUID itemIdFilter) {
        // 1. 当前库存位
        List<StockPositionEntity> actualPositions =
                consistencyCheckMapper.currentPositions(householdId, itemIdFilter);

        // 2. 流水聚合的应有数量
        List<StockPositionEntity> expectedPositions =
                consistencyCheckMapper.expectedFromMovements(householdId, itemIdFilter);

        // 3. 按 (lotId, locationId) 建立 expected 映射
        Map<StockPositionKey, BigDecimal> expectedMap = expectedPositions.stream()
                .collect(Collectors.toMap(
                        sp -> new StockPositionKey(sp.getLotId(), sp.getLocationId()),
                        StockPositionEntity::getQuantity,
                        BigDecimal::add));

        // 4. 比对实际库存位与应有数量
        List<Discrepancy> discrepancies = new ArrayList<>();
        for (StockPositionEntity actual : actualPositions) {
            StockPositionKey key = new StockPositionKey(actual.getLotId(), actual.getLocationId());
            BigDecimal expected = expectedMap.getOrDefault(key, BigDecimal.ZERO);
            BigDecimal actualQty = actual.getQuantity();

            if (actualQty.compareTo(expected) != 0) {
                discrepancies.add(new Discrepancy(
                        actual.getLotId(), actual.getLocationId(), expected, actualQty));
            }
        }

        return discrepancies;
    }

    /**
     * 一致性差异记录。
     *
     * @param lotId      批次 ID
     * @param locationId 位置 ID
     * @param expected   流水聚合的应有数量
     * @param actual     库存位的实际数量
     */
    public record Discrepancy(UUID lotId, UUID locationId,
                              BigDecimal expected, BigDecimal actual) {}

    private record StockPositionKey(UUID lotId, UUID locationId) {}
}
