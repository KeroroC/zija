package com.zija.reminder.internal;

import com.zija.shared.ZijaProblems;
import com.zija.reminder.internal.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ReminderController.class, NotificationController.class})
class ReminderExceptionHandler {

    @ExceptionHandler(ReminderRuleNotInitializedException.class)
    ProblemDetail handleRuleNotInit(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.INTERNAL_SERVER_ERROR, "规则未初始化", "REMINDER_RULE_NOT_INITIALIZED");
    }

    @ExceptionHandler(ReminderRuleVersionConflictException.class)
    ProblemDetail handleVersionConflict(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.CONFLICT, "规则版本冲突", "REMINDER_RULE_VERSION_CONFLICT");
    }

    @ExceptionHandler(MailSettingVersionConflictException.class)
    ProblemDetail handleMailSettingVersionConflict(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.CONFLICT, "邮件设置版本冲突", "MAIL_SETTING_VERSION_CONFLICT");
    }

    @ExceptionHandler(ReminderRuleExpiryDaysInvalidException.class)
    ProblemDetail handleExpiryDays(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.UNPROCESSABLE_CONTENT, "临期天数无效", "REMINDER_RULE_EXPIRY_DAYS_INVALID");
    }

    @ExceptionHandler(ReminderRuleLowStockInvalidException.class)
    ProblemDetail handleLowStock(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.UNPROCESSABLE_CONTENT, "低库存阈值无效", "REMINDER_RULE_LOW_STOCK_INVALID");
    }

    @ExceptionHandler(ReminderTaskNotFoundException.class)
    ProblemDetail handleNotFound(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.NOT_FOUND, "任务不存在", "REMINDER_TASK_NOT_FOUND");
    }

    @ExceptionHandler(ReminderTaskInvalidTransitionException.class)
    ProblemDetail handleTransition(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.CONFLICT, "状态转换非法", "REMINDER_TASK_INVALID_TRANSITION");
    }

    @ExceptionHandler(ReminderTaskSnoozeUntilInvalidException.class)
    ProblemDetail handleSnoozeUntil(HttpServletRequest r) {
        return ZijaProblems.of(r, HttpStatus.UNPROCESSABLE_CONTENT, "稍后提醒时间无效", "REMINDER_TASK_SNOOZE_UNTIL_INVALID");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(HttpServletRequest r, IllegalArgumentException ex) {
        return ZijaProblems.of(r, HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), "REMINDER_INVALID_ARGUMENT");
    }
}
