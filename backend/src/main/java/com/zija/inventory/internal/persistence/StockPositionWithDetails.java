package com.zija.inventory.internal.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 库存位详情 DTO，包含物品名称、单位、批次信息等。
 * 用于库存位列表查询，直接映射 SQL 查询结果。
 */
public record StockPositionWithDetails(
        UUID lotId,
        UUID locationId,
        BigDecimal quantity,
        long revision,
        OffsetDateTime updatedAt,
        String itemName,
        String itemManagementType,
        String unitName,
        String lotNumber,
        String serialNumber,
        LocalDate expiryDate
) {}
