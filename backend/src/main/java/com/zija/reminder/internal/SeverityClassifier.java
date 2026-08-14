package com.zija.reminder.internal;

import java.math.BigDecimal;

final class SeverityClassifier {
    private SeverityClassifier() {}

    private static final long URGENT_EXPIRY_DAY = 1;
    private static final long WARN_EXPIRY_DAY = 7;
    private static final double URGENT_LOW_STOCK_RATIO = 0.5;
    private static final double WARN_LOW_STOCK_RATIO = 0.25;

    /** 返回 INFO/WARN/URGENT；不在窗口返回 null。 */
    static String expiry(short maxDay, long daysLeft) {
        if (daysLeft <= maxDay) {
            if (maxDay <= URGENT_EXPIRY_DAY || daysLeft <= URGENT_EXPIRY_DAY) return TaskSeverity.URGENT;
            if (daysLeft <= WARN_EXPIRY_DAY) return TaskSeverity.WARN;
            return TaskSeverity.INFO;
        }
        return null;
    }

    /** 低库存严重度：按 (threshold-qty)/threshold 比例。 */
    static String lowStock(BigDecimal qty, BigDecimal threshold) {
        if (qty.signum() <= 0) return TaskSeverity.URGENT;
        double ratio = threshold.subtract(qty).divide(threshold, 6, java.math.RoundingMode.HALF_UP).doubleValue();
        if (ratio > URGENT_LOW_STOCK_RATIO) return TaskSeverity.URGENT;
        if (ratio > WARN_LOW_STOCK_RATIO) return TaskSeverity.WARN;
        return TaskSeverity.INFO;
    }
}
