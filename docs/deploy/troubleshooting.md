# 故障排除

本文档汇总知家部署与运维中常见的故障现象、诊断步骤与修复方法。

---

## 1. 健康端点

知家暴露以下端点用于服务状态探测：

| 端点 | 说明 | 典型用途 |
|---|---|---|
| `GET /healthz` | Nginx 层直接返回 `200 ok`，不经过应用 | 负载均衡器 / 外部监控探活 |
| `GET /actuator/health/liveness` | 仅检查应用进程是否存活（`livenessState`） | K8s liveness probe |
| `GET /actuator/health/readiness` | 检查数据库连接等依赖是否就绪（`readinessState` + `db`） | K8s readiness probe / Compose healthcheck |
| `GET /actuator/health` | 综合健康信息（生产环境不暴露内部细节） | 手动诊断 |

### liveness vs readiness 的区别

- **liveness 失败**：应用进程卡死或 OOM，需要重启容器。
- **readiness 失败**：数据库尚未就绪或连接池耗尽，容器暂时不可接收流量，但不需要重启。Compose 的 `depends_on: condition: service_healthy` 会在 postgres 就绪后才启动 app，首次启动时 readiness 可能短暂返回 `DOWN`。

```bash
# 快速检查
curl -s http://localhost:8088/actuator/health/liveness
# {"status":"UP"}

curl -s http://http://localhost:8088/actuator/health/readiness
# {"status":"UP"} 或 {"status":"DOWN"}
```

如果 readiness 持续 `DOWN`，检查数据库连接配置和 postgres 容器状态：

```bash
docker compose ps postgres
docker compose logs postgres --tail=20
docker compose logs app --tail=50
```

---

## 2. requestId 日志追踪

每个 HTTP 请求都会被分配一个唯一的 `X-Request-Id`（UUID 格式），用于全链路追踪：

1. **生成**：`ZijaRequestIdFilter` 在请求进入时生成（或复用客户端提供的合法 ID）。
2. **响应头**：同一 ID 写入响应头 `X-Request-Id`，前端可用此 ID 关联请求。
3. **MDC**：写入 SLF4J MDC（key 为 `requestId`），日志中以 `[requestId]` 形式出现。

### 排查步骤

```bash
# 1. 从前端浏览器 DevTools 或 curl 响应头中获取 requestId
curl -si http://localhost:8088/api/v1/system/info
# X-Request-Id: a1b2c3d4-e5f6-7890-abcd-ef1234567890

# 2. 在容器日志中搜索该 ID
docker compose logs app | grep 'a1b2c3d4-e5f6-7890-abcd-ef1234567890'
```

日志格式示例：

```
2026-07-28 09:30:00.123 [http-nio-8080-exec-1] INFO  [a1b2c3d4-...] c.z.s.i.SystemController - ...
```

方括号中的 `a1b2c3d4-...` 即为 requestId，可用于追踪同一次请求在过滤器、控制器、服务层的完整调用链。

### 注意事项

- 客户端提供的 `X-Request-Id` 必须仅含 `[A-Za-z0-9._-]` 且长度不超过 100 字符，否则会被替换为自动生成的 UUID。
- 反代应透传 `X-Request-Id` 头（见 [deploy.md](deploy.md) 的 Nginx 配置示例）。

---

## 3. Cookie Secure 误配

### 症状

- 登录接口返回 200，但浏览器不存储 `ZIJA_SESSION` Cookie。
- 登录后立即跳回登录页，前端始终处于未认证状态。
- `curl` 手动登录可正常获取 Cookie，但浏览器不行。

### 原因

生产 Profile（`ZIJA_PROFILES_ACTIVE=prod`）启用了 `server.servlet.session.cookie.secure=true`，表示 Cookie 仅通过 HTTPS 连接发送。如果 TLS 反代未正确设置 `X-Forwarded-Proto: https`，应用认为当前是 HTTP 连接，会发出带 `Secure` 标志的 Cookie，浏览器在 HTTP 环境下拒绝存储。

### 诊断

```bash
# 检查响应中的 Set-Cookie 头
curl -si -X POST https://zija.example.com/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"owner@test.com","password":"..."}'

# 正常应看到：
# Set-Cookie: ZIJA_SESSION=...; Secure; HttpOnly; SameSite=Lax
```

如果浏览器通过 HTTP 访问且看到 `Secure` 标志，说明反代配置有误。

### 修复

确保反代传递正确的协议标识：

**Nginx：**

```nginx
proxy_set_header X-Forwarded-Proto $scheme;
```

**Caddy：** 默认已传递，无需额外配置。

**验证反代头：**

```bash
# 从应用容器内部检查收到的头
docker compose exec app curl -s http://localhost:8080/actuator/health \
  -H "X-Forwarded-Proto: https"
```

### 开发环境临时方案

如果在纯 HTTP 环境下开发调试，不要使用 `prod` profile。默认 profile 不设置 `Secure` 标志，Cookie 可在 HTTP 下正常工作。

---

## 4. 恢复验证失败诊断

`make restore-smoke` 在临时空栈中执行恢复并运行三重验证。验证失败时脚本返回非零退出码并输出具体失败项。

### 验证 1：应用版本不匹配

```
[1/3] GET /api/v1/system/info ... FAIL (expected=1.0.0, actual=dev)
```

**含义**：备份时的应用版本与当前运行的镜像版本不一致。

**修复**：使用与备份时相同版本的镜像。检查 `.env` 中的 `ZIJA_VERSION` 是否与 `manifest.json` 中的 `appVersion` 一致，然后重新构建镜像：

```bash
# 查看备份的期望版本
python3 -c "import json; print(json.load(open('./backups/<backup-dir>/manifest.json'))['appVersion'])"

# 设置正确的版本
echo "ZIJA_VERSION=<版本号>" >> .env
docker compose --env-file .env up -d --build
```

> **注意**：v1 仅支持同版本备份到空环境恢复，不支持跨版本升级恢复。

### 验证 2：文件完整性失败

```
[2/3] GET /api/v1/files/integrity-report ... FAIL (checked=42/50, missing=3, hashMismatch=2)
```

三种子失败：

| 字段 | 含义 | 常见原因 |
|---|---|---|
| `checkedCount` 与期望不符 | 部分文件未恢复到卷上 | 文件卷拷贝不完整，检查 `files/` 目录内容 |
| `missingCount > 0` | 数据库记录的文件在卷上不存在 | 文件卷未正确挂载或恢复脚本跳过了文件拷贝 |
| `hashMismatchCount > 0` | 文件内容与备份时的 SHA256 不一致 | 文件在备份后被篡改，或卷拷贝过程中损坏 |

**修复**：

1. 确认备份目录的 `files/` 子目录内容完整。
2. 检查 Docker 卷是否正确挂载。
3. 如果是备份后篡改，重新执行 `make backup-test` 创建新备份。

### 验证 3：库存一致性失败

```
[3/3] GET /api/v1/inventory/consistency-report ... FAIL (discrepancies=5)
```

**含义**：`inventory_stock_position` 表中记录的实际数量与 `inventory_movement` 流水聚合的应有数量不一致。

**每个差异项结构**：

```json
{
  "lotId": "批次 UUID",
  "locationId": "位置 UUID",
  "expected": "流水聚合的应有数量",
  "actual": "库存位的实际数量"
}
```

**原因**：备份时数据已存在不一致（可能由异常中断或并发竞争导致），恢复过程忠实地还原了这个状态。

**处理**：

- 此报告为只读诊断，不会自动修复数据。
- 排查导致不一致的业务操作（如并发领用、异常盘点）。
- 确认差异范围后，可通过盘点功能手动校正库存数量。

---

## 5. 孤儿文件

文件完整性报告中的 `orphanCount` 字段表示**孤儿文件**数量：文件卷上存在但数据库 `stored_file` 表中没有对应记录的文件。

```bash
# 手动查看文件完整性报告
curl -sb cookies.txt -H "X-XSRF-TOKEN: <token>" \
  http://localhost:8088/api/v1/files/integrity-report
```

报告示例：

```json
{
  "checkedCount": 50,
  "missingCount": 0,
  "hashMismatchCount": 0,
  "byteSizeMismatchCount": 0,
  "orphanCount": 3,
  "missing": [],
  "hashMismatch": []
}
```

### 为什么孤儿文件不计入失败

- 孤儿文件不影响数据完整性——数据库中没有引用它们，业务逻辑不会使用它们。
- 它们通常是正常操作的副产物：文件上传失败后事务回滚但文件已写入卷、或手动调试时残留的测试文件。
- 恢复验证脚本（`restore.sh`）的三重验证中**不检查 `orphanCount`**，仅检查 `missingCount` 和 `hashMismatchCount`。

### 清理孤儿文件

孤儿文件会占用磁盘空间但不会影响功能。如需清理：

1. 从文件完整性报告中确认 `orphanCount` 数值。
2. 备份当前文件卷（以防误删）。
3. 手动比对卷上文件与 `stored_file` 表记录，删除无引用的文件。

> **建议**：在生产环境中定期运行 `GET /api/v1/files/integrity-report` 监控文件健康状态。

---

## 6. 常见问题 FAQ

### 容器启动后数据库连接失败

```bash
docker compose logs app --tail=50 | grep -i 'connection\|datasource\|postgres'
```

确认 `postgres` 已通过健康检查：

```bash
docker compose ps
# postgres 应显示 Up (healthy)
```

首次初始化 PostgreSQL 可能需要几秒，`app` 配置了 `depends_on: condition: service_healthy`，但极端情况下健康检查可能延迟。可手动重启 app：

```bash
docker compose restart app
```

### 登录后 CSRF 校验失败（403）

知家使用 Spring Security 的 CSRF 保护。前端需要先 `GET /api/v1/auth/csrf` 获取 token，然后在后续请求中通过 `X-XSRF-TOKEN` 头携带。

如果使用 `curl` 测试：

```bash
# 1. 登录并保存 Cookie
curl -sc cookies.txt -X POST http://localhost:8088/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"owner@test.com","password":"..."}'

# 2. 获取 CSRF token
CSRF=$(curl -sb cookies.txt http://localhost:8088/api/v1/auth/csrf \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")

# 3. 后续请求携带 token
curl -sb cookies.txt -H "X-XSRF-TOKEN: $CSRF" \
  http://localhost:8088/api/v1/inventory/consistency-report
```

### Swagger UI 无法访问

生产 Profile 默认关闭 Swagger UI。确认 `.env` 中 `ZIJA_PROFILES_ACTIVE` 的值：

```bash
grep ZIJA_PROFILES_ACTIVE .env
```

开发环境如需启用，可设置 `ZIJA_SWAGGER_ENABLED=true` 或不使用 `prod` profile。

### 磁盘空间不足

```bash
# 查看 Docker 卷占用
docker system df

# 查看应用日志大小
docker compose logs --tail=0 --follow

# 清理旧备份
ls -lt ./backups/ | head -20
```

建议配置 cron 定时执行 `make backup-test` 并定期清理超过保留期限的备份。

### Flyway 迁移失败

应用启动时 Flyway 自动执行数据库迁移。如果迁移失败，应用将无法启动。

```bash
docker compose logs app | grep -i 'flyway\|migration'
```

常见原因：

- 手动修改了数据库 schema，与 Flyway 记录不一致。
- 数据库版本降级（不支持回退迁移）。

处理方式：参考 Flyway 官方文档修复 `flyway_schema_history` 表，或从备份恢复到已知一致状态。

### 登录频率限制

知家对登录接口实施速率限制（默认：同一账户 5 分钟内最多 5 次，同一 IP 5 分钟内最多 50 次）。超限后返回 429。

```bash
# 查看当前配置
grep ZIJA_RATE_LIMIT .env
```

如需调整阈值，修改 `.env` 中的以下变量后重启应用：

```dotenv
ZIJA_RATE_LIMIT_ACCOUNT_THRESHOLD=10
ZIJA_RATE_LIMIT_ACCOUNT_WINDOW_MINUTES=5
ZIJA_RATE_LIMIT_IP_THRESHOLD=100
ZIJA_RATE_LIMIT_IP_WINDOW_MINUTES=5
```

### 会话过期后行为

默认会话超时为 24 小时。超时后前端请求会收到 401，自动跳转到登录页。恢复操作等价于全员强制重新登录（`pg_restore --clean` 会清空 session 表）。
