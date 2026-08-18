package com.zija.ai.internal;

import com.zija.ai.AiApi;
import com.zija.household.HouseholdApi;
import com.zija.shared.ZijaAuditOutcome;
import com.zija.system.SystemApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 家庭事实问答编排。
 *
 * <p>身份边界：从当前认证成员推导家庭与权限（{@code HouseholdApi.requireActiveMember}），
 * 家庭 ID 由服务端注入只读工具，模型不能提供或改变家庭 ID。问答只返回确定性事实 +
 * 模型摘要，事实来源不可用或模型不可用时明确说明，不补答。</p>
 */
@Service
class HouseholdFactQaService {

    private static final String REASON_ANSWERED = "ANSWERED";
    private static final String REASON_MODEL_UNAVAILABLE = "MODEL_UNAVAILABLE";

    private static final String SYSTEM_PROMPT = """
            你是知家家庭物品库存助手。你只能调用提供的工具查询当前家庭的真实数据来回答问题。
            规则：
            - 工具的返回来自家庭数据库，是你回答的唯一事实依据；没有依据绝不编造物品、数量、位置、日期或流水。
            - 如果某工具返回 "status":"UNAVAILABLE" 或没有任何工具结果能支撑回答，请明确回答「暂时无法确认」。
            - 不要生成 SQL，不要尝试写入或修改任何数据，不要自行跨页汇总。
            - 用简洁自然的中文回答：先给结论，再列关键事实。""";

    private final HouseholdApi householdApi;
    private final AiApi aiApi;
    private final HouseholdFactQueries queries;
    private final ChatClient.Builder chatClientBuilder;
    private final SystemApi systemApi;

    HouseholdFactQaService(
            HouseholdApi householdApi,
            AiApi aiApi,
            HouseholdFactQueries queries,
            ChatClient.Builder chatClientBuilder,
            SystemApi systemApi
    ) {
        this.householdApi = householdApi;
        this.aiApi = aiApi;
        this.queries = queries;
        this.chatClientBuilder = chatClientBuilder;
        this.systemApi = systemApi;
    }

    HouseholdFactQaModels.Answer ask(UUID accountId, HouseholdFactQaModels.QaRequest request) {
        var member = householdApi.requireActiveMember(accountId);
        UUID householdId = member.householdId();
        String question = request.question().trim();

        AiApi.AiStatus status = aiApi.status();
        if (!status.available()) {
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    SystemApi.AuditAction.AI_HOUSEHOLD_QA, ZijaAuditOutcome.FAILURE,
                    householdId, accountId, null, null, null,
                    Map.of("reasonCode", status.reasonCode())));
            return new HouseholdFactQaModels.Answer(
                    question, false, status.reasonCode(),
                    "AI 模型当前不可用（" + status.reasonCode() + "），暂时无法确认家庭事实。",
                    List.of(), List.of(), List.of(), OffsetDateTime.now());
        }

        var collector = new HouseholdFactQaModels.Collector();
        var tools = new HouseholdFactTools(householdId, queries, collector);
        OffsetDateTime dataTime = OffsetDateTime.now();

        ChatClient chatClient = chatClientBuilder.build();
        String summary = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .tools(tools)
                .user(question)
                .call()
                .content();

        var answer = new HouseholdFactQaModels.Answer(
                question, true, REASON_ANSWERED,
                summary != null ? summary.trim() : "",
                collector.results(),
                List.of(new HouseholdFactQaModels.AnswerSource(
                        HouseholdFactTools.CATEGORY_HOUSEHOLD_FACT, "家庭事实", dataTime,
                        !collector.factSourceUnavailable(),
                        collector.factSourceUnavailable()
                                ? "部分家庭事实来源当前不可用，相关结论按「暂时无法确认」处理"
                                : null)),
                collector.jumps(),
                dataTime);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                SystemApi.AuditAction.AI_HOUSEHOLD_QA, ZijaAuditOutcome.SUCCESS,
                householdId, accountId, null, null, null,
                Map.of("reasonCode", REASON_ANSWERED,
                        "modelAvailable", Boolean.TRUE.toString(),
                        "structuredResultCount", String.valueOf(answer.structuredResults().size()),
                        "jumpCount", String.valueOf(answer.jumps().size()))));
        return answer;
    }
}
