package com.zija.inventory.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemStockAggregateMapper {

    /** 按物品列出批次与总库存（仅活跃 lot，含冲正/移位后正确，按 lot 维度 SUM(stock_position.quantity)）。 */
    List<LotAggregateRow> lotsOfItem(@Param("householdId") UUID householdId, @Param("itemId") UUID itemId);

    /** 某物品总库存。 */
    BigDecimal totalStockOfItem(@Param("householdId") UUID householdId, @Param("itemId") UUID itemId);

    /** 批次聚合行（普通类，MyBatis 需要 setter）。 */
    class LotAggregateRow {
        private UUID lotId;
        private UUID itemId;
        private LocalDate expiryDate;
        private BigDecimal totalQuantity;

        public UUID getLotId() { return lotId; }
        public void setLotId(UUID lotId) { this.lotId = lotId; }
        public UUID getItemId() { return itemId; }
        public void setItemId(UUID itemId) { this.itemId = itemId; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
        public BigDecimal getTotalQuantity() { return totalQuantity; }
        public void setTotalQuantity(BigDecimal totalQuantity) { this.totalQuantity = totalQuantity; }
    }
}
