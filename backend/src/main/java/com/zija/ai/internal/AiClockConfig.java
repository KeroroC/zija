package com.zija.ai.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * AI 模块专用 Clock。时区与 {@code zija.schedule.zone}（默认 {@code Asia/Shanghai}）保持一致，
 * 供家庭事实问答里的日期边界逻辑使用——临期批次「今天」以家庭所在时区计算，避免按 JVM 默认时区漂移一天。
 */
@Configuration(proxyBeanMethods = false)
class AiClockConfig {

    static final String AI_CLOCK = "aiClock";

    @Bean(name = AI_CLOCK)
    Clock aiClock(@Value("${zija.schedule.zone:Asia/Shanghai}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
