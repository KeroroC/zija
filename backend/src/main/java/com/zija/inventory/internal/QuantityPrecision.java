package com.zija.inventory.internal;

import com.zija.inventory.internal.exception.InventoryQuantityPrecisionInvalidException;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class QuantityPrecision {
    private QuantityPrecision() {}

    static BigDecimal require(int decimalScale, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new InventoryQuantityPrecisionInvalidException();
        }
        try {
            var scaled = quantity.setScale(decimalScale, RoundingMode.UNNECESSARY);
            if (scaled.signum() <= 0) throw new InventoryQuantityPrecisionInvalidException();
            return scaled;
        } catch (ArithmeticException ex) {
            throw new InventoryQuantityPrecisionInvalidException();
        }
    }
}
