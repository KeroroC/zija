package com.zija.reminder.internal;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 时区回归测试（提醒模块）。
 * <p>
 * 历史 Bug：{@link ClockConfig} 曾用 {@code Clock.systemUTC()}，但调度 cron
 * 用 {@code Asia/Shanghai}，二者口径不一致 —— CST 03:00 = UTC 19:00 前一天，
 * {@link LocalDate#now(Clock)} 在扫描瞬时取到 UTC 前一天，使
 * {@link ExpiryScanScheduler} / {@link ReminderReconciler} 的「今天」整体偏一天，
 * 临期/低库存扫描因此提前或延后一天。
 * <p>
 * 此测试验证：修复后 {@link ClockConfig#reminderClock(String)} 在
 * {@code zija.schedule.zone=Asia/Shanghai} 下，对典型临界瞬时
 * （北京 03:00 = UTC 前一日 19:00）取到的是北京日期，而非 UTC 日期。
 */
class ReminderClockZoneTest {

    /** 2026-01-16T19:00Z = 北京时间 2026-01-17 03:00。在 UTC 下是 16 号，北京是 17 号。 */
    private static final Instant BEIJING_03_00_OF_0117 = Instant.parse("2026-01-16T19:00:00Z");

    @Test
    void reminderClockDefaultsToShanghaiAndCrossesCstMidnightCorrectly() {
        var clock = new ClockConfig().reminderClock("Asia/Shanghai");
        assertThat(clock.getZone().getId()).isEqualTo("Asia/Shanghai");

        var fixed = Clock.fixed(BEIJING_03_00_OF_0117, clock.getZone());
        LocalDate utcToday = LocalDate.ofInstant(BEIJING_03_00_OF_0117, ZoneOffset.UTC);
        LocalDate beijingToday = LocalDate.now(fixed);

        assertThat(utcToday).isEqualTo(LocalDate.of(2026, 1, 16));      // 自检：确为临界瞬时
        assertThat(beijingToday).isEqualTo(LocalDate.of(2026, 1, 17)); // 修复后应为北京今天
    }
}