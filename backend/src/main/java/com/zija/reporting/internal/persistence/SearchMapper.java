package com.zija.reporting.internal.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper
public interface SearchMapper {

    /** 按物品名/品牌/标签搜索 ITEM 实体。 */
    List<Map<String, Object>> searchItems(
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("limit") int limit);

    /** 按批次号/序列号搜索 LOT 实体。 */
    List<Map<String, Object>> searchLots(
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("limit") int limit);

    /** 按位置名/路径搜索 LOCATION 实体。 */
    List<Map<String, Object>> searchLocations(
            @Param("householdId") UUID householdId,
            @Param("q") String q,
            @Param("limit") int limit);
}
