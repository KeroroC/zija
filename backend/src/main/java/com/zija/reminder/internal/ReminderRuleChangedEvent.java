package com.zija.reminder.internal;

import java.util.UUID;

/**
 * 提醒规则已变更的内部事件。在规则写入事务内发布，提交后触发全量重算。
 * 仅 reminder 模块内部使用，不跨模块发布。
 */
record ReminderRuleChangedEvent(UUID householdId) {}
