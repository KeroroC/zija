package com.zija.inventory.internal.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 批次详情 DTO，包含物品名称、单位、总数量等。
 * 用于批次列表查询，直接映射 SQL 查询结果。
 */
public record LotWithDetails(
        UUID lotId,
        UUID itemId,
        String itemName,
        String unitName,
        BigDecimal totalQuantity,
        LocalDate purchaseDate,
        LocalDate productionDate,
        LocalDate expiryDate,
        String lotNumber,
        String serialNumber,
        String memo,
        Integer version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
