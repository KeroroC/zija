# AI 能力采用 Spring AI 与 PostgreSQL pgvector

状态：已批准。

知家 AI 能力阶段采用 Spring AI 作为模型接入、`ChatClient`、工具调用、文档处理和 RAG 编排框架，采用 PostgreSQL pgvector 作为知识来源的向量存储。家庭事实通过 Spring AI 工具调用连接现有模块的受控只读查询契约，附件知识通过 Spring AI 的文档检索能力连接 `PgVectorStore`；不引入独立向量数据库，也不允许 Spring AI 类型穿过现有模块公共边界。具体 LLM、embedding 模型和外部提供方仍保持可替换。

## 选择理由

- Spring AI 与现有 Spring Boot 模块化单体和 Java 技术栈一致，提供模型提供方、工具调用和检索编排的统一抽象。
- PostgreSQL 已经是知家的事实数据库和备份边界，pgvector 能让知识索引继续留在单一私有部署中，减少新的运行时服务。
- pgvector 的向量检索与元数据过滤可以承载家庭边界、挂载点范围和知识准备状态过滤；这些过滤条件由服务端生成，不交给模型决定。
- 结构化家庭事实不使用向量相似度替代确定性查询，向量存储只负责附件知识的语义召回。

## 后果

- AI 模块内部需要隔离 Spring AI 类型，并通过可测试的项目自有接口封装模型提供方、工具调用和检索结果。
- PostgreSQL 部署需要启用 pgvector 扩展，知识来源派生表需要保存附件 ID、家庭 ID、挂载范围、准备状态、文本定位信息和 embedding 元数据。
- 测试需要提供假模型和可控的向量检索 seam，不把具体模型回答质量或向量库实现细节作为业务契约。
- 当前项目处于开发阶段，计划按当前数据库基线直接调整 Flyway/数据库结构；不为历史版本兼容、旧数据迁移或升级回滚设计额外流程。
- 具体 Spring AI、模型和 embedding 版本在实现基线中锁定，但不能改变本 ADR 的框架与存储方向。

## 当前开发基线参数

本项目当前使用 Spring Boot 4.1.0、Java 25 和 Spring AI 2.0.0。Spring AI BOM 通过
`org.springframework.ai:spring-ai-bom` 导入，应用依赖固定为：

- `spring-ai-starter-model-ollama`：默认本地模型提供方，同时提供可替换的 `ChatModel` 和 `EmbeddingModel`；
- `spring-ai-starter-vector-store-pgvector`：自动配置 `PgVectorStore`；
- `com.zija.ai.AiApi`：只暴露项目自有的 prompt/reply 和 embedding records；
- `com.zija.ai.internal.SpringAiProvider`：唯一持有 `ChatClient`、`EmbeddingModel` 和工具调用适配的实现。
  Spring AI 类型只在 AI 模块内部使用，不进入其他模块的公共 API。

默认模型和连接使用环境变量配置，默认值为 Ollama `qwen2.5:7b`（聊天）与
`nomic-embed-text:latest`（embedding），地址为 `http://localhost:11434`。更换为 OpenAI、Anthropic
或其他提供方只替换 Spring AI provider starter 和对应配置，不改变 AI 自有接口或向量表。
可替换的 embedding 模型必须输出 768 维；需要其他维度时另行设计索引重建和数据库迁移，不能通过
运行时环境变量悄然改变当前表结构。
未运行 Ollama 时应用仍可启动，调用 AI 时报告模型不可用，核心业务不依赖模型可用性。

知识向量固定使用 768 维、`COSINE_DISTANCE` 和 HNSW (`vector_cosine_ops`)。向量表为
`public.ai_knowledge_chunk`，由 Flyway `V10__create_ai_knowledge_vector_store.sql` 创建；
`PgVectorStore` 的 `initialize-schema` 保持关闭，避免 Spring AI 自动建表绕过迁移审计。
`metadata` 是 JSONB，服务端必须写入并过滤以下字段：`household_id`、`mount_type`、
`mount_id`、`item_id`、`lot_id`、`attachment_id`、`readiness_status`、`page_number`、
`section_path`、`char_start`、`char_end`、`embedding_model`、`embedding_dimensions` 和
`chunker_version`。数据库从 JSONB 生成家庭、挂载点、物品/批次、附件和准备状态列，并为
这些列建立范围索引；向量相似检索只能叠加服务端生成的家庭边界过滤。

全新数据库和共享 PostgreSQL 测试容器使用 `pgvector/pgvector:pg17`。基线测试
`SpringAiBaselineIntegrationTest` 验证项目自有 provider seam、`ChatClient` 的真实工具注册与
两步 dispatch、扩展和表校验、自动配置 `VectorStore` 对 Flyway 表的向量写入、家庭/准备状态过滤、
元数据生成列、相似检索及按 ID 删除。
