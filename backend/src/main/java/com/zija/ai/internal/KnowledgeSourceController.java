package com.zija.ai.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 知识来源端点：任何活跃家庭成员可对家庭 / 物品 / 批次附件执行选择、取消与手动重试，
 * 沿用现有家庭附件权限。异步准备状态在附件界面可见。
 *
 * <ul>
 *   <li>{@code GET    /api/v1/ai/knowledge-sources}              — 列出当前家庭全部知识来源</li>
 *   <li>{@code PUT    /api/v1/ai/knowledge-sources/{fileId}}      — 选择附件为知识来源</li>
 *   <li>{@code DELETE /api/v1/ai/knowledge-sources/{fileId}}      — 取消选定（已停用）</li>
 *   <li>{@code POST   /api/v1/ai/knowledge-sources/{fileId}/retry} — 手动重试失败来源</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ai/knowledge-sources")
class KnowledgeSourceController {

    private final KnowledgeSourceService knowledgeSourceService;
    private final HouseholdApi householdApi;

    KnowledgeSourceController(KnowledgeSourceService knowledgeSourceService, HouseholdApi householdApi) {
        this.knowledgeSourceService = knowledgeSourceService;
        this.householdApi = householdApi;
    }

    @GetMapping
    Map<String, Object> list(@AuthenticationPrincipal ZijaPrincipal principal) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        List<Map<String, Object>> items = knowledgeSourceService.list(member.householdId()).stream()
                .map(this::toItem)
                .toList();
        var response = new LinkedHashMap<String, Object>();
        response.put("items", items);
        return response;
    }

    @PutMapping("/{fileId}")
    Map<String, Object> select(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return toItem(knowledgeSourceService.select(member.householdId(), principal.getAccountId(), fileId));
    }

    @DeleteMapping("/{fileId}")
    Map<String, Object> cancel(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return toItem(knowledgeSourceService.cancel(member.householdId(), principal.getAccountId(), fileId));
    }

    @PostMapping("/{fileId}/retry")
    Map<String, Object> retry(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @PathVariable UUID fileId
    ) {
        var member = householdApi.requireActiveMember(principal.getAccountId());
        return toItem(knowledgeSourceService.retry(member.householdId(), principal.getAccountId(), fileId));
    }

    private Map<String, Object> toItem(KnowledgeSourceService.KnowledgeSourceView view) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fileId", view.fileId());
        item.put("status", view.status());
        if (view.failureCode() != null) {
            item.put("failureCode", view.failureCode());
        }
        if (view.failureMessage() != null) {
            item.put("failureMessage", view.failureMessage());
        }
        if (view.disabledReason() != null) {
            item.put("disabledReason", view.disabledReason());
        }
        if (view.nextRetryAt() != null) {
            item.put("nextRetryAt", view.nextRetryAt());
        }
        item.put("processingVersion", view.processingVersion());
        item.put("selectedAt", view.selectedAt());
        if (view.processedAt() != null) {
            item.put("processedAt", view.processedAt());
        }
        item.put("updatedAt", view.updatedAt());
        return item;
    }
}
