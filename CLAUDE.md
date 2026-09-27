# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

知家 (zija) is a private-deployment household inventory management system for a single family with multiple members. It tracks durable goods and consumables across batches, locations, and stock positions with immutable movement records as the source of truth.

## Prerequisites

- **Backend:** JDK 25, Maven Wrapper (`./mvnw`), Docker (for postgres via `make dev-db`)
- **Frontend:** Node **≥ 24.0.0** (enforced in `frontend/package.json` `engines`)
- **Tooling:** `make`, `docker compose`

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

# Lint
make frontend-lint          # npm --prefix frontend run lint (ESLint + typescript-eslint)
npm --prefix frontend run lint         # ESLint over frontend/
npm --prefix frontend run lint:fix     # ESLint with --fix

# Build & verify
make verify                  # Runs layout check, all tests, production builds, git diff --check
make backup-restore-contract-test # ./scripts/test-ai-backup-restore-contract.sh (part of `make verify`)
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

- **Backend:** Java 25, Spring Boot 4.1.x, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, Spring AI 2.0 (Ollama), Flyway, PostgreSQL 17 + pgvector
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

Existing modules: `shared` (cross-cutting enums, error codes, problem helpers — open to all modules), `system` (health check, installation info, audit), `identity` (auth, users, sessions), `household` (family management, bootstrap, invitations), `catalog` (item categories), `location` (storage places), `file` (attachments, remount, recycle bin), `inventory` (lots, stock movements, stocktake, idempotency, consistency checks), `reminder` (reminder rules, notifications), `reporting` (read-model projections, CSV export, query ports), `ai` (readonly Q&A, knowledge RAG via pgvector; Spring AI types stay inside the module).

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
  api/          # HTTP client (http.ts) + domain API modules (auth, catalog, file, household, inventory, invitation, location, member, notification, reminder, reporting, owner-recovery, audit, system, ai)
  components/   # Shared components (AppShell.vue, NotificationBell.vue)
  views/        # Pages: inventory/ (stock, lot, movement, stocktake), reports/, settings/ (AiSettingsTab, ReminderRulesTab). Top-level: HomeView, LoginPage, BootstrapPage, ItemsPage, InventoryPage, LocationsPage, AttachmentsPage, QaView, CatalogSettingsPage, MembersPage, InvitationRedeemPage, NotificationsView, RemindersView, SystemStatusView, AuditLogPage, OwnerRecoveryPage, ProfilePage, NotFoundPage.
  stores/       # Pinia stores (session.ts — auth/session state)
  router/       # Vue Router configuration
  types/        # TypeScript interfaces for API responses
  utils/        # Shared helpers (date.ts, movement.ts, location.ts, aiStatus.ts, qaThread.ts, format.ts)
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
- Key variables: `ZIJA_DB_*`, `ZIJA_VERSION`, `ZIJA_POSTGRES_PORT`, `ZIJA_HTTP_PORT` (Compose host port; local `make dev-backend` still defaults to 8080), `ZIJA_SETUP_TOKEN`, `ZIJA_FILE_STORAGE_PATH`, `ZIJA_FILE_RETENTION_DAYS`, `ZIJA_AI_OLLAMA_BASE_URL`, `ZIJA_AI_CHAT_MODEL`, `ZIJA_AI_EMBEDDING_MODEL`, `ZIJA_LOG_*` (level, SQL level, path, retention). Full list: `.env.example`.

### Runtime Logging (Logback — not the audit log)

- `logback-spring.xml` owns appenders/rolling only; levels live in `application*.yml` (`com.zija` DEBUG in dev, INFO in `prod`; SQL under the `sql` logger via MyBatis `log-prefix`).
- `prod` writes `zija.log` + `error.log` (WARN+) under `ZIJA_LOG_PATH` with size/time rolling and automatic cleanup; non-prod is console only.
- Every line carries `[requestId accountId]` from MDC. Never log passwords, session/CSRF/invitation/recovery tokens, setup token, or SMTP credentials; AI question/answer text only at DEBUG. Guarded by `SensitiveValueLogTest`.
- Details and the audit-vs-runtime-log split: `docs/developer/architecture.md`.

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
- **AI is readonly and optional.** Q&A must not write inventory/attachments; answers need grounding (ADR-020). App starts without Ollama; AI calls report unavailable. Embedding models must be 1024-dim (ADR-026). Attachment domain terms (挂载点 / 改挂 / 回收站) live in `CONTEXT.md` — do not invent synonyms.

## Visual Design (松间账册 / Pine Ledger)

Full spec: `docs/design/redesign-visual-spec.md`. Token source of truth: `frontend/src/styles/tokens.css` (Element Plus `--el-*` overrides live there too).

**Concept:** 高端、精致、宁静 — 装帧克制的家庭账册，不是鲜艳 SaaS 后台。暖白纸面、极低饱和、大量留白、单一深松绿强调色。

**Hard rules for UI work:**
- Never hardcode colors/spacing/radii/shadows/fonts in components — use `--zj-*` tokens via `<style scoped>`.
- One accent only: pine (`--zj-pine-*`). Warm-green greys; no pure black; no second brand color.
- Titles use Noto Serif SC; UI body uses Inter Variable; tabular nums for numeric columns.
- 4px spacing grid; page shell `.page-container` / `.page-header` / `.page-title` patterns in `index.css`.
- Prefer surface layering over borders; hairline borders only via `--zj-line`.

## Code Style

- Java: 4-space indent, no `proxyBeanMethods` on `@Configuration` classes (use `@Configuration(proxyBeanMethods = false)`).
- TypeScript/Vue: 2-space indent.
- LF line endings, UTF-8 charset, final newline, trim trailing whitespace (`.editorconfig` enforced).
- Commit messages: Chinese body with English technical prefix (e.g., `fix:`, `chore:`, `docs:`).

## Reference docs

- `docs/design/system-design.md` — confirmed product + system design plan
- `docs/developer/architecture.md` — module-level architecture deep-dive
- `docs/developer/developers.md` — secondary dev guide (most rules consolidated into this file)
- `docs/design/redesign-visual-spec.md` — full visual design spec (松间账册)
- `docs/agents/{issue-tracker,triage-labels,domain}.md` — agent workflows

## Agent skills

### Issue tracker

GitHub Issues via `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context layout: `CONTEXT.md` + `docs/adr/` at repo root. See `docs/agents/domain.md`.
