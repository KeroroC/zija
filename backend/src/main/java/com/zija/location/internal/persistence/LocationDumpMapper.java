package com.zija.location.internal.persistence;

import com.zija.location.LocationApi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface LocationDumpMapper {
    List<LocationApi.LocationFlat> dumpTree(@Param("householdId") UUID householdId,
                                             @Param("cursor") OffsetDateTime cursor,
                                             @Param("limit") int limit);
}
