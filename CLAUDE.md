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

# Build & verify
make verify                  # Runs layout check, all tests, production builds, git diff --check
make backend-build           # cd backend && ./mvnw -q -DskipTests package
make frontend-build          # npm --prefix frontend run build (includes typecheck)

# Smoke tests (create temporary Docker volumes, auto-cleanup)
make compose-smoke           # Full Docker Compose stack health check
make e2e-smoke               # Playwright browser smoke test against Compose stack

# Cleanup
make clean                   # Remove build artifacts

# Owner recovery (run in container)
make recover-owner           # Generate owner recovery link
```

## Tech Stack

- **Backend:** Java 25, Spring Boot 4.1.x, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, Flyway, PostgreSQL 17
- **Frontend:** Vue 3, TypeScript, Vite 7, Vue Router 4, Pinia 3, Element Plus, Vitest, Playwright
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

Existing modules: `system` (health check, installation info, audit), `identity` (auth, users), `household` (family management), `catalog` (item categories), `location` (storage places), `file` (file storage). Planned: `inventory`, `reminder`, `reporting`.

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
  api/          # HTTP client (http.ts) and API functions (system.ts)
  components/   # Shared components (AppShell.vue)
  views/        # Page-level components
  router/       # Vue Router configuration
  types/        # TypeScript interfaces for API responses
  styles/       # Global CSS — tokens.css (design tokens + Element Plus variable overrides) and index.css (shell, components)
  test/         # Test setup
```

- API calls go through the centralized `getJson<T>()` helper which handles Problem Details errors and `X-Request-Id` tracing.
- Vite proxies `/api` to `http://localhost:8080` in development.
- Pinia is for session/UI state only — server data is not cached as long-lived global state.
- Tests mock API modules with `vi.mock()`, mount components with `@vue/test-utils` + Element Plus plugin.

### API Conventions

- All business endpoints under `/api/v1`.
- Errors use RFC 7807 Problem Details with stable `errorCode`, `requestId`, and field-level validation errors.
- `X-Request-Id` header is generated per request (UUID) if not supplied or if the supplied value is unsafe; it appears in response headers, MDC logging, and error responses.
- Spring Security currently permits only `GET /api/v1/system/info` and actuator health endpoints; all other requests are denied. Auth will be added in the identity module phase.

### Environment Configuration

- All config via environment variables prefixed with `ZIJA_` (see `.env.example`).
- `.env` file loaded by `docker compose` and by `make dev-backend` (via `set -a; . ./$(ENV_FILE); set +a`).
- Key variables: `ZIJA_DB_URL`, `ZIJA_DB_USERNAME`, `ZIJA_DB_PASSWORD`, `ZIJA_VERSION`, `ZIJA_POSTGRES_PORT`, `ZIJA_HTTP_PORT`.

### Docker Compose Services

- `postgres`: PostgreSQL 17 Alpine with health check.
- `app`: Spring Boot JAR (built from `deploy/app/Dockerfile`), depends on healthy postgres.
- `web`: Nginx serving frontend static files + reverse-proxying `/api` to app, depends on healthy app.

## Testing Patterns

**Backend:**
- Unit tests with `@SpringBootTest` + `@MockitoBean` for mocking module APIs.
- Integration tests with Testcontainers (`@Testcontainers` + `@ServiceConnection` + `PostgreSQLContainer`) for real database testing.
- `@AutoConfigureMockMvc` for controller-level HTTP testing.
- Module boundary verification: `ModularityTests` ensures `ApplicationModules.verify()` passes.
- Dependency alignment: `DependencyAlignmentTests` confirms Testcontainers 2.x is used.

**Frontend:**
- Vitest with jsdom environment.
- `@vue/test-utils` `mount()` with Element Plus as a global plugin for component tests.
- API modules mocked via `vi.mock()` at the module level.

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
