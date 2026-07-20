package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface InvitationMapper extends BaseMapper<InvitationEntity> {

    Optional<InvitationEntity> selectByDigestForUpdate(@Param("tokenDigest") String tokenDigest);

    int markConsumed(@Param("id") UUID id, @Param("consumedBy") UUID consumedBy);
}
