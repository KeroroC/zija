# 备份与恢复

知家的备份恢复由宿主机运维脚本驱动，不经过应用内部。备份产物是一个自包含目录，恢复过程在临时空栈中完成并通过 REST 端点自动验证。

## 前置条件

- Docker Compose 栈正在运行（`make compose-smoke` 可通过）
- 宿主机安装 `curl`、`python3`
- macOS 需要 `shasum`（系统自带）；Linux 需要 `sha256sum`

## 备份

```bash
make backup-test
```

该命令调用 `scripts/backup.sh`，执行以下步骤：

1. 在 postgres 容器内运行 `pg_dump --format=custom`，导出到 `db.dump`
2. 从 `flyway_schema_history` 读取当前 schema 版本
3. 通过 `/api/v1/system/info` 获取应用版本
4. 遍历 `stored_file` 表，从 Docker 文件卷拷出所有文件
5. 计算 `db.dump` 和每个文件的 SHA256 校验和
6. 生成 `manifest.json` 并原子移动到最终目录

### 备份产物结构

```
backups/
  backup_<short-id>_<timestamp>/
    db.dump            # PostgreSQL custom-format dump
    files/             # 文件卷镜像（storage_key 为路径）
    manifest.json      # 元数据清单
```

批次标识（目录名）格式为 `backup_<8位UUID>_<UTC时间戳>`，例如 `backup_a1b2c3d4_20260728T093000Z`。

### manifest.json 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `schemaVersion` | string | Flyway 最新成功迁移版本号（如 `V6`） |
| `schemaVersionInstalledOn` | string | 该 schema 版本的安装时间 |
| `appVersion` | string | 备份时运行的应用版本（来自 `ZIJA_VERSION`） |
| `backupId` | string | 批次标识，与目录名一致 |
| `createdAt` | string | 备份完成的 UTC 时间（ISO 8601） |
| `db.dumpFile` | string | 固定为 `db.dump` |
| `db.sha256` | string | `db.dump` 的 SHA256 校验和 |
| `db.byteSize` | number | `db.dump` 文件大小（字节） |
| `files.checkedCount` | number | 备份的文件总数 |
| `files.entries` | array | 每个文件的 `storageKey`、`sha256`、`byteSize` |
| `files.orphanCount` | number | 孤儿文件计数（当前始终为 0） |

### 备份目录配置

默认备份输出到 `./backups/`。可通过环境变量覆盖：

```bash
ZIJA_BACKUP_DIR=/path/to/backups make backup-test
```

## 恢复验证

```bash
make restore-smoke
```

该命令调用 `scripts/restore.sh`，在**临时空栈**中完成恢复并自动验证。整个过程不影响正在运行的生产栈。

### 执行流程

1. **定位最新备份** -- 从 `./backups/` 中选取最新的 `backup_*_*` 目录，读取 `manifest.json`
2. **启动临时 PostgreSQL** -- 以独立 Compose 项目名（`zija-restore-<timestamp>`）启动空 postgres 实例
3. **验证空库** -- 确认临时数据库无任何表（恢复要求空库起点）
4. **恢复数据库** -- 将 `db.dump` 拷入容器并执行 `pg_restore --clean --if-exists`
5. **恢复文件卷** -- 将 `files/` 目录内容拷入临时 Docker 卷
6. **启动应用** -- 拉起 web 服务，等待 `/actuator/health` 返回 200
7. **三重验证**：
   - `GET /api/v1/system/info` -- 应用版本与 manifest 中的 `appVersion` 一致
   - `GET /api/v1/files/integrity-report` -- `missingCount` 和 `hashMismatchCount` 均为 0，`checkedCount` 与 manifest 的 `files.checkedCount` 一致
   - `GET /api/v1/inventory/consistency-report` -- `discrepancies` 数组长度为 0
8. **清理** -- 自动销毁临时 Compose 项目（含数据卷）

验证三连使用备份中的 owner 账户（默认 `owner@test.com`，密码通过 `ZIJA_OWNER_PASSWORD` 环境变量配置，默认 `TestPass123!`）登录。

### 恢复目录配置

与备份相同，通过 `ZIJA_BACKUP_DIR` 指定备份目录路径：

```bash
ZIJA_BACKUP_DIR=/path/to/backups make restore-smoke
```

端口通过 `ZIJA_HTTP_PORT` 配置（默认 8088），需与 `.env` 中的设置一致。

## .env 文件

**备份不包含 `.env` 文件。** `.env` 包含数据库密码等敏感信息，需要单独保管。

恢复到新环境时，必须先准备 `.env` 文件（参考 `.env.example`），确保以下变量与目标环境匹配：

- `ZIJA_DB_URL` / `ZIJA_POSTGRES_USER` / `ZIJA_POSTGRES_DB`
- `ZIJA_DB_PASSWORD`
- `ZIJA_OWNER_PASSWORD`（恢复验证脚本需要此变量登录）
- `ZIJA_HTTP_PORT`

建议将 `.env` 与备份产物存放在同一安全位置，或使用独立的密钥管理方案。

## 恢复失败处理

如果 `make restore-smoke` 失败，脚本会自动清理临时栈并退出。常见问题及处理：

### 备份目录不存在

```
ERROR: 未找到备份目录（./backups/backup_*_*）
```

先运行 `make backup-test` 创建备份。

### 数据库非空

```
ERROR: 数据库非空（N 张表），恢复要求空库
```

临时栈的 postgres 数据卷未清空。脚本会在退出时自动清理；如需手动处理：

```bash
docker compose -p zija-restore-<timestamp> down -v
```

### 应用未就绪

```
ERROR: 应用未就绪
```

检查 Docker 日志定位原因：

```bash
docker compose -p zija-restore-<timestamp> logs --tail=30
```

常见原因：镜像版本不匹配、端口冲突、`.env` 配置错误。

### 验证失败

三重验证中任一环节失败时脚本返回非零退出码并输出具体失败项。检查：

- 版本不匹配 -- 备份的应用版本与当前运行的镜像版本不一致，确认使用同版本镜像
- 文件完整性失败 -- `db.dump` 或文件卷在备份后被篡改，重新备份
- 库存一致性失败 -- 备份时数据已存在不一致，排查业务逻辑

## 真实环境恢复

`make restore-smoke` 用于验证备份有效性。如需在生产环境执行真实恢复：

1. 停止当前运行的栈：
   ```bash
   docker compose down -v
   ```
2. 准备 `.env` 文件（从备份保管处取回）
3. 启动空 postgres：
   ```bash
   docker compose up -d postgres
   ```
4. 等待 postgres 就绪后，手动执行恢复：
   ```bash
   # 恢复数据库
   docker compose cp backups/<backup-dir>/db.dump postgres:/tmp/db.dump
   docker compose exec -T postgres pg_restore --clean --if-exists \
     -U "${ZIJA_POSTGRES_USER:-zija}" -d "${ZIJA_POSTGRES_DB:-zija}" /tmp/db.dump

   # 恢复文件卷
   docker run --rm \
     -v "$(pwd)/backups/<backup-dir>/files:/src:ro" \
     -v "zija_zija-files:/dst" \
     alpine sh -c 'cp -r /src/. /dst/'
   ```
5. 启动完整栈：
   ```bash
   docker compose up -d
   ```
6. 等待应用就绪后手动验证：
   ```bash
   curl -sf http://localhost:<port>/actuator/health
   ```

## 注意事项

- **恢复等价全员强制重新登录**：恢复后所有活跃会话失效，所有成员需要重新登录。这是因为 session 数据存储在数据库中，`pg_restore --clean` 会清空 session 表。
- 备份过程中数据库仍可正常读写，`pg_dump` 使用一致性快照不影响业务。
- v1 仅支持「同版本备份到空环境恢复」，不支持跨版本升级恢复（参见 [ADR-007](../adr/007-v1-skips-upgrade-smoke-restore-only.md)）。
- 备份架构设计参见 [ADR-009](../adr/009-backup-restore-architecture.md)。
