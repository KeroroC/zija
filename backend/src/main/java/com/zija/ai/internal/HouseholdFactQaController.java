package com.zija.ai.internal;

import com.zija.ZijaRequestIdFilter;
import com.zija.ZijaPrincipal;
import com.zija.household.RequireMember;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 家庭问答统一入口。回答范围由服务端根据问题、页面上下文和用户选择共同解析。
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
            @Valid ScopeRequest scope,
            String answerScope,
            @Valid ScopeRequest pageContext,
            @Size(max = 3, message = "已确认范围过多") List<@Valid ScopeRequest> confirmedScopes
    ) {
    }

    record ScopeRequest(
            @NotBlank(message = "范围类型不能为空") String type,
            @NotNull(message = "范围对象不能为空") UUID id,
            String label
    ) {
    }

    /**
     * 家庭问答。家庭与权限由服务端从当前认证成员推导。
     */
    @RequireMember
    @PostMapping("/qa")
    HouseholdFactQaModels.Answer ask(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody QaRequest request,
            HttpServletRequest httpRequest
    ) {
        HouseholdFactQaModels.QaTargetInput scope = request.scope() == null
                ? null
                : new HouseholdFactQaModels.QaTargetInput(
                        request.scope().type(), request.scope().id(), request.scope().label());
        HouseholdFactQaModels.QaTargetInput pageContext = request.pageContext() == null
                ? null
                : new HouseholdFactQaModels.QaTargetInput(
                        request.pageContext().type(), request.pageContext().id(), request.pageContext().label());
        List<HouseholdFactQaModels.QaTargetInput> confirmedScopes = request.confirmedScopes() == null
                ? List.of()
                : request.confirmedScopes().stream()
                .map(candidate -> new HouseholdFactQaModels.QaTargetInput(
                        candidate.type(), candidate.id(), candidate.label()))
                .toList();
        return qaService.ask(
                principal.getAccountId(),
                new HouseholdFactQaModels.QaInput(
                        request.question(), scope, request.answerScope(), pageContext, confirmedScopes),
                String.valueOf(httpRequest.getAttribute(ZijaRequestIdFilter.ATTRIBUTE)));
    }
}
