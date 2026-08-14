package com.zija.reporting.internal.reports;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reporting.internal.LocationScopeResolver;
import com.zija.reporting.internal.persistence.ReportMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

    private ReportMapper reportMapper;
    private LocationScopeResolver locationScopeResolver;
    private ReportService reportService;

    private final UUID householdId = UUID.randomUUID();
    private final Clock fixedClock = Clock.fixed(
            LocalDate.of(2026, 7, 26).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(),
            ZoneId.of("Asia/Shanghai"));

    @BeforeEach
    void setUp() {
        reportMapper = mock(ReportMapper.class);
        locationScopeResolver = mock(LocationScopeResolver.class);
        reportService = new ReportService(reportMapper, locationScopeResolver, fixedClock);
    }

    // --- stockByLocation ---

    @Test
    void stockByLocationReturnsCorrectColumns() {
        var row = new LinkedHashMap<String, Object>();
        row.put("location_path", "厨房/柜子");
        row.put("item_name", "洗衣液");
        row.put("lot_number", "LOT-001");
        row.put("quantity", 10);
        var page = new Page<Map<String, Object>>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(reportMapper.stockByLocation(any(Page.class), eq(householdId),
                isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        var result = reportService.stockByLocation(householdId, 1, 20,
                null, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
        var record = result.getRecords().get(0);
        assertThat(record).containsKey("location_path");
        assertThat(record).containsKey("item_name");
        assertThat(record).containsKey("lot_number");
        assertThat(record).containsKey("quantity");
    }

    // --- expiringLots ---

    @Test
    void expiringLotsRespectsWithinDays() {
        var expectedToday = LocalDate.now(fixedClock);
        var page = new Page<Map<String, Object>>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(reportMapper.expiringLots(any(Page.class), eq(householdId),
                eq(expectedToday), eq(7), isNull(), isNull())).thenReturn(page);

        var result = reportService.expiringLots(householdId, 1, 20, 7, null, null);

        assertThat(result.getRecords()).isEmpty();
        verify(reportMapper).expiringLots(any(Page.class), eq(householdId),
                eq(expectedToday), eq(7), isNull(), isNull());
    }

    // --- lowStock ---

    @Test
    void lowStockOnlyReturnsBelowThreshold() {
        var row = new LinkedHashMap<String, Object>();
        row.put("item_name", "洗衣液");
        row.put("total_quantity", 2);
        row.put("low_stock_threshold", 5);
        var page = new Page<Map<String, Object>>(1, 20);
        page.setRecords(List.of(row));
        page.setTotal(1);
        when(reportMapper.lowStock(any(Page.class), eq(householdId), isNull())).thenReturn(page);

        var result = reportService.lowStock(householdId, 1, 20, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).get("item_name")).isEqualTo("洗衣液");
    }

    // --- stockChanges（已并入 movements）---

    // --- movements ---

    @Test
    void movementsAcceptsMemberTypeAndLocationFilter() {
        UUID operatorId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        var page = new Page<Map<String, Object>>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(locationScopeResolver.expandWithDescendants(eq(householdId), eq(locationId)))
                .thenReturn(List.of(locationId, childId));
        when(reportMapper.movements(any(Page.class), eq(householdId),
                isNull(), isNull(), isNull(), eq("INBOUND"), eq(operatorId),
                eq(List.of(locationId, childId))))
                .thenReturn(page);

        var result = reportService.movements(householdId, 1, 20,
                null, null, null, "INBOUND", operatorId, locationId);

        assertThat(result.getRecords()).isEmpty();
        verify(reportMapper).movements(any(Page.class), eq(householdId),
                isNull(), isNull(), isNull(), eq("INBOUND"), eq(operatorId),
                eq(List.of(locationId, childId)));
    }

    /** 未选位置时，不应展开位置集合，直接把 null 传给 mapper。 */
    @Test
    void movementsWithoutLocationFilterPassesNullScope() {
        var page = new Page<Map<String, Object>>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(reportMapper.movements(any(Page.class), eq(householdId),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        var result = reportService.movements(householdId, 1, 20,
                null, null, null, null, null, null);

        assertThat(result.getRecords()).isEmpty();
        verify(locationScopeResolver, never()).expandWithDescendants(any(), any());
    }

    // --- pagination ---

    @Test
    void paginationParametersArePassedCorrectly() {
        var page = new Page<Map<String, Object>>(2, 10);
        page.setRecords(List.of());
        page.setTotal(50);
        when(reportMapper.stockByLocation(any(Page.class), eq(householdId),
                isNull(), isNull(), isNull(), isNull())).thenReturn(page);

        var result = reportService.stockByLocation(householdId, 2, 10,
                null, null, null, null);

        verify(reportMapper).stockByLocation(
                argThat(p -> p.getCurrent() == 2 && p.getSize() == 10),
                eq(householdId), isNull(), isNull(), isNull(), isNull());
        assertThat(result.getCurrent()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
    }
}
