package com.zija.reminder.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 提醒模块 REST 控制器，提供规则查看/编辑、任务分页/状态机、仪表盘聚合。
 *
 * <p>所有端点均要求当前用户为家庭的活跃成员（{@link RequireMember}）。
 * 规则编辑额外要求管理员角色。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code GET  /api/v1/reminder/rules}               — 查看提醒规则（懒初始化）</li>
 *   <li>{@code PUT  /api/v1/reminder/rules}               — 更新提醒规则（ADMIN+）</li>
 *   <li>{@code GET  /api/v1/reminder/tasks}                — 分页查询任务</li>
 *   <li>{@code POST /api/v1/reminder/tasks/{id}/snooze}   — 稍后提醒</li>
 *   <li>{@code POST /api/v1/reminder/tasks/{id}/complete}  — 完成任务</li>
 *   <li>{@code POST /api/v1/reminder/tasks/{id}/ignore}    — 忽略任务</li>
 *   <li>{@code POST /api/v1/reminder/tasks/{id}/reopen}    — 重新打开任务</li>
 *   <li>{@code GET  /api/v1/reminder/dashboard}            — 仪表盘聚合</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/reminder")
class ReminderController {

    private final ReminderService reminderService;
    private final ReminderTaskStateService stateService;
    private final DashboardService dashboardService;
    private final HouseholdApi householdApi;

    ReminderController(ReminderService reminderService, ReminderTaskStateService stateService,
                       DashboardService dashboardService, HouseholdApi householdApi) {
        this.reminderService = reminderService;
        this.stateService = stateService;
        this.dashboardService = dashboardService;
        this.householdApi = householdApi;
    }

    // ==================== Rules ====================

    @RequireMember
    @GetMapping("/rules")
    ReminderService.RuleView getRules(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return reminderService.getOrCreateRule(member.householdId());
    }

    @RequireMember
    @PutMapping("/rules")
    ReminderService.RuleView updateRules(@AuthenticationPrincipal ZijaPrincipal principal,
                                         @RequestBody ReminderService.RuleUpdate body) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (!householdApi.hasAtLeastRole(principal.getAccountId(), HouseholdApi.MemberRole.ADMIN)) {
            throw new AccessDeniedException("需要管理员权限");
        }
        return reminderService.updateRule(member.householdId(), body);
    }

    // ==================== Tasks ====================

    @RequireMember
    @GetMapping("/tasks")
    ReminderService.TaskPage listTasks(@AuthenticationPrincipal ZijaPrincipal principal,
                                       @RequestParam(required = false) String kind,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) UUID itemId,
                                       @RequestParam(required = false, defaultValue = "false") boolean overdue,
                                       @RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int pageSize,
                                       @RequestParam(defaultValue = "severity,dueAt") String orderBy) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;
        return reminderService.tasksPage(member.householdId(), kind, status, itemId, overdue, page, pageSize, orderBy);
    }

    @RequireMember
    @PostMapping("/tasks/{id}/snooze")
    void snooze(@AuthenticationPrincipal ZijaPrincipal principal,
                @PathVariable UUID id, @RequestBody SnoozeBody body) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        stateService.snooze(member.householdId(), id, body.until());
    }

    @RequireMember
    @PostMapping("/tasks/{id}/complete")
    void complete(@AuthenticationPrincipal ZijaPrincipal principal, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        stateService.complete(member.householdId(), id);
    }

    @RequireMember
    @PostMapping("/tasks/{id}/ignore")
    void ignore(@AuthenticationPrincipal ZijaPrincipal principal, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        stateService.ignore(member.householdId(), id);
    }

    @RequireMember
    @PostMapping("/tasks/{id}/reopen")
    void reopen(@AuthenticationPrincipal ZijaPrincipal principal, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        stateService.reopen(member.householdId(), id);
    }

    // ==================== Dashboard ====================

    @RequireMember
    @GetMapping("/dashboard")
    DashboardService.DashboardView dashboard(@AuthenticationPrincipal ZijaPrincipal principal,
                                             @RequestParam(defaultValue = "7") int days,
                                             @RequestParam(defaultValue = "8") int topN) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return dashboardService.dashboard(member.householdId(), days, topN);
    }

    // ==================== Request DTOs ====================

    record SnoozeBody(OffsetDateTime until) {}
}
