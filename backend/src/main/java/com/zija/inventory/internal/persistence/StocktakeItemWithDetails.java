package com.zija.inventory.internal.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 盘点行项详情 DTO，包含物品名称、批次号、单位等展示信息。
 * 用于盘点草稿详情查询，直接映射 SQL 查询结果。
 * <p>
 * 名称字段通过 LEFT JOIN 补齐：批次/物品/单位缺失时（例如行项引用的
 * 批次已被清理）为 {@code null}，行项本身必须始终返回，与确认/取消
 * 流程处理的全量行项保持一致。前端对缺失名称兜底显示「—」。
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
