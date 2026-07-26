package com.zija.reminder.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface TaskMapper extends BaseMapper<TaskEntity> {

    /** 锁定未完任务（OPEN/SNOOZED）行，FOR UPDATE；不存在返回 null。 */
    TaskEntity lockOpenByKindAndTarget(@Param("householdId") UUID householdId,
                                       @Param("kind") String kind,
                                       @Param("lotId") UUID lotId);

    /** 后台扫描用：列出家庭所有未完 OPEN/SNOOZED 任务 FOR UPDATE。 */
    List<TaskEntity> lockOpenTasksForScan(@Param("householdId") UUID householdId);

    /** 状态机：把 OPEN/SNOOZED 转 SNOOZED 并设 snoozed_until。 */
    int snooze(@Param("householdId") UUID householdId, @Param("id") UUID id,
               @Param("fromStatuses") List<String> fromStatuses,
               @Param("until") OffsetDateTime until);

    /** 状态机：转指定终态（DONE/IGNORED），清 snoozed_until。 */
    int transitionTo(@Param("householdId") UUID householdId, @Param("id") UUID id,
                     @Param("fromStatuses") List<String> fromStatuses,
                     @Param("toStatus") String toStatus);

    /** 状态机：reopen（IGNORED/DONE → OPEN），清 snoozed_until。 */
    int reopen(@Param("householdId") UUID householdId, @Param("id") UUID id);

    /** 首页聚合：7 天内到期任务（EXPIRY, due_at <= now+days, status OPEN/SNOOZED）。 */
    List<TaskEntity> expiryWithinDays(@Param("householdId") UUID householdId,
                                      @Param("from") OffsetDateTime from,
                                      @Param("to") OffsetDateTime to,
                                      @Param("limit") int limit);

    /** 首页聚合：低库存未完任务（LOW_STOCK, OPEN/SNOOZED），前 limit 条。 */
    List<TaskEntity> lowStockOpenTasks(@Param("householdId") UUID householdId,
                                       @Param("limit") int limit);

    /** 首页聚合：优先任务（OPEN/SNOOZED），按 severity ASC(URGENT,WARN,INFO)、due_at ASC，前 limit。 */
    List<TaskEntity> priorityTasks(@Param("householdId") UUID householdId,
                                   @Param("limit") int limit);

    /** 分页查询。 */
    IPage<TaskEntity> findPage(Page<TaskEntity> page,
                               @Param("householdId") UUID householdId,
                               @Param("kind") String kind,
                               @Param("status") String status,
                               @Param("itemId") UUID itemId,
                               @Param("overdue") Boolean overdue,
                               @Param("now") OffsetDateTime now,
                               @Param("orderBy") String orderBy);
}
