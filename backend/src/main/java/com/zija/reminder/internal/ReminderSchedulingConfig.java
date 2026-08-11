package com.zija.reminder.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring @Scheduled 支持，供 EventRetryService 定时重投 dead-letter。
 *
 * <p>注意：Spring Modulith 的 {@code MomentsAutoConfiguration} 也带 {@code @EnableScheduling}，
 * 因此移除本类并不能关闭调度。各定时任务的开关统一走 cron 属性，
 * 设为 {@code "-"}（{@code ScheduledTaskRegistrar.CRON_DISABLED}）即可单独禁用。
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ReminderSchedulingConfig {
}
