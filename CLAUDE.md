# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

知家 (zija) is a private-deployment household inventory management system for a single family with multiple members. It tracks durable goods and consumables across batches, locations, and stock positions with immutable movement records as the source of truth.

## Common Commands

```bash
# Local development (three terminals)
make dev-db                  # Start PostgreSQL via Docker Compose
make dev-backend             # Spring Boot (port 8080)
make dev-frontend            # Vite dev server (port 5173, proxies /api to backend)

# Tests
make backend-test            # cd backend && ./mvnw -q test
make frontend-test           # npm --prefix frontend test
cd backend && ./mvnw test -Dtest=ClassName          # Single backend test class
cd backend && ./mvnw test -Dtest=ClassName#method    # Single test method
npm --prefix frontend test -- --reporter=verbose     # Frontend with verbose output
npm --prefix frontend test -- ItemsPage              # Single frontend test file
npm --prefix frontend run typecheck                  # vue-tsc only (build runs this first)
npm --prefix frontend run test:e2e                   # Playwright directly (needs a running stack)

# Build & verify
make verify                  # Runs layout check, all tests, production builds, git diff --check
make backend-build           # cd backend && ./mvnw -q -DskipTests package
make frontend-build          # npm --prefix frontend run build (includes typecheck)

# Smoke tests (create temporary Docker volumes, auto-cleanup)
make compose-smoke           # Full Docker Compose stack health check
make e2e-smoke               # Playwright browser smoke test against Compose stack

# Layout & data safety
make verify-layout           # Layout/module-boundary check only (subset of `make verify`)
make backup-test             # Snapshot the running stack to ./backups/
make restore-smoke           # Restore the latest backup into a temp stack and verify

# Cleanup
make clean                   # Remove build artifacts

# Owner recovery (run in container)
make recover-owner           # Generate owner recovery link
```

## Tech Stack

- **Backend:** Java 25, Spring Boot 4.1.x, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, Flyway, PostgreSQL 17 + pgvector
- **Frontend:** Vue 3, TypeScript, Vite 7, Vue Router 4, Pinia 3, Element Plus, Vitest, Playwright (e2e)
- **Infra:** Docker Compose (postgres + app + web/nginx), Maven Wrapper, npm

## Architecture

### Modular Monolith (Spring Modulith)

The backend is organized as business-capability modules enforced by `ModularityTests`. Each module lives in `com.zija.<module>` with this structure:

```
com.zija.<module>/
  <Module>Api.java          # Public interface (the only cross-module contract)
  package-info.java         # @ApplicationModule annotation
  internal/                 # Implementation — NOT accessible to other modules
    <Module>Controller.java
    <Module>Service.java
    persistence/            # Mapper, Entity, XML — module-internal
```

Existing modules: `system` (health check, installation info, audit), `identity` (auth, users, sessions), `household` (family management, bootstrap, invitations), `catalog` (item categories), `location` (storage places), `file` (file storage), `inventory` (lots, stock movements, stocktake, idempotency, consistency checks), `reminder` (reminder rules, notifications), `reporting` (read-model projections, CSV export, query ports), `ai` (provider abstraction and Spring AI adapter).

**Rules:**
- External modules may only depend on another module's public `Api` interface and its public DTOs/records.
- Never import from another module's `internal` package.
- Module dependency direction is verified automatically by `ModularityTests`.

### Persistence (MyBatis-Plus)

- Simple CRUD uses MyBatis-Plus `BaseMapper` and Lambda Wrappers.
- Complex queries (inventory aggregation, reports, CSV export) use custom Mapper XML under `src/main/resources/mapper/`.
- Pagination via `PaginationInnerInterceptor(DbType.POSTGRE_SQL)` registered last in the interceptor chain.
- Optimistic locking via `OptimisticLockerInnerInterceptor` for metadata entities (items, locations, reminder rules).
- Inventory stock deduction uses explicit `SELECT ... FOR UPDATE` in custom XML, not the optimistic lock plugin.
- No global logical delete — archiving/disabling uses explicit business state fields.
- Entity classes are module-internal; they must not leak across module boundaries.
- UUID primary keys (`id-type: assign_uuid`), underscore-to-camel-case mapping enabled.

### Database Migrations (Flyway)

- SQL files in `backend/src/main/resources/db/migration/` following `V<version>__<description>.sql` naming.
- Migrations run automatically on application startup.
- All migrations must be forward-only and idempotent-safe for fresh databases.

### Frontend Structure

```
src/
  api/          # HTTP client (http.ts) + domain API modules (auth, catalog, file, household, inventory, invitation, location, member, notification, reminder, reporting, owner-recovery, audit, system)
  components/   # Shared components (AppShell.vue)
  views/        # Page-level components. Notable subdirectories: inventory/ (stock, lot, movement, stocktake), reports/ (reporting read models, CSV export). Other top-level views: HomeView, LoginPage, BootstrapPage, ItemsPage, InventoryPage, LocationsPage, CatalogSettingsPage, MembersPage, InvitationRedeemPage, NotificationsView, RemindersView, ReminderRulesSettingsView, SystemStatusView, AuditLogPage, OwnerRecoveryPage, ProfilePage, NotFoundPage.
  stores/       # Pinia stores (session.ts — auth/session state)
  router/       # Vue Router configuration
  types/        # TypeScript interfaces for API responses
  utils/        # Shared helpers (date.ts, movement.ts)
  styles/       # Global CSS — tokens.css (design tokens + Element Plus variable overrides) and index.css (shell, components)
  test/         # Test setup
```

- API calls go through the centralized `getJson<T>()` helper which handles Problem Details errors and `X-Request-Id` tracing.
- Vite proxies `/api` to `http://localhost:8080` in development.
- Pinia is for session/UI state only — server data is not cached as long-lived global state.
- Tests mock API modules with `vi.mock()`, mount components with `@vue/test-utils` + Element Plus plugin.
- Element Plus is on-demand via `ElementPlusResolver({ importStyle: "css" })` (vite.config.ts) — the resolver only scans `<template>` usage. Imperative APIs (`ElMessageBox.confirm`, `ElMessage.error`, ...) do **not** trigger CSS auto-import. When a component is used only via JS API, import its CSS explicitly in `main.ts` (e.g. `el-message-box.css`, `el-overlay.css`, `el-message.css`). Missing CSS manifests as broken positioning/visibility at runtime, never as a build error.

### API Conventions

- All business endpoints under `/api/v1`.
- Errors use RFC 7807 Problem Details with stable `errorCode`, `requestId`, and field-level validation errors.
- `X-Request-Id` header is generated per request (UUID) if not supplied or if the supplied value is unsafe; it appears in response headers, MDC logging, and error responses.
- Spring Security uses session-based auth. Permit-all endpoints: login (`POST /api/v1/auth/login`), CSRF (`GET /api/v1/auth/csrf`), household bootstrap/status, invitation inspect/redeem, owner recovery, system info, Swagger UI, actuator health. All other requests require authentication.

### Environment Configuration

- All config via environment variables prefixed with `ZIJA_` (see `.env.example`).
- `.env` file loaded by `docker compose` and by `make dev-backend` (via `set -a; . ./$(ENV_FILE); set +a`).
- Key variables: `ZIJA_DB_URL`, `ZIJA_DB_USERNAME`, `ZIJA_DB_PASSWORD`, `ZIJA_VERSION`, `ZIJA_POSTGRES_PORT`, `ZIJA_HTTP_PORT`.

### Docker Compose Services

- `postgres`: PostgreSQL 17 with pgvector and health check.
- `app`: Spring Boot JAR (built from `deploy/app/Dockerfile`), depends on healthy postgres.
- `web`: Nginx serving frontend static files + reverse-proxying `/api` to app, depends on healthy app.

## Testing Patterns

**Backend:**
- Unit tests with `@SpringBootTest` + `@MockitoBean` for mocking module APIs.
- Integration tests share ONE JVM-wide Postgres container: wire `SharedPostgres.get()` through `@DynamicPropertySource` (not `@Testcontainers`/`@ServiceConnection`). Never start your own `PostgreSQLContainer`.
- Isolation between test classes: call `TestDb.cleanAll(jdbcTemplate)` — one fixed-order `TRUNCATE`. New tables must be registered in `TestDb.TABLES`, or `TestDbTableCoverageTest` fails.
- `@AutoConfigureMockMvc` for controller-level HTTP testing; shared bases `AbstractMockMvcIntegrationTest` / `AbstractWebMvcSliceTest`.
- Guard tests: `ModularityTests` (module boundaries), `DependencyAlignmentTests` (Testcontainers 2.x), `NoBackgroundSchedulingInTestsTest`, `TestDbTableCoverageTest`, `OpenApiContractTest`, `DocumentationTests`.

**Frontend:**
- Vitest with jsdom environment.
- `@vue/test-utils` `mount()` with Element Plus as a global plugin for component tests.
- API modules mocked via `vi.mock()` at the module level.

**CI:** develop on `dev`, merge to `main` to release. `dev` runs backend `mvnw verify` plus frontend test/build; the deployment smoke job (Compose + Playwright) runs only on `main` pushes or PRs targeting `main`.

## Gotchas

- **Background schedulers must stay disabled in tests.** `backend/src/test/resources/application.properties` sets every `zija.schedule.*` cron to `-`. Background writes race with each test class's `TRUNCATE` and cause random PostgreSQL deadlocks in CI. Cover schedulers by calling their methods directly (`scanAt` / `sendDailyDigests` / `retryOnceNow`). Enforced by `NoBackgroundSchedulingInTestsTest`.
- **Schedulers are timezone-pinned.** `@Scheduled` uses `zone = "${zija.schedule.zone:Asia/Shanghai}"`, and the reminder `Clock` reads the same property. New scheduled jobs and any date-boundary logic must use that clock, not the JVM default zone — otherwise scan dates drift by a day.

## Visual Design (松间账册 / Pine Ledger)

Design spec: `docs/design/redesign-visual-spec.md`.

**Concept:** 高端、精致、宁静 — 一本装帧克制的家庭账册，不是鲜艳的 SaaS 后台。暖白纸面底色、极低饱和度、大量留白、单一深松绿强调色。

### CSS Architecture

```
src/styles/
  tokens.css    # 设计令牌 + Element Plus --el-* 变量覆盖（唯一色源）
  index.css     # 全局样式：导入 tokens.css，应用骨架、通用组件、Element Plus 细节调制
```

- 所有颜色/间距/圆角/阴影/字体通过 `tokens.css` 的 CSS 变量定义，**禁止在组件中硬编码色值**。
- Element Plus 主题通过覆盖 `--el-*` 变量实现，不修改组件源码。
- 组件样式使用 `<style scoped>`，引用 `--zj-*` 令牌。

### Color System

唯一强调色：**松绿（pine）**。所有灰色统一偏暖绿一族，禁止纯黑。

| 令牌 | 色值 | 用途 |
|---|---|---|
| `--zj-canvas` | `#F6F5F1` | 主区背景（暖纸白） |
| `--zj-surface` | `#FFFFFF` | 卡片、表格、顶栏 |
| `--zj-surface-sunken` | `#EFEDE6` | 凹陷区、筛选条底、禁用态 |
| `--zj-ink-900` | `#1F2721` | 主文字（带绿墨感，非纯黑） |
| `--zj-ink-600` | `#5A655D` | 次级文字 |
| `--zj-ink-400` | `#98A29A` | 占位、辅助 |
| `--zj-line` | `#E5E3DB` | 发丝边框 |
| `--zj-pine-800` | `#1C3A2F` | 侧边栏底、登录页底 |
| `--zj-pine-600` | `#2E5D4B` | 主按钮/主色 |
| `--zj-pine-50` | `#EFF4F0` | 行 hover 底 |
| `--zj-warning` | `#9C7426` | 低饱和赭金（仅警告） |
| `--zj-danger` | `#A3492F` | 低饱和砖红（仅删除/失败） |

规则：不引入第二种强调色；语义色去饱和；阴影带松绿/墨色调，禁止纯黑。

### Typography

字体通过 `@fontsource-variable` 自托管打包（私有部署，不走 CDN）。

| 角色 | 字体栈 | 用途 |
|---|---|---|
| 展示/标题 | `"Noto Serif SC Variable", serif` | 品牌字标、h1/h2、页标题 |
| 界面正文 | `"Inter Variable", "PingFang SC", system-ui, sans-serif` | 组件、表格、表单 |
| 数字/代码 | `"JetBrains Mono", ui-monospace, monospace` | 邀请链接、安装 ID、IP、表格数字列 |

规则：标题用衬线体（书卷气）；表格数字列用 `font-variant-numeric: tabular-nums`。

### Spacing & Layout

- **4px 网格**：间距令牌 `4 / 8 / 12 / 16 / 24 / 32 / 48 / 64`。
- **统一页面骨架**：主区 `padding: 32px 40px`；页面容器 `.page-container`（`max-width: 1120px`）；窄表单页 `.page-container-narrow`（`max-width: 440px`）。
- **页头**：`.page-header`（flex，两端对齐）+ 衬线 `.page-title`（22px）+ `.page-subtitle`（13px），下距 24px。
- 卡片内边距 24px；表格行高 ≥ 52px。

### Radius & Shadow

| 令牌 | 值 | 用途 |
|---|---|---|
| `--zj-radius-sm` | 6px | 输入框、按钮、标签 |
| `--zj-radius-md` | 10px | 卡片、表格容器 |
| `--zj-radius-lg` | 14px | 抽屉、弹窗、登录卡 |

规则：容器圆角 > 内部元素圆角；优先用底色分层，边框仅 `--zj-line` 发丝级。

### Animation

- 缓动：`--zj-ease-out: cubic-bezier(0.22, 1, 0.36, 1)`
- 时长：`--zj-dur-fast: 150ms`（hover/焦点），`--zj-dur-med: 240ms`（抽屉/弹窗）
- 按钮按下 `transform: scale(0.98)`
- 尊重 `prefers-reduced-motion`（全局关闭非必要动效）

### Key UI Patterns

- **深色登录/入口页**：`.auth-stage`（全屏 `--zj-pine-800` 底 + 噪点 + 微弱径向提亮）居中 `.auth-card`（实色暖白卡，`--zj-shadow-lg`）。
- **侧边栏**：`--zj-pine-800` 底；激活项 = 4px 左指示条 + `--zj-pine-100` 文字 + 8% 白底；菜单分两组（物品/家庭），组间 `.nav-group-label`（11px 全大写）。
- **顶栏**：56px 高；左侧家庭名；右侧角色徽章（`.zj-badge` 描边药丸）+ 登出文字按钮。
- **全局噪点**：`body::after` 固定定位 SVG noise，3% 不透明度，`pointer-events: none`，消除平面感。
- **可点击表格行**：`.table-clickable` → `cursor: pointer` + hover `--zj-pine-50`。
- **徽章**：`.zj-badge`（描边药丸）+ `.zj-badge-pine` / `.zj-badge-ink` / `.zj-badge-plain`。
- **状态点**：`.zj-dot`（7px 圆点）+ `.zj-dot-pine` / `.zj-dot-warn` / `.zj-dot-danger` / `.zj-dot-off`。

## Code Style

- Java: 4-space indent, no `proxyBeanMethods` on `@Configuration` classes (use `@Configuration(proxyBeanMethods = false)`).
- TypeScript/Vue: 2-space indent.
- LF line endings, UTF-8 charset, final newline, trim trailing whitespace (`.editorconfig` enforced).
- Commit messages: Chinese body with English technical prefix (e.g., `fix:`, `chore:`, `docs:`).

## Agent skills

### Issue tracker

GitHub Issues via `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.
