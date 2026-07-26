package com.zija.reminder.internal;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityClassifierTest {

    @Test
    void expiryUrgentWhenDaysLeftLe1() {
        assertThat(SeverityClassifier.expiry((short)1, 0)).isEqualTo("URGENT");
        assertThat(SeverityClassifier.expiry((short)1, -5)).isEqualTo("URGENT");
    }

    @Test
    void expiryWarnWhenDaysLeftLe7() {
        assertThat(SeverityClassifier.expiry((short)7, 4)).isEqualTo("WARN");
        assertThat(SeverityClassifier.expiry((short)7, 7)).isEqualTo("WARN");
    }

    @Test
    void expiryInfoWhenDaysLeftLe30() {
        assertThat(SeverityClassifier.expiry((short)30, 25)).isEqualTo("INFO");
        assertThat(SeverityClassifier.expiry((short)30, 30)).isEqualTo("INFO");
    }

    @Test
    void expiryNullWhenOutsideWindow() {
        assertThat(SeverityClassifier.expiry((short)30, 100)).isNull();
    }

    @Test
    void lowStockUrgentWhenQtyZero() {
        assertThat(SeverityClassifier.lowStock(BigDecimal.ZERO, new BigDecimal("2"))).isEqualTo("URGENT");
    }

    @Test
    void lowStockUrgentWhenRatioHigh() {
        // qty=0.3, threshold=2 -> (2-0.3)/2 = 0.85 > 0.5 -> URGENT
        assertThat(SeverityClassifier.lowStock(new BigDecimal("0.3"), new BigDecimal("2"))).isEqualTo("URGENT");
    }

    @Test
    void lowStockWarnWhenRatioMid() {
        // qty=1.4, threshold=2 -> 0.3 -> WARN
        assertThat(SeverityClassifier.lowStock(new BigDecimal("1.4"), new BigDecimal("2"))).isEqualTo("WARN");
    }

    @Test
    void lowStockInfoWhenSlightlyBelow() {
        assertThat(SeverityClassifier.lowStock(new BigDecimal("1.9"), new BigDecimal("2"))).isEqualTo("INFO");
    }
}
