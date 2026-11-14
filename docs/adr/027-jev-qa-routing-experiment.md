# ADR 027：Jev 问答路由试验（可关闭）

## 状态

已接受（试验特性，默认关闭）

## 背景

家庭只读问答已有确定性范围词表（`QaScopePlanner`）与家庭事实 `@Tool` 全量挂载。为在不改写路径、不生成回答正文的前提下试探 TypeSafe Jev（System One）决策模型，引入可选的 Jev `choice` 路由。

## 决策

1. **范围（AUTO）**：`JevQaScopeRecommender` 在 `zija.ai.jev.enabled=true` 且配置了 API 密钥时，对 `HOUSEHOLD_FACT` / `KNOWLEDGE_SOURCE` / `BOTH` 做 `choice`；低置信或失败时回退词表。用户显式 `answerScope` 不受影响。
2. **事实 tool 预路由**：`JevFactToolPrerouter` 在调用 ChatClient 前按 `HouseholdFactToolIntent` 收窄工具子集；低置信或失败时挂全量工具。`structuredFactFallback` 与 Jev 共用 `HouseholdFactIntentLexicon` 意图标签。
3. **配置**：`zija.ai.jev.*`（环境变量 `ZIJA_AI_JEV_*`），默认 `enabled=false`；密钥不得入库。
4. **客户端**：默认 OpenRouter Decisions API（`POST …/api/alpha/decisions`），通过 `JevDecisionClient` 抽象，可替换为 TypeSafe `/v1/decide` 等价实现。

## 非目标

不用 Jev 生成回答、写库、改提醒 reconcile/severity、全局搜索、自动确认同名或知识 grounding。

## 回退

关闭 `ZIJA_AI_JEV_ENABLED` 或清空 `ZIJA_AI_JEV_API_KEY` 即与现网词表 + 全量 tool 行为一致。
