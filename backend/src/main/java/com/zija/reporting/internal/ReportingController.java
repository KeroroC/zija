package com.zija.reporting.internal;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import com.zija.reporting.internal.export.ExportService;
import com.zija.reporting.internal.projection.ProjectionRebuilder;
import com.zija.reporting.internal.reports.ReportService;
import com.zija.reporting.internal.search.SearchService;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reporting")
class ReportingController {

    private final SearchService searchService;
    private final ReportService reportService;
    private final ExportService exportService;
    private final ProjectionRebuilder projectionRebuilder;
    private final HouseholdApi householdApi;

    ReportingController(SearchService searchService, ReportService reportService,
                         ExportService exportService, ProjectionRebuilder projectionRebuilder,
                         HouseholdApi householdApi) {
        this.searchService = searchService;
        this.reportService = reportService;
        this.exportService = exportService;
        this.projectionRebuilder = projectionRebuilder;
        this.householdApi = householdApi;
    }

    // --- 全局搜索（成员可读） ---

    @RequireMember
    @GetMapping("/search")
    Map<String, Object> search(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam String q,
            @RequestParam(defaultValue = "5") int limitPerGroup) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        int limit = Math.min(Math.max(limitPerGroup, 1), 20);
        return searchService.search(member.householdId(), q.trim(), limit);
    }

    // --- 报表查询（成员可读） ---

    @RequireMember
    @GetMapping("/reports/stock-by-location")
    Map<String, Object> stockByLocation(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) UUID brandId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.stockByLocation(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                itemId, categoryId, locationId, brandId);
        return toPageResponse(result);
    }

    @RequireMember
    @GetMapping("/reports/expiring-lots")
    Map<String, Object> expiringLots(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "30") int withinDays,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.expiringLots(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                Math.max(withinDays, 1), itemId, locationId);
        return toPageResponse(result);
    }

    @RequireMember
    @GetMapping("/reports/low-stock")
    Map<String, Object> lowStock(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) UUID categoryId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.lowStock(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100), categoryId);
        return toPageResponse(result);
    }

    @RequireMember
    @GetMapping("/reports/stock-changes")
    Map<String, Object> stockChanges(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) UUID locationId,
            @RequestParam(required = false) String type) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.stockChanges(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                from, to, itemId, locationId, type);
        return toPageResponse(result);
    }

    @RequireMember
    @GetMapping("/reports/movements")
    Map<String, Object> movements(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID operatorAccountId) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        var result = reportService.movements(member.householdId(),
                Math.max(page, 1), Math.min(Math.max(pageSize, 1), 100),
                from, to, itemId, type, operatorAccountId);
        return toPageResponse(result);
    }

    // --- 导出（ADMIN+） ---

    @RequireAdmin
    @GetMapping("/exports/{reportKey}")
    ResponseEntity<StreamingResponseBody> export(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable String reportKey,
            @RequestParam Map<String, String> params) {
        var member = householdApi.requireActiveMember(principal.getAccountId());

        String filename = "zija-" + reportKey + "-" + System.currentTimeMillis() + ".csv";
        StreamingResponseBody body = out -> exportService.exportToStream(
                member.householdId(), reportKey, params, out);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(body);
    }

    // --- 投影重建（ADMIN+） ---

    @RequireAdmin
    @PostMapping("/projection/rebuild")
    Map<String, Object> rebuildProjection(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @RequestParam UUID householdId) {
        householdApi.requireActiveMember(principal.getAccountId());
        projectionRebuilder.rebuild(householdId);
        return Map.of("status", "ok", "householdId", householdId);
    }

    // --- 辅助 ---

    private Map<String, Object> toPageResponse(IPage<?> page) {
        var response = new LinkedHashMap<String, Object>();
        response.put("items", page.getRecords());
        response.put("total", page.getTotal());
        response.put("page", page.getCurrent());
        response.put("pageSize", page.getSize());
        return response;
    }
}
