package com.zija.reporting.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 报表模块专用 Clock。时区由 {@code zija.schedule.zone} 控制，默认 {@code Asia/Shanghai}。
 * <p>
 * 与 {@link com.zija.reminder.internal.ClockConfig} 读同一属性，确保报表中的「今日临期」
 * 与提醒模块的扫描口径一致，避免报表与提醒列表相差一天。
 */
@Configuration(proxyBeanMethods = false)
class ReportingClockConfig {
    @Bean
    Clock reportingClock(@Value("${zija.schedule.zone:Asia/Shanghai}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}