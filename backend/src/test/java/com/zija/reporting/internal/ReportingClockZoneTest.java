package com.zija.reporting.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时区回归测试（报表模块）。
 * <p>
 * 历史 Bug：报表用的「今日临期」依赖 PostgreSQL {@code CURRENT_DATE}
 * （随数据库会话时区，容器默认 UTC），与提醒模块 UTC-{@code Clock}
 * 取出的 {@link LocalDate} 又不一致 —— 报表与提醒列表第二次对不上。
 * 修复后：报表模块改用自己的 {@code reportingClock}（与提醒模块同源
 * {@code zija.schedule.zone=Asia/Shanghai}），SQL 中的「今日」由参数
 * {@code today} 从应用层显式传入，不再依赖 DB 会话时区。
 * <p>
 * 此测试验证：{@link ReportingClockConfig#reportingClock(String)} 在默认
 * 北京时区下对典型临界瞬时给出北京日期，并与cron 同一时区。
 */
class ReportingClockZoneTest {

    /** 2026-01-16T19:00Z = 北京时间 2026-01-17 03:00。在 UTC 下是 16 号，北京是 17 号。 */
    private static final Instant BEIJING_03_00_OF_0117 = Instant.parse("2026-01-16T19:00:00Z");

    @Test
    void reportingClockDefaultsToShanghaiAndAlignsWithReminderMinutesShortOfMidnight() {
        var clock = new ReportingClockConfig().reportingClock("Asia/Shanghai");
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));

        var fixed = Clock.fixed(BEIJING_03_00_OF_0117, clock.getZone());
        assertThat(LocalDate.now(fixed)).isEqualTo(LocalDate.of(2026, 1, 17));
        assertThat(LocalDate.ofInstant(BEIJING_03_00_OF_0117, ZoneOffset.UTC))
                .isEqualTo(LocalDate.of(2026, 1, 16)); // 自检
    }
}