package com.zija.reminder.internal;

import com.zija.catalog.CatalogApi;
import com.zija.reminder.internal.ReminderRuleResolver.EffectiveExpiryRule;
import com.zija.reminder.internal.ReminderRuleResolver.EffectiveLowStockRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderRuleResolverTest {

    private static CatalogApi.ItemInfo item(String em, List<Short> days, String lm, BigDecimal t) {
        return new CatalogApi.ItemInfo(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "x", "CONSUMABLE", null, null, null, null, "ACTIVE", em, days, lm, t);
    }
    private static ReminderService.RuleView hh(boolean eDis, List<Short> eDays, boolean lDis, BigDecimal t) {
        return new ReminderService.RuleView(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                eDis, eDays, lDis, t, 0);
    }

    @Test
    void itemInheritsHouseholdDays() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("INHERIT", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isTrue();
        assertThat(r.days()).containsExactly((short)30,(short)7,(short)1);
    }

    @Test
    void itemCustomOverridesHousehold() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("CUSTOM", List.of((short)14,(short)3), "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isTrue();
        assertThat(r.days()).containsExactly((short)14,(short)3);
    }

    @Test
    void itemDisabledWins() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("DISABLED", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void householdDisabledWins() {
        var r = ReminderRuleResolver.resolveExpiry(
                item("INHERIT", null, "INHERIT", null),
                hh(true, List.of((short)30,(short)7,(short)1), false, BigDecimal.ONE));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void lowStockInherits() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("3")));
        assertThat(r.enabled()).isTrue();
        assertThat(r.threshold()).isEqualByComparingTo("3");
    }

    @Test
    void lowStockCustomOverrides() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "CUSTOM", new BigDecimal("0.5")),
                hh(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("3")));
        assertThat(r.enabled()).isTrue();
        assertThat(r.threshold()).isEqualByComparingTo("0.5");
    }

    @Test
    void lowStockItemDisabledWins() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "DISABLED", null),
                hh(false, List.of((short)30,(short)7,(short)1), false, new BigDecimal("3")));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void lowStockHouseholdDisabledWins() {
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "INHERIT", null),
                hh(false, List.of((short)30,(short)7,(short)1), true, new BigDecimal("3")));
        assertThat(r.enabled()).isFalse();
    }

    @Test
    void lowStockHouseholdDisabledAndItemCustomStillActive() {
        // 物品 CUSTOM 不受家庭禁用影响（物品级显式覆盖）
        var r = ReminderRuleResolver.resolveLowStock(
                item("INHERIT", null, "CUSTOM", new BigDecimal("0.5")),
                hh(false, List.of((short)30,(short)7,(short)1), true, new BigDecimal("3")));
        assertThat(r.enabled()).isTrue();
        assertThat(r.threshold()).isEqualByComparingTo("0.5");
    }
}
