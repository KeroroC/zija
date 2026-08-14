package com.zija.reporting.internal.reports;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reporting.internal.LocationScopeResolver;
import com.zija.reporting.internal.persistence.ReportMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportMapper reportMapper;
    private final LocationScopeResolver locationScopeResolver;
    private final Clock clock;

    public ReportService(ReportMapper reportMapper, LocationScopeResolver locationScopeResolver,
                         @Qualifier("reportingClock") Clock clock) {
        this.reportMapper = reportMapper;
        this.locationScopeResolver = locationScopeResolver;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> stockByLocation(UUID householdId, int page, int pageSize,
                                                        UUID itemId, UUID categoryId,
                                                        UUID locationId, UUID brandId) {
        return reportMapper.stockByLocation(new Page<>(page, pageSize),
                householdId, itemId, categoryId, locationId, brandId);
    }

@Transactional(readOnly = true)
    public IPage<Map<String, Object>> expiringLots(UUID householdId, int page, int pageSize,
                                                       int withinDays, UUID itemId, UUID locationId) {
        LocalDate today = LocalDate.now(clock);
        return reportMapper.expiringLots(new Page<>(page, pageSize),
                householdId, today, withinDays, itemId, locationId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> lowStock(UUID householdId, int page, int pageSize,
                                                  UUID categoryId) {
        return reportMapper.lowStock(new Page<>(page, pageSize), householdId, categoryId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> movements(UUID householdId, int page, int pageSize,
                                                   OffsetDateTime from, OffsetDateTime to,
                                                   UUID itemId, String type, UUID operatorAccountId,
                                                   UUID locationId) {
        var locationIds = locationId != null
                ? locationScopeResolver.expandWithDescendants(householdId, locationId) : null;
        return reportMapper.movements(new Page<>(page, pageSize),
                householdId, from, to, itemId, type, operatorAccountId, locationIds);
    }
}
