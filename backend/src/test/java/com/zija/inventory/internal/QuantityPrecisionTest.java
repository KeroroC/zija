package com.zija.inventory.internal;

import com.zija.inventory.internal.exception.InventoryQuantityPrecisionInvalidException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuantityPrecisionTest {

    @Test
    void integerUnitRejectsFraction() {
        assertThatThrownBy(() -> QuantityPrecision.require(0, new BigDecimal("1.5")))
                .isInstanceOf(InventoryQuantityPrecisionInvalidException.class);
    }

    @Test
    void integerUnitAcceptsIntegers() {
        assertThat(QuantityPrecision.require(0, new BigDecimal("3"))).isEqualByComparingTo("3");
        assertThat(QuantityPrecision.require(0, new BigDecimal("3.00"))).isEqualByComparingTo("3");
    }

    @Test
    void threeScaleRejectsFourth() {
        assertThatThrownBy(() -> QuantityPrecision.require(3, new BigDecimal("0.1234")))
                .isInstanceOf(InventoryQuantityPrecisionInvalidException.class);
    }

    @Test
    void threeScaleAcceptsUpToThree() {
        assertThat(QuantityPrecision.require(3, new BigDecimal("0.123"))).isEqualByComparingTo("0.123");
    }

    @Test
    void rejectsNegative() {
        assertThatThrownBy(() -> QuantityPrecision.require(2, new BigDecimal("-1")))
                .isInstanceOf(InventoryQuantityPrecisionInvalidException.class);
    }
}
