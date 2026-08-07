package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface ReportMapper {

    /** 当前库存与位置分布：按位置 + 物品聚合。 */
    IPage<Map<String, Object>> stockByLocation(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("itemId") UUID itemId,
            @Param("categoryId") UUID categoryId,
            @Param("locationId") UUID locationId,
            @Param("brandId") UUID brandId);

    /** 临期批次。「今天」由调用方按家庭时区计算后显式传入，避免依赖 DB 会话时区（CURRENT_DATE）。 */
    IPage<Map<String, Object>> expiringLots(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("today") LocalDate today,
            @Param("withinDays") int withinDays,
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId);

    /** 低库存物品。 */
    IPage<Map<String, Object>> lowStock(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("categoryId") UUID categoryId);

    /** 指定时间范围库存变化。 */
    IPage<Map<String, Object>> stockChanges(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("itemId") UUID itemId,
            @Param("locationId") UUID locationId,
            @Param("type") String type);

    /** 按成员/类型/物品筛选流水。 */
    IPage<Map<String, Object>> movements(
            Page<?> page,
            @Param("householdId") UUID householdId,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to,
            @Param("itemId") UUID itemId,
            @Param("type") String type,
            @Param("operatorAccountId") UUID operatorAccountId);
}
