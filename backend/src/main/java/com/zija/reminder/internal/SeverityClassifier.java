package com.zija.reminder.internal;

import java.math.BigDecimal;

final class SeverityClassifier {
    private SeverityClassifier() {}

    /** 返回 INFO/WARN/URGENT；不在窗口返回 null。 */
    static String expiry(short maxDay, long daysLeft) {
        if (daysLeft <= maxDay) {
            if (maxDay <= 1 || daysLeft <= 1) return "URGENT";
            if (daysLeft <= 7) return "WARN";
            return "INFO";
        }
        return null;
    }

    /** 低库存严重度：按 (threshold-qty)/threshold 比例。 */
    static String lowStock(BigDecimal qty, BigDecimal threshold) {
        if (qty.signum() <= 0) return "URGENT";
        double ratio = threshold.subtract(qty).divide(threshold, 6, java.math.RoundingMode.HALF_UP).doubleValue();
        if (ratio > 0.5) return "URGENT";
        if (ratio > 0.25) return "WARN";
        return "INFO";
    }
}
