package com.zija.reporting.internal.export;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zija.reporting.internal.exception.ExportTooLargeException;
import com.zija.reporting.internal.persistence.ReportMapper;
import com.zija.reporting.internal.persistence.SearchMapper;
import com.zija.system.SystemApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * 导出服务。行数硬上限 100,000；写审计 EXPORT_PERFORMED。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final int MAX_ROWS = 100_000;
    private static final int PAGE_SIZE = 1000;

    private final ReportMapper reportMapper;
    private final SearchMapper searchMapper;
    private final SystemApi systemApi;

    public ExportService(ReportMapper reportMapper, SearchMapper searchMapper, SystemApi systemApi) {
        this.reportMapper = reportMapper;
        this.searchMapper = searchMapper;
        this.systemApi = systemApi;
    }

    /**
     * 导出指定报表到输出流。超过 MAX_ROWS 抛出 ExportTooLargeException。
     */
    public void exportToStream(UUID householdId, String reportKey,
                                Map<String, String> params,
                                OutputStream out) throws IOException {
        var headers = getHeaders(reportKey);
        List<Map<String, Object>> allRows = fetchAllRows(householdId, reportKey, params);

        if (allRows.size() > MAX_ROWS) {
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "EXPORT_PERFORMED", "FAILURE", householdId, null, null, null, null,
                    Map.of("reportKey", reportKey, "reason", "TOO_LARGE", "rowCount", allRows.size())));
            throw new ExportTooLargeException(allRows.size(), MAX_ROWS);
        }

        CsvWriter.write(out, headers, allRows);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "EXPORT_PERFORMED", "SUCCESS", householdId, null, null, null, null,
                Map.of("reportKey", reportKey, "rowCount", allRows.size())));
    }

    private List<Map<String, Object>> fetchAllRows(UUID householdId, String reportKey,
                                                     Map<String, String> params) {
        return switch (reportKey) {
            case "stock-by-location" -> fetchAllPaged(
                    page -> reportMapper.stockByLocation(page, householdId,
                            parseUuid(params, "itemId"),
                            parseUuid(params, "categoryId"),
                            parseUuid(params, "locationId"),
                            parseUuid(params, "brandId")));
            case "expiring-lots" -> fetchAllPaged(
                    page -> reportMapper.expiringLots(page, householdId,
                            parseInt(params, "withinDays", 30),
                            parseUuid(params, "itemId"),
                            parseUuid(params, "locationId")));
            case "low-stock" -> fetchAllPaged(
                    page -> reportMapper.lowStock(page, householdId,
                            parseUuid(params, "categoryId")));
            case "stock-changes" -> fetchAllPaged(
                    page -> reportMapper.stockChanges(page, householdId,
                            parseOffsetDateTime(params, "from"),
                            parseOffsetDateTime(params, "to"),
                            parseUuid(params, "itemId"),
                            parseUuid(params, "locationId"),
                            params.get("type")));
            case "movements" -> fetchAllPaged(
                    page -> reportMapper.movements(page, householdId,
                            parseOffsetDateTime(params, "from"),
                            parseOffsetDateTime(params, "to"),
                            parseUuid(params, "itemId"),
                            params.get("type"),
                            parseUuid(params, "operatorAccountId")));
            case "items-full" -> fetchAllSearch(
                    (q, limit) -> searchMapper.searchItems(householdId, q, limit),
                    params.getOrDefault("q", ""));
            case "locations-full" -> fetchAllSearch(
                    (q, limit) -> searchMapper.searchLocations(householdId, q, limit),
                    params.getOrDefault("q", ""));
            default -> throw new IllegalArgumentException("Unknown reportKey: " + reportKey);
        };
    }

    private List<Map<String, Object>> fetchAllPaged(PageFunction pageFunction) {
        List<Map<String, Object>> allRows = new ArrayList<>();
        int pageNum = 1;
        while (true) {
            Page<?> page = new Page<>(pageNum, PAGE_SIZE);
            IPage<Map<String, Object>> result = pageFunction.apply(page);
            allRows.addAll(result.getRecords());
            if (allRows.size() > MAX_ROWS || result.getCurrent() >= result.getPages()) {
                break;
            }
            pageNum++;
        }
        return allRows;
    }

    private List<Map<String, Object>> fetchAllSearch(SearchFunction searchFunction, String query) {
        // 对于搜索类报表，用一个足够大的 limit 拉取全部数据
        return new ArrayList<>(searchFunction.apply(query, MAX_ROWS));
    }

    private List<String> getHeaders(String reportKey) {
        return switch (reportKey) {
            case "stock-by-location" -> List.of("location_path", "item_name", "lot_number",
                    "serial_number", "unit_name", "quantity", "expiry_date");
            case "expiring-lots" -> List.of("lot_number", "serial_number", "item_name",
                    "location_path", "quantity", "expiry_date", "days_until_expiry");
            case "low-stock" -> List.of("item_name", "total_quantity", "low_stock_threshold");
            case "stock-changes" -> List.of("item_name", "type", "quantity_delta",
                    "from_location_path", "to_location_path", "operator_display_name",
                    "reason", "business_time");
            case "movements" -> List.of("item_name", "type", "quantity_delta",
                    "from_location_path", "to_location_path", "operator_display_name",
                    "reason", "reversal_of", "business_time");
            case "items-full" -> List.of("item_name", "brand", "tags", "category", "unit");
            case "locations-full" -> List.of("name", "path");
            default -> throw new IllegalArgumentException("Unknown reportKey: " + reportKey);
        };
    }

    // --- 参数解析辅助方法 ---

    private static UUID parseUuid(Map<String, String> params, String key) {
        String val = params.get(key);
        return (val == null || val.isBlank()) ? null : UUID.fromString(val);
    }

    private static int parseInt(Map<String, String> params, String key, int defaultValue) {
        String val = params.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        return Integer.parseInt(val);
    }

    private static OffsetDateTime parseOffsetDateTime(Map<String, String> params, String key) {
        String val = params.get(key);
        if (val == null || val.isBlank()) return null;
        return OffsetDateTime.parse(val);
    }

    // --- 函数式接口 ---

    @FunctionalInterface
    private interface PageFunction {
        IPage<Map<String, Object>> apply(Page<?> page);
    }

    @FunctionalInterface
    private interface SearchFunction {
        List<Map<String, Object>> apply(String query, int limit);
    }
}
