package com.zija.inventory.internal.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 盘点行项详情 DTO，包含物品名称、批次号、单位等展示信息。
 * 用于盘点草稿详情查询，直接映射 SQL 查询结果。
 */
public record StocktakeItemWithDetails(
        UUID id,
        UUID lotId,
        UUID locationId,
        BigDecimal bookQuantity,
        BigDecimal actualQuantity,
        Long positionRevision,
        String reason,
        String itemName,
        String lotNumber,
        String unitName
) {}
