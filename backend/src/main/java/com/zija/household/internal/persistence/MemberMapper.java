package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface MemberMapper extends BaseMapper<MemberEntity> {

    List<MemberEntity> selectByHousehold(@Param("householdId") UUID householdId);

    Optional<MemberEntity> selectByAccount(@Param("accountId") UUID accountId);

    int updateRole(@Param("id") UUID id, @Param("role") String role, @Param("version") Integer version);

    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("version") Integer version);

    Optional<MemberEntity> selectOwner(@Param("householdId") UUID householdId);
}
