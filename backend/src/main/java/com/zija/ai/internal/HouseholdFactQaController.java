package com.zija.ai.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.RequireMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 家庭问答统一入口。本票只覆盖家庭事实范围：活跃家庭成员用自然语言查询
 * 物品、批次、库存位、流水与提醒，答案包含摘要、结构化结果与权威页面跳转。
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
            String question
    ) {
    }

    /**
     * 家庭事实问答。家庭与权限由服务端从当前认证成员推导。
     */
    @RequireMember
    @PostMapping("/qa")
    HouseholdFactQaModels.Answer ask(
            @AuthenticationPrincipal ZijaPrincipal principal,
            @Valid @RequestBody QaRequest request
    ) {
        return qaService.ask(principal.getAccountId(), new HouseholdFactQaModels.QaRequest(request.question()));
    }
}
