package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;

import java.math.BigDecimal;
import java.util.List;

final class ReminderRuleResolver {
    private ReminderRuleResolver() {}

    record EffectiveExpiryRule(boolean enabled, List<Short> days) {}
    record EffectiveLowStockRule(boolean enabled, BigDecimal threshold) {}

    static EffectiveExpiryRule resolveExpiry(CatalogApi.ItemInfo item, ReminderService.RuleView hh) {
        if ("DISABLED".equals(item.expiryReminderMode()) || hh.expiryDisabled()) return new EffectiveExpiryRule(false, null);
        List<Short> days = "CUSTOM".equals(item.expiryReminderMode())
                ? item.expiryReminderDays() : hh.expiryReminderDays();
        if (days == null || days.isEmpty()) return new EffectiveExpiryRule(false, null);
        return new EffectiveExpiryRule(true, days);
    }

    static EffectiveLowStockRule resolveLowStock(CatalogApi.ItemInfo item, ReminderService.RuleView hh) {
        if ("DISABLED".equals(item.lowStockMode())) return new EffectiveLowStockRule(false, null);
        if ("CUSTOM".equals(item.lowStockMode())) {
            return new EffectiveLowStockRule(true, item.lowStockThreshold());
        }
        // INHERIT
        if (hh.lowStockDisabled()) return new EffectiveLowStockRule(false, null);
        return new EffectiveLowStockRule(true, hh.lowStockThreshold());
    }
}
