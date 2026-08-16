# 开发者指南

定位于希望阅读、修改或扩展知家源码的开发者。**用户视角的安装与使用**请回到 [`README.md`](../../README.md)；**二次开发规范**已合并到 [`CLAUDE.md`](../../CLAUDE.md) 与 [`docs/agents/`](../agents/)。

---

## 技术栈

- **后端：** Java 25, Spring Boot 4.1.x, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, Flyway, PostgreSQL 17 + pgvector
- **前端：** Vue 3, TypeScript, Vite 7, Vue Router 4, Pinia 3, Element Plus, Vitest, Playwright
- **基础设施：** Docker Compose (postgres + app + web/nginx), Maven Wrapper, npm

---

## 模块架构

详见 [`architecture.md`](architecture.md)。

---

## 环境配置

所有配置通过 `ZIJA_` 前缀的环境变量设置（完整列表见 `.env.example`）：

| 变量 | 用途 | 备注 |
|---|---|---|
| `ZIJA_DB_URL` | 数据库连接 URL | Docker Compose 中用 `postgres` 作为主机名 |
| `ZIJA_DB_USERNAME` / `ZIJA_DB_PASSWORD` | 数据库凭据 | 生产须替换为强随机密码 |
| `ZIJA_VERSION` | 应用版本号 | 显示在系统信息页 |
| `ZIJA_PROFILES_ACTIVE` | 运行 Profile | **生产环境须设为 `prod`**，启用 Secure 会话 Cookie 并关闭 Swagger |
| `ZIJA_POSTGRES_PORT` | PostgreSQL 容器端口 | 仅本地访问时可省略 |
| `ZIJA_HTTP_PORT` | 8088 | 容器对外 HTTP 端口 |
| `ZIJA_FILE_STORAGE_PATH` | `/var/lib/zija/files` | 上传文件存储目录 |
| `ZIJA_BACKUP_DIR` | `./backups` | 备份产物宿主机目录 |
| `ZIJA_SMTP_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` / `_FROM` / `_TLS` | SMTP 邮件 | 不配置则邮件提醒静默禁用，站内通知不受影响 |
| `ZIJA_AI_OLLAMA_BASE_URL` / `ZIJA_AI_CHAT_MODEL` / `_EMBEDDING_MODEL` | 本地 Ollama AI | 可选；Compose 会将配置传入 `app` 容器，未配置时核心业务仍可用 |

`.env` 文件由 `docker compose` 和 `make dev-backend`（通过 `set -a; . ./.env; set +a`）自动加载。

---

## 测试

### 后端

```bash
cd backend && ./mvnw -q test
cd backend && ./mvnw test -Dtest=ClassName             # 单个测试类
cd backend && ./mvnw test -Dtest=ClassName#method      # 单个方法
```

- 集成测试共用一个 JVM 级 PostgreSQL 容器（`SharedPostgres`），通过 `@DynamicPropertySource` 注入连接信息；不要自起 `PostgreSQLContainer`。
- 测试类间数据隔离通过 `TestDb.cleanAll(jdbcTemplate)` 完成；新增业务表必须在 `TestDb.TABLES` 中登记，否则 `TestDbTableCoverageTest` 会失败。
- `@AutoConfigureMockMvc` + 共享基类 `AbstractMockMvcIntegrationTest` / `AbstractWebMvcSliceTest` 提供控制器层 HTTP 测试。
- 守护测试（build 阻断）：`ModularityTests`（模块边界）、`DependencyAlignmentTests`（Testcontainers 2.x）、`NoBackgroundSchedulingInTestsTest`、`TestDbTableCoverageTest`、`OpenApiContractTest`、`DocumentationTests`。

### 前端

```bash
npm --prefix frontend test                       # 全部
npm --prefix frontend test -- ItemsPage          # 单文件
npm --prefix frontend test -- --reporter=verbose # 详细输出
npm --prefix frontend run typecheck              # 仅类型检查
npm --prefix frontend run test:e2e               # Playwright（需运行中的栈）
```

- Vitest + jsdom 环境。
- `@vue/test-utils` `mount()` + Element Plus 作为全局插件。
- API 模块通过 `vi.mock()` 在模块级打桩。

### Gotchas

- **测试中后台 `@Scheduled` 必须保持关闭**。`backend/src/test/resources/application.properties` 把所有 `zija.schedule.*` cron 设为 `-`。后台写库与每个测试类的 `TRUNCATE` 抢锁顺序反转 → CI 随机死锁。调度方法测试通过直接调用完成（`scanAt` / `sendDailyDigests` / `retryOnceNow`），由 `NoBackgroundSchedulingInTestsTest` 强制。
- **调度时区固定为 `Asia/Shanghai`**。`@Scheduled` 使用 `zone = "${zija.schedule.zone:Asia/Shanghai}"`，提醒模块 `Clock` 读取同一属性。新增调度任务与日期边界逻辑必须使用该 Clock，否则扫描日期会整体差一天。

---

## 分支与 CI

开发在 `dev` 分支进行，合并到 `main` 即视为发布。`dev` 上运行后端 `mvnw verify` 与前端测试/构建；部署烟雾测试（Compose + Playwright）仅在 `main` 的 push 或以 `main` 为目标的 PR 上运行。

CI 配置位于 `.github/workflows/ci.yml`。

---

## 代码风格

- **Java：** 4 空格缩进；`@Configuration` 类禁用 `proxyBeanMethods`（`@Configuration(proxyBeanMethods = false)`）。
- **TypeScript/Vue：** 2 空格缩进。
- **通用：** LF 换行符、UTF-8 编码、文件末尾换行、自动裁剪尾随空格（`.editorconfig` 强制）。
- **提交信息：** 主体中文，前缀与技术名词保留英文（`fix:` / `feat:` / `chore:` / `docs:` / `refactor:` / `test:`）。

---

## 视觉设计

- 规范：[`docs/design/redesign-visual-spec.md`](../design/redesign-visual-spec.md)（松间账册 / Pine Ledger）
- 系统设计：[`docs/design/system-design.md`](../design/system-design.md)
- 颜色、间距、圆角、字体、阴影全部由 `frontend/src/styles/tokens.css` 的 CSS 变量定义，**禁止在组件中硬编码色值或尺寸**。
- Element Plus 主题通过覆盖 `--el-*` 变量实现（同一文件），不修改组件源码。
- Element Plus 按需引入（`ElementPlusResolver({ importStyle: "css" })`）仅扫描 `<template>` 用法；**仅通过 JS API 调用**的组件（`ElMessageBox.confirm` / `ElMessage.error` 等）需在 `main.ts` 显式 import 对应 CSS（`el-message-box.css` / `el-overlay.css` / `el-message.css` 等），否则运行时表现为定位/可见性异常，构建不报错。

---

## 文档地图

- 架构决策记录（ADR）：[`docs/adr/`](../adr/)（15 份）
- 领域词汇表：[`CONTEXT.md`](../../CONTEXT.md)
- 视觉规范：[`docs/design/redesign-visual-spec.md`](../design/redesign-visual-spec.md)
- 系统设计：[`docs/design/system-design.md`](../design/system-design.md)
- 部署指南：[`docs/deploy/deploy.md`](../deploy/deploy.md)（Docker Compose 私有部署）
- CloudBase 云托管：[`docs/deploy/cloudbase.md`](../deploy/cloudbase.md)
- 备份与恢复：[`docs/deploy/backup-restore.md`](../deploy/backup-restore.md)
- 故障排除：[`docs/deploy/troubleshooting.md`](../deploy/troubleshooting.md)
- 发行说明：[`docs/deploy/release-notes.md`](../deploy/release-notes.md)
- AI 协作约定：[`CLAUDE.md`](../../CLAUDE.md)、[`docs/agents/`](../agents/)
