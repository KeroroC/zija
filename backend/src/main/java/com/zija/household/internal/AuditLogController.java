package com.zija.household.internal;

import com.zija.household.RequireAdmin;
import com.zija.household.RequireMember;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/audit-logs")
class AuditLogController {

    private final SystemApi systemApi;
    private final IdentityApi identityApi;

    AuditLogController(SystemApi systemApi, IdentityApi identityApi) {
        this.systemApi = systemApi;
        this.identityApi = identityApi;
    }

    @GetMapping
    @RequireAdmin
    AuditLogPageResponse auditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) UUID actorAccountId,
            @RequestParam(required = false) String outcome
    ) {
        if (pageSize > 100) pageSize = 100;
        if (pageSize < 1) pageSize = 20;
        if (page < 1) page = 1;

        var result = systemApi.queryAuditLogs(
                null, from, to, action, actorAccountId, outcome, page, pageSize);

        // Resolve display names
        Set<UUID> accountIds = result.items().stream()
                .flatMap(e -> {
                    var s = new java.util.HashSet<UUID>();
                    if (e.actorAccountId() != null) s.add(e.actorAccountId());
                    if (e.subjectAccountId() != null) s.add(e.subjectAccountId());
                    return s.stream();
                })
                .collect(Collectors.toSet());

        Map<UUID, IdentityApi.AccountInfo> accounts = accountIds.isEmpty()
                ? Map.of()
                : identityApi.findByIds(accountIds);

        List<AuditLogItemResponse> items = result.items().stream()
                .map(e -> new AuditLogItemResponse(
                        e.id(),
                        e.action(),
                        e.outcome(),
                        e.actorAccountId(),
                        e.actorAccountId() != null
                                ? accounts.getOrDefault(e.actorAccountId(), null)
                                : null,
                        e.subjectAccountId(),
                        e.subjectAccountId() != null
                                ? accounts.getOrDefault(e.subjectAccountId(), null)
                                : null,
                        e.detail(),
                        e.ipAddress(),
                        e.requestId(),
                        e.createdAt()
                ))
                .toList();

        return new AuditLogPageResponse(items, result.total(), result.page(), result.pageSize());
    }

    record AuditLogItemResponse(
            UUID id,
            String action,
            String outcome,
            UUID actorAccountId,
            IdentityApi.AccountInfo actor,
            UUID subjectAccountId,
            IdentityApi.AccountInfo subject,
            Map<String, Object> detail,
            String ipAddress,
            String requestId,
            OffsetDateTime createdAt
    ) {
    }

    record AuditLogPageResponse(
            List<AuditLogItemResponse> items,
            long total,
            int page,
            int pageSize
    ) {
    }
}
