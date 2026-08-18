package com.zija.ai.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 家庭问答统一入口。未指定范围时查询家庭事实；指定物品或批次范围时查询可用知识来源。
 */
@RestController
@RequestMapping("/api/v1/ai")
class HouseholdFactQaController {

    private final HouseholdFactQaService qaService;

    HouseholdFactQaController(HouseholdFactQaService qaService) {
        this.qaService = qaService;
    }

    record QaRequest(
            @NotBlank(message = "问题不能为空")
            @Size(max = 2000, message = "问题过长")
            String question,
            @Valid ScopeRequest scope
    ) {
    }

    record ScopeRequest(
            @NotBlank(message = "范围类型不能为空") String type,
            @NotNull(message = "范围对象不能为空") UUID id
    ) {
    }

    /**
     * 家庭问答。家庭与权限由服务端从当前认证成员推导。
     */
    @RequireMember
    @PostMapping("/qa")
    HouseholdFactQaModels.Answer ask(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody QaRequest request
    ) {
        HouseholdFactQaModels.KnowledgeScope scope = request.scope() == null
                ? null
                : new HouseholdFactQaModels.KnowledgeScope(request.scope().type(), request.scope().id());
        return qaService.ask(
                principal.getAccountId(),
                new HouseholdFactQaModels.QaRequest(request.question(), scope));
    }
}
