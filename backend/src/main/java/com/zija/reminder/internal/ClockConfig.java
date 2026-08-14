package com.zija.reminder.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 提醒模块专用 Clock。时区由 {@code zija.schedule.zone} 控制，默认 {@code Asia/Shanghai}。
 * <p>
 * 调度 cron（{@link ExpiryScanScheduler} 等）也读取同一属性，确保 {@code @Scheduled}
 * 的触发时区与 {@link Clock} 的日期口径完全一致：临期/低库存「今天」以家庭所在时区为准。
 * <p>
 * 标记 {@link Primary}使得 Spring Modulith 的
 * {@code EventPublicationRegistry} 等仅按 {@link Clock} 类型注入的组件
 * 拿到家庭时区的 Clock，与提醒/报表口径统一。
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    public static final String REMINDER_CLOCK = "reminderClock";

    @Bean(name = REMINDER_CLOCK)
    @Primary
    Clock reminderClock(@Value("${zija.schedule.zone:Asia/Shanghai}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}