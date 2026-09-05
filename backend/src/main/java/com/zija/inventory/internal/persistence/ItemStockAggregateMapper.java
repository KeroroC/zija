package com.zija.inventory.internal.persistence;

import com.zija.inventory.InventoryApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemStockAggregateMapper {

    /** 按物品列出批次与总库存（仅活跃 lot，含冲正/移位后正确，按 lot 维度 SUM(stock_position.quantity)）。 */
    List<LotAggregateRow> lotsOfItem(@Param("householdId") UUID householdId, @Param("itemId") UUID itemId);

    /** 某物品总库存。 */
    BigDecimal totalStockOfItem(@Param("householdId") UUID householdId, @Param("itemId") UUID itemId);

    List<InventoryApi.LotQuantitySnapshot> findExpiringLots(
            @Param("householdId") UUID householdId,
            @Param("today") LocalDate today,
            @Param("horizon") LocalDate horizon,
            @Param("itemId") UUID itemId,
            @Param("lotId") UUID lotId,
            @Param("limit") int limit
    );

    List<InventoryApi.LotQuantitySnapshot> findExpiredLots(
            @Param("householdId") UUID householdId,
            @Param("today") LocalDate today,
            @Param("itemId") UUID itemId,
            @Param("lotId") UUID lotId,
            @Param("limit") int limit
    );

    List<InventoryApi.LowStockSnapshot> findLowStockItems(
            @Param("householdId") UUID householdId,
            @Param("itemId") UUID itemId,
            @Param("limit") int limit
    );

    List<InventoryApi.LocationStockPositionSnapshot> findStockPositionsInLocations(
            @Param("householdId") UUID householdId,
            @Param("locationIds") Collection<UUID> locationIds,
            @Param("itemNameContains") String itemNameContains,
            @Param("limit") int limit
    );

    /** 批次聚合行（普通类，MyBatis 需要 setter）。 */
    class LotAggregateRow {
        private UUID lotId;
        private UUID itemId;
        private LocalDate expiryDate;
        private BigDecimal totalQuantity;
        private String lotNumber;
        private String serialNumber;

        public UUID getLotId() { return lotId; }
        public void setLotId(UUID lotId) { this.lotId = lotId; }
        public UUID getItemId() { return itemId; }
        public void setItemId(UUID itemId) { this.itemId = itemId; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
        public BigDecimal getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
        public String getLotNumber() { return lotNumber; }
        public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }
        public String getSerialNumber() { return serialNumber; }
        public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }
    }
}
