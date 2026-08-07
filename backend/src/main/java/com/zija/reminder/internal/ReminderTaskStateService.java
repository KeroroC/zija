package com.zija.reminder.internal;

import com.zija.reminder.internal.exception.ReminderTaskInvalidTransitionException;
import com.zija.reminder.internal.exception.ReminderTaskNotFoundException;
import com.zija.reminder.internal.exception.ReminderTaskSnoozeUntilInvalidException;
import com.zija.reminder.internal.persistence.TaskEntity;
import com.zija.reminder.internal.persistence.TaskMapper;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
class ReminderTaskStateService {

    private final TaskMapper taskMapper;
    private final SystemApi systemApi;
    private final Clock clock;

    ReminderTaskStateService(TaskMapper taskMapper, SystemApi systemApi, @org.springframework.beans.factory.annotation.Qualifier("reminderClock") Clock clock) {
        this.taskMapper = taskMapper;
        this.systemApi = systemApi;
        this.clock = clock;
    }

    @Transactional
    public void snooze(UUID householdId, UUID taskId, OffsetDateTime until) {
        var now = OffsetDateTime.now(clock);
        if (!until.isAfter(now.plusMinutes(1)) || until.isAfter(now.plusDays(3650))) {
            throw new ReminderTaskSnoozeUntilInvalidException();
        }
        requireTask(householdId, taskId);
        int rows = taskMapper.snooze(householdId, taskId, List.of("OPEN", "SNOOZED"), until);
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_SNOOZED");
    }

    @Transactional
    public void complete(UUID householdId, UUID taskId) {
        requireTask(householdId, taskId);
        int rows = taskMapper.transitionTo(householdId, taskId, List.of("OPEN", "SNOOZED"), "DONE");
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_COMPLETED");
    }

    @Transactional
    public void ignore(UUID householdId, UUID taskId) {
        requireTask(householdId, taskId);
        int rows = taskMapper.transitionTo(householdId, taskId, List.of("OPEN", "SNOOZED"), "IGNORED");
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_IGNORED");
    }

    @Transactional
    public void reopen(UUID householdId, UUID taskId) {
        requireTask(householdId, taskId);
        int rows = taskMapper.reopen(householdId, taskId);
        if (rows == 0) throw new ReminderTaskInvalidTransitionException();
        audit(householdId, taskId, "REMINDER_TASK_REOPENED");
    }

    private TaskEntity requireTask(UUID householdId, UUID taskId) {
        var t = taskMapper.selectById(taskId);
        if (t == null || !t.getHouseholdId().equals(householdId)) {
            throw new ReminderTaskNotFoundException();
        }
        return t;
    }

    private void audit(UUID householdId, UUID taskId, String action) {
        systemApi.recordAudit(new SystemApi.AuditEvent(
                action, "SUCCESS", householdId, null, null, null, null,
                Map.of("taskId", taskId.toString())));
    }
}
