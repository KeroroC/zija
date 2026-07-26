package com.zija.reminder.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 通知 REST 控制器，提供通知分页、未读计数、标记已读。
 *
 * <p>所有端点均要求当前用户为家庭的活跃成员（{@link RequireMember}）。</p>
 *
 * <p>端点概览：</p>
 * <ul>
 *   <li>{@code GET  /api/v1/notifications}            — 分页查询通知</li>
 *   <li>{@code GET  /api/v1/notifications/unread-count} — 未读通知计数</li>
 *   <li>{@code POST /api/v1/notifications/{id}/read}   — 标记单条已读</li>
 *   <li>{@code POST /api/v1/notifications/read-all}    — 标记全部已读</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {

    private final NotificationService notificationService;
    private final HouseholdApi householdApi;

    NotificationController(NotificationService notificationService, HouseholdApi householdApi) {
        this.notificationService = notificationService;
        this.householdApi = householdApi;
    }

    @RequireMember
    @GetMapping
    NotificationService.NotificationPage list(@AuthenticationPrincipal ZijaPrincipal principal,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int pageSize,
                                              @RequestParam(defaultValue = "false") boolean unreadOnly) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;
        return notificationService.page(member.householdId(), page, pageSize, unreadOnly);
    }

    @RequireMember
    @GetMapping("/unread-count")
    UnreadCount unreadCount(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return new UnreadCount(notificationService.unreadCount(member.householdId()));
    }

    @RequireMember
    @PostMapping("/{id}/read")
    void readOne(@AuthenticationPrincipal ZijaPrincipal principal, @PathVariable UUID id) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        notificationService.markOneRead(member.householdId(), id);
    }

    @RequireMember
    @PostMapping("/read-all")
    void readAll(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        notificationService.markAllRead(member.householdId());
    }

    record UnreadCount(long count) {}
}
