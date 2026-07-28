# 阶段七：发布加固 设计方案

- **日期：** 2026-07-28
- **状态：** 已确认，作为阶段七实施计划与验收依据
- **覆盖规格：** `docs/superpowers/specs/2026-07-18-zija-design.md` §10（安全与审计）、§11.1/§11.3（业务错误、备份恢复升级）、§12.3（CI 门禁）、§13（非功能：可观察性、本地化、浏览器）、§3.1（私有部署）。
- **交付路线：** 阶段 7（发布加固）。依赖阶段 1–6 全部交付（`system` / `identity` / `household` / `catalog` / `location` / `file` / `inventory` / `reminder` / `reporting`）。
- **关联决策：** ADR-007（v1 跳过升级冒烟，仅恢复验证）、ADR-008（v1 跳过性能验证）、ADR-009（备份恢复架构：运维脚本 + 自包含目录 + 恢复时 REST 验证）。
- **领域术语：** 见 `CONTEXT.md` 「阶段七：发布加固」一节（备份 / 备份批次标识 / 恢复 / 应用版本）。

> 阶段七**不新增业务模块**，不扩展任何跨模块领域规则。它只在 `file` 模块补一个恢复验证端点、在 `system` 模块补一处文档级校验，其余全部为配置、日志、运维脚本、Make 目标与文档。`ModularityTests` 依赖方向不变。

## 1. 目标与边界

### 1.1 必须达成的结果

- 备份：`make backup-test` 产出一份自包含备份目录（含 `pg_dump`、封面文件镜像、`manifest.json`），可重复执行并落到宿主机 `./backups/`（可经 `ZIJA_BACKUP_DIR` 覆盖）。
- 恢复：`make restore-smoke` 用临时 Docker 卷启动一套空环境 Compose 栈，恢复备份后启动 app，跑完三条 REST 验证并全部通过；栈及其临时卷自动清理。
- 安全 Cookie：依靠现有两机制（`application.yml` 的 `forward-headers-strategy: native` 配 RFC1918 `internal-proxies` + `application-prod.yml` 的 `session.cookie.secure: true`），阶段七只补部署文档与 CSRF cookie 一致性确认，不新增 env 开关。
- 结构化日志：新增 `logback-spring.xml`，文本模式，pattern 含 `%X{requestId}`，prod/dev 一致（不上 JSON），满足 §13 「日志含请求追踪编号」。
- 敏感值防护：补一组对抗测试断言日志不含密码、会话标识、恢复链接、敏感配置值；不引运行时过滤器（代码纪律 + 测试）。
- 健康端点：确认 `/actuator/health/liveness` 与 `/actuator/health/readiness` 在 permit-all 范围内返回 200；readiness 组含 `readinessState` 与 `db`（已配置）；阶段七只验证不重构。
- 文档：部署、备份、恢复、故障排除、发行说明齐备（见 §9）。
- `ModularityTests` 通过；`make verify` 通过；`make compose-smoke` 通过。

### 1.2 阶段七明确不做（均见相关 ADR）

- 不交付升级冒烟——v1 是首个发布，无先前已发布版本可升（ADR-007）。
- 不交付性能验证——不引种子器、不进 CI、不产报告（ADR-008）。
- 不引 JSON 结构化日志、不引运行时日志脱敏过滤器（见 §4）。
- 不新增应用内备份端点、不为 `pg_dump` 修改 app 镜像（ADR-009）。
- 不扩 spec §4.2 角色矩阵；不新增角色能力。
- 不把 CSV 导出当备份（已在 `CONTEXT.md` 区分 `备份` 与 `导出`）。

## 2. 备份设计

### 2.1 产物结构

```
./backups/backup_<id>_<timestamp>/
  db.dump               # docker compose exec postgres pg_dump --format=custom 产出的自定义格式 dump
  files/                # zija-files 卷的完整镜像（保留原 storage_key 相对路径结构）
  manifest.json
```

`<id>` 为形如 `7af3...` 的短 UUID；`<timestamp>` 为 UTC `YYYYMMDDTHHiissZ`。备份批次标识就是目录名（见 `CONTEXT.md`「备份批次标识」）。

### 2.2 `manifest.json` schema

```jsonc
{
  "schemaVersion": "V6",                       // flyway_schema_history 中已应用迁移的最大 queued_version
  "schemaVersionInstalledOn": "2026-07-28T...", // 该最大迁移 installed_on(UTC)
  "appVersion": "1.0.0",                       // 取备份时 GET /api/v1/system/info 的 ZIJA_VERSION
  "backupId": "backup_7af3..._20260728T001500Z",
  "createdAt": "2026-07-28T00:15:00Z",
  "db": {
    "dumpFile": "db.dump",
    "sha256": "...",                            // db.dump 的整体 SHA256
    "byteSize": 123456
  },
  "files": {
    "checkedCount": 421,                        // file 表行数
    "entries": [
      { "storageKey": "ab/cd/<uuid>.jpg", "sha256": "...", "byteSize": 23456 }
    ],
    "orphanCount": 0                            // 卷上存在但 file 表无引用（仅告警）
  }
}
```

manifest 由 `scripts/backup.sh` 生成：

- `schemaVersion` / `schemaVersionInstalledOn`：`docker compose exec postgres psql -tAc "SELECT version, installed_on FROM flyway_schema_history WHERE success=TRUE ORDER BY installed_rank DESC LIMIT 1"`。
- `appVersion`：`curl -s http://localhost:<ZIJA_HTTP_PORT>/api/v1/system/info` 取 `version`。
- 每个 file 行：`psql` 导出 `(storage_key, sha256, byte_size)` 全量；脚本逐文件算文件流 SHA256 写入，同时把卷上文件拷入 `files/`。
- `db.dump` SHA256 在拷出后算。

### 2.3 触发与产物落点

- `make backup-test`：要求一套已运行的 compose 栈（先跑 `make compose-smoke` 或 `make dev-db` + `make dev-backend`）。脚本 `scripts/backup.sh` 执行备份并打印产物目录路径，以 `0` 退出表示成功。
- 备份输出目录：`${ZIJA_BACKUP_DIR:-./backups}/backup_<id>_<timestamp>/`，绑定在宿主机文件系统；不为备份引入额外的命名卷（运维侧管理即可）。
- 备份写入 `audit_log`：抓取备份过程的操作者身份在运维场景中不存在（无人经 UI 触发），因此 `backup.sh` 不写 `audit_log`。备份行为本身由宿主机运维者负责留痕（脚本 stdout 标注时间与产物路径），应用审计保持「业务动作」语义不污染。
- `files/` 由一次性容器 `docker compose run --rm --volume zija_zija-files:/src --volume <backupDir>:/dst ...` 或等价 `docker run --rm -v ...` 用 `tar/cp` 拷出。`db.dump` 由 `docker compose exec postgres pg_dump` 经宿主机重定向写盘。
- 进程式恢复的关键校验（孤儿、哈希）在 §3 复用应用能力，不在备份阶段硬性断言——备份只需如实记录。

### 2.4 备份不做什么

- 不做增量化、不保留滚动策略、不加密品——v1 只交付「一次完整备份」能力，保留期/调度/加密属宿主机运维，不在应用或 Compose 内建模。
- 不备份运行中的 Spring Session 表行（`pg_dump` 已含，恢复后即过期失效，无害——恢复等价一次全员强制重新登录，符合「恢复到空环境」语义）。
- 不备份 Compose 自身配置 `.env`：脚本在 manifest 里登记所读到的 `ZIJA_VERSION` 与 DB 连接所需变量名清单，提示运维者另行保管 `.env`，应用不解析它。

## 3. 恢复设计

### 3.1 恢复链路

```
restore-smoke →
  1) compose up 临时空栈（新 postgres-data 卷 + 新 zija-files 卷，project-name=zija-restore-<ts>）
  2) docker compose exec postgres pg_restore --clean --if-exists < db.dump
  3) 一次性容器把 <backupDir>/files/ 灌入空 zija-files 卷
  4) compose up app（Flyway 启动跑迁移；v1 下预期 no-op）
  5) REST 验证三连（见 §3.2），全绿才退出 0
  6) compose down -v 临时栈，自动清理临时卷
```

- `pg_restore --clean --if-exists` 在空库上等价于建表导入；恢复前 postgres 卷必为空（脚本校验 `SELECT count(*) FROM information_schema.tables` 为 0，非空报错中止）。
- Flyway 在 step 4 启动时比对 `flyway_schema_history` 与本地迁移：v1 恢复目标是「同版本备份」，预期无 pending；任何 pending 表示版本与 manifest 不符，恢复验证整体失败。
- 文件卷灌入用 §2.3 的镜像一次性容器，等价反向操作。

### 3.2 恢复验证端点契约

恢复 smoke 用备份产出库里的**所有者账户**登录该空恢复栈。登录凭据来自备份源栈 семьи 的所有者记录——这是测试 fixtures 约定：做 backup 所用的源栈必须用一份已知所有者密码 bootstrap，`scripts/restore.sh` 用同一密码登录。非密码泄漏而是同源恢复链的自包容测试约定。

验证三连：

| # | 调用 | 断言 |
|---|---|---|
| 1 | `GET /api/v1/system/info` | 响应 `version == manifest.appVersion`；`dbStatus`、`appStatus` 健康 |
| 2 | `GET /api/v1/files/integrity-report`（OWNER-only，新端点） | `checkedCount == manifest.files.checkedCount` 且 `missing == 0` 且 `hashMismatch == 0`（`orphanCount` 不计入失败，仅打印） |
| 3 | `GET /api/v1/inventory/consistency-check`（Owner/Admin，既有端点） | `discrepancies` 为空 |

任一失败 `make restore-smoke` 退出非 0 并打印差异。

### 3.3 `file` 模块新增 `GET /api/v1/files/integrity-report`

- 模块边界：放在 `file/internal/FileController`，OWNER-only（复用既有 `@RequireOwner` 等机制，与导出端点同模式）。不扩 `FileApi` 跨模块方法（防止 `system`/`reporting` 误用）；该端点仅供恢复验证与未来运维自检。
- 行为：遍历 `file_stored`（既有 file 表，字段含 `storage_key`、`sha256`、`byte_size`、refcount），对每条记录：
  - 卷上对应路径文件存在？
  - 重新计算文件流 SHA256 == 库中 `sha256`？
  - 字节数匹配？
- 返回：

```jsonc
{
  "checkedCount": 421,
  "missingCount": 0,
  "hashMismatchCount": 0,
  "byteSizeMismatchCount": 0,
  "orphanCount": 7,        // 卷上存在但 file 表无引用；仅报告
  "missing": [],            // 列出 storage_key（受上限限制）
  "hashMismatch": []
}
```

- 注意：恢复后 `orphanCount` 可能 > 0（备份瞬间与卷计入之间也可能有未清理的暂时孤儿）。设计决定：孤儿不视为失败，仅打印。
- `ModularityTests` 不变（端点属 `file` 模块内部 REST，不引新跨模块依赖）。

### 3.4 恢复与一致性失败时的策略

- 文件缺失/SHA 不符：硬失败（这是 spec §6.9 「图片目录必须和数据库处于同一备份策略中」的直接观测失败）。
- 库存位与流水不一致：硬失败（spec §11.2 数据完整性优先）。
- 孤儿文件：软告警（与 `file` 模块既有「软清理孤儿」定调一致）。
- 应用版本不符：硬失败（恢复链不跨版本，ADR-007）。
- schema pending：硬失败（恢复链不跨版本）。

## 4. 安全与日志

### 4.1 安全 Cookie（保留现有，仅补文档）

- 现状（不重构）：
  - `application.yml`：`server.forward-headers-strategy: native` + `server.tomcat.remoteip.internal-proxies` 限定 RFC1918。信任同一私有网内反代发来的 `X-Forwarded-Proto`，使 `request.isSecure()` 在 TLS 终止后正确为 true。
  - `application-prod.yml`：`server.servlet.session.cookie.secure: true`（prod profile 强制会话 Cookie Secure）。
  - CSRF Cookie `XSRF-TOKEN`：`CookieCsrfTokenRepository` 默认按 `request.isSecure()` 决定 Secure；TLS 终止 + 转发头作用下与会话 Cookie 一致 Secure。
- 阶段七新增工作：
  - 部署文档明确：凡用 TLS 反向代理，必须启用 prod profile 并由代理回送 `X-Forwarded-Proto=https`；不走 TLS 的实验室/本地部署继续用默认 profile，会话 Cookie 不带 Secure（符合 spec §10「生产 HTTPS 下」措辞）。
  - CSRF Cookie 一致性确认：在 `make verify` 或新增的 `scripts/test-security-headers.sh`（已存在）里加一条断言——prod profile + TLS 模拟下 `Set-Cookie: ZIJA_SESSION; Secure` 与 `Set-Cookie: XSRF-TOKEN; Secure` 同时出现。不新增 env 开关。

### 4.2 日志：新增 `logback-spring.xml`

```xml
<configuration>
  <springProperty name="appName" source="spring.application.name" defaultValue="zija"/>

  <springProfile name="prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%X{requestId:-}] %logger{40} - %msg%n</pattern>
      </encoder>
    </appender>
  </springProfile>

  <springProfile name="!prod">
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
      <encoder>
        <pattern>%d{HH:mm:ss.SSS} %-5level [%X{requestId:-}] %logger{40} - %msg%n</pattern>
      </encoder>
    </appender>
  </springProfile>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>
</configuration>
```

- 不引 JSON 编码器。spec §13 「结构化日志」在此理解为「每条日志含稳定可定位字段：时间、级别、请求 ID、logger、消息」，不强求机器可解析 JSON。
- 默认 level `INFO`；装运/排错时由 `LOGGING_LEVEL_<PKG>` 控制，§5 配置。

### 4.3 敏感值防护（对抗测试 + 代码纪律）

不引 logback `MaskingConverter`。落地方式：

- 对抗测试列入后端集成测试套件，断言以下场景的容器日志输出不含：
  - 任何形如 `ZIJA_*PASSWORD` 的 env 值、`ZIJA_DB_PASSWORD`；
  - 会话 Cookie 值（`ZIJA_SESSION=<...>`）与 CSRF Cookie 值；
  - owner-recovery 链接中的明文 token；
  - Authorization 头/Bearer 值（首期 Web 不用，但保留断言以防回归）。
- 场景：登录失败、登录成功、`POST /api/v1/owner-recovery/inspect`、`POST /api/v1/owner-recovery/reset-password`、应用启动期各采一次容器 stdout。
- 代码纪律：禁止任何 `log.*` 调用以字符串拼接传递上述值；owner-recovery 模块已只在审计与响应中持有链接精简形式，阶段七复审并补测试断言。

### 4.4 健康端点（验证为主）

- 现状（不重构）：`management.endpoint.health.probes.enabled=true`；`management.endpoint.health.group.readiness.include=readinessState,db`；`/actuator/health/**` 在 `ZijaSecurityConfiguration` 中 permit-all；`show-details=never` 防泄漏。
- 阶段七工作：
  - 显式补 `management.endpoint.health.group.liveness.include=livenessState`，使 `/actuator/health/liveness` 返回标准 `livenessState`；compose 探针只 readiness 已在用，liveness 端点留作排错。
  - `make compose-smoke` 加两条 curl 断言：`/actuator/health/liveness` 与 `/actuator/health/readiness` 均返回 200 + `"status":"UP"`。
  - 不放开 `show-details`，不暴露 env/beans/mappings。

## 5. 环境与配置

新增/明确的环境变量（全部进 `.env.example`）：

| 变量 | 默认 | 说明 |
|---|---|---|
| `ZIJA_VERSION` | `dev` | 发行版标识，写入系统信息与备份 manifest。发行时由 CI 注入 git tag |
| `ZIJA_BACKUP_DIR` | `./backups` | 备份产物根目录（宿主机） |
| `ZIJA_PROFILES_ACTIVE` | （空） | 生产设 `prod`，启用 Secure 会话 + 关闭 Swagger |
| `ZIJA_SECURE_COOKIE` | — | **不引入**；Secure 会话由 prod profile 决定 |
| `ZIJA_FORWARD_HEADERS_ENABLED` | — | **不引入**；forward-headers 由 application.yml 固定作用域私有网 |

`application-prod.yml` 维持现状（`session.cookie.secure=true` + Swagger 关闭）；阶段七不向其中新增键。

## 6. Make 目标

新增/明确（追加进 Makefile 的 `.PHONY` 与目标体）：

| 目标 | 作用 | 依赖栈 |
|---|---|---|
| `backup-test` | 对一套已运行 compose 栈执行 `scripts/backup.sh`，产出 `./backups/backup_*_*` 目录 | 需先有运行栈（`compose-smoke` 或 dev） |
| `restore-smoke` | `scripts/restore.sh` 启动临时空栈 → 恢复最近一份备份 → 跑 §3.2 三连验证 → `compose down -v` 清理 | 需存在一份备份（`backup-test` 产出） |
| `verify` | 维持不变 | 不纳入 backup/restore（耗时 + 需 Docker） |
| `verify-layout`/`backend-test`/... | 维持 | — |

- 阶段七**不新增** `upgrade-smoke`（ADR-007）与 `perf-smoke`（ADR-008）目标。
- `compose-smoke` 内追加 health/liveness 断言与安全头/Cookie 一致性断言（§4.4、§4.1）。
- `restore-smoke` 复用 `compose-smoke.sh` 既有的「临时卷 + 自动清理」模式，独立脚本以 project-name 隔离，避免污染开发卷。

## 7. 收尾测试策略

### 7.1 后端

- `FileIntegrityReportControllerTest` / `FileIntegrityServiceTest`：覆盖 `missing==0 && hashMismatch==0` 通过、人为摘除一文件触发 `missing>0`、篡改文件内容触发 `hashMismatch>0`、孤儿计数仅报告。
- 对抗日志测试（§4.3）：用 Testcontainers 采 app stdout，断言不含敏感字面量。
- `ModularityTests.fileModuleDependenciesUnchanged`：现有断言不回归，确认新端点未引入跨模块依赖。
- PostgreSQL 集成：`restore-flow` 端到端（备份一个 fixture 栈 → 清空 → 恢复 → 三连验证），用 Testcontainers 模拟空环境恢复；备份脚本本身的容器编排不在 JUnit 内跑，由 `make` 烟雾覆盖。

### 7.2 前端

- 不新增前端视图；阶段七不扩侧边栏。备份/恢复/升级是运维动作，UI 无入口（不与 ADR-009 决定冲突）。
- 若运维侧希望前端显示「最近备份时间」——**v1 明确不做**。

### 7.3 烟雾与端到端

- `make compose-smoke`：健康/liveness/安全头/Cookie 一致性断言。
- `make backup-test` + `make restore-smoke`：组成发布候选验证链；在干净检出 + 发布镜像下以发布 profile（`ZIJA_PROFILES_ACTIVE=prod`、`ZIJA_VERSION=<tag>`）跑通。

## 8. 验收门槛

1. `make verify` 通过（含新增对抗日志测试与 `file` integrity 端点测试）。
2. `make compose-smoke` 通过，含 `/actuator/health/liveness` 与 `/readiness` 200 断言、prod profile 下会话+CSRF Cookie Secure 断言。
3. `make backup-test` 在发布 profile 栈上产出自包含目录；`manifest.json` 字段齐全且 `db.dump`/`files/` 校验和自洽。
4. `make restore-smoke` 在临时空栈上恢复该备份并通过 §3.2 三连验证；栈与临时卷自动清理。
5. `ModularityTests` 通过；`file` 模块依赖方向未变。
6. `logback-spring.xml` 使日志输出含 `%X{requestId}`；对抗测试无敏感字面量。
7. §9 文档全部落库，发行说明标识确切的后端、前端、数据库、容器版本。
8. `CONTEXT.md` 反映本次新增术语；ADR-007/008/009 完整登记。

最终 V1 完成门槛维持路线图定义：`make verify`、`make compose-smoke`、`make backup-test`、`make restore-smoke` 依次通过且工作树干净。不含 `make upgrade-smoke` 与 `make perf-smoke`。

## 9. 文档交付

新增/更新（路径建议，可在实施计划中确定最终位置）：

| 文档 | 内容要点 |
|---|---|
| `docs/deploy/deploy.md` | 一套部署从拉取镜像、`.env`、prod profile、TLS 反代 + `X-Forwarded-Proto`、卷挂载、首次引导到健康检查 |
| `docs/deploy/backup-restore.md` | `make backup-test` / `make restore-smoke` 用法、备份产物结构、manifest 字段、保管 `.env` 要求、恢复失败如何回滚到空栈重试 |
| `docs/deploy/troubleshooting.md` | 健康端点含义、`requestId` 排查法、登录 Cookie Secure 误配症状、恢复验证失败诊断、孤儿文件说明 |
| `docs/deploy/release-notes.md`（或根 `CHANGELOG.md`） | 首版发行说明：确切后端/前端/PostgreSQL/容器版本、所含阶段 1–7 能力、明确不做项（升级冒烟、性能验证、CSV 导入、移动端）与后续路线指引 |
| `README.md` | 追加部署与一键恢复入口链接 |

阶段七关闭前确保以上文档与 `docs/superpowers/specs/2026-07-18-zija-design.md` 的 §10–§13 要求逐条可追溯。

## 10. 实施拆分（供 writing-plans）

建议拆为 3 个子任务：

1. **7a 日志、健康、安全配置与对抗测试**：`logback-spring.xml`、liveness group、`compose-smoke` 的 health/Cookie/安全头断言、对抗日志测试、`.env.example` 收口。
2. **7b 备份/恢复脚本与 file 完整性端点**：`scripts/backup.sh` + `scripts/restore.sh` + `make backup-test` / `make restore-smoke`、`file` 模块 `GET /api/v1/files/integrity-report` 端点 + 测试、Testcontainers 模拟恢复链测试。
3. **7c 部署与运维文档**：`docs/deploy/*` 与 `release-notes.md`、README 入口、ADR/CONTEXT 已登记的复核。

每个子任务在执行前需各自获得一份独立的实施计划文件；不得仅凭本设计文档启动实施。

## 11. 已确认关键决策

1. **v1 跳过升级冒烟**，仅交付「备份 → 空环境恢复」链（ADR-007）。
2. **v1 跳过性能验证**，不引种子器与 CI 性能门（ADR-008）。
3. **备份恢复架构 = 运维脚本触发 + 自包含目录 + 恢复时 REST 验证**；拒绝应用内 OWNER 备份端点（ADR-009）。
4. **备份批次标识 = 备份目录名**，manifest 记录 schema 基线与应用版本，与 `导出`(CSV) 严格区分（`CONTEXT.md`）。
5. **文件完整性检查由 `file` 模块承担**，新增 OWNER-only `/api/v1/files/integrity-report`；`system` 不聚合、不引新跨模块依赖；恢复验证由 `make restore-smoke` 调三连 REST。
6. **硬失败文件缺失/SHA 不符、库存位不一致、版本不符**；孤儿文件软告警。
7. **安全 Cookie 保留现有两机制**（`forward-headers native` 配 RFC1918 + prod profile 强制 Secure），不新增 env 开关；阶段七只补部署文档与 CSRF 一致性断言。
8. **日志统一文本模式含 `%X{requestId}`，不上 JSON**；敏感值靠对抗测试 + 代码纪律，不引运行时过滤器。
9. **健康端点已就绪**，阶段七只补 liveness group include 与 compose-smoke 断言。
10. **阶段七不新增业务模块、不动 `ModularityTests` 依赖方向、不扩角色矩阵**。