package com.zija.reminder.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring @Scheduled 支持，供 EventRetryService 定时重投 dead-letter。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ReminderSchedulingConfig {
}
