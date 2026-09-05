package com.zija.reminder.internal;

import com.zija.reminder.ReminderApi;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** 提醒模块公共只读端口适配：复用首页优先任务的标题与排序口径。 */
@Service
class ReminderApiService implements ReminderApi {

    private final DashboardService dashboardService;

    ReminderApiService(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Override
    public List<PriorityTaskInfo> priorityTasks(UUID householdId, int topN) {
        return dashboardService.priorityTasks(householdId, topN);
    }
}
