package com.zija.catalog.internal.persistence;

import com.zija.catalog.CatalogApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface ItemDumpMapper {
    List<CatalogApi.ItemFlat> dumpItems(@Param("householdId") UUID householdId,
                                         @Param("cursor") OffsetDateTime cursor,
                                         @Param("limit") int limit);
}
