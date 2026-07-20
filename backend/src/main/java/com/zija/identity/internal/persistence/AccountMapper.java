package com.zija.identity.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    Optional<AccountEntity> selectByNormalizedUsername(@Param("normalizedUsername") String normalizedUsername);

    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("version") Integer version);

    int updatePasswordHash(@Param("id") UUID id, @Param("passwordHash") String passwordHash, @Param("version") Integer version);
}
