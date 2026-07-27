package com.zija.reporting.internal.reports;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reporting.internal.persistence.ReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
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
        return reportMapper.expiringLots(new Page<>(page, pageSize),
                householdId, withinDays, itemId, locationId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> lowStock(UUID householdId, int page, int pageSize,
                                                  UUID categoryId) {
        return reportMapper.lowStock(new Page<>(page, pageSize), householdId, categoryId);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> stockChanges(UUID householdId, int page, int pageSize,
                                                      OffsetDateTime from, OffsetDateTime to,
                                                      UUID itemId, UUID locationId, String type) {
        return reportMapper.stockChanges(new Page<>(page, pageSize),
                householdId, from, to, itemId, locationId, type);
    }

    @Transactional(readOnly = true)
    public IPage<Map<String, Object>> movements(UUID householdId, int page, int pageSize,
                                                   OffsetDateTime from, OffsetDateTime to,
                                                   UUID itemId, String type, UUID operatorAccountId) {
        return reportMapper.movements(new Page<>(page, pageSize),
                householdId, from, to, itemId, type, operatorAccountId);
    }
}
