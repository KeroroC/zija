package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface NotificationMapper extends BaseMapper<NotificationEntity> {
    IPage<NotificationEntity> findPage(Page<NotificationEntity> page,
                                       @Param("householdId") UUID householdId,
                                       @Param("unreadOnly") Boolean unreadOnly);
    long countUnread(@Param("householdId") UUID householdId);
    int markOneRead(@Param("householdId") UUID householdId, @Param("id") UUID id);
    int markAllRead(@Param("householdId") UUID householdId);
}
