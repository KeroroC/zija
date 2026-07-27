package com.zija.reporting.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface SearchIndexMapper extends BaseMapper<SearchIndexEntity> {
    /** 按主键 upsert（INSERT ON CONFLICT UPDATE）。 */
    int upsert(SearchIndexEntity entity);
    /** 删除指定实体的搜索索引行。 */
    int deleteByEntity(@Param("householdId") UUID householdId,
                        @Param("entityType") String entityType,
                        @Param("entityId") UUID entityId);
}
