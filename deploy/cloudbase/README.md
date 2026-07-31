# CloudBase 云托管部署说明（单容器自包含）

本目录用于把 zija 以**单容器**形式部署到腾讯云 CloudBase 云托管（CloudRun）。
容器内同时运行 PostgreSQL、Spring Boot 后端、nginx（前端静态资源 + `/api` 反向代理），前后端同源同域，无需外部数据库和 VPC。

| 文件 | 作用 |
| --- | --- |
| `Dockerfile.cloudbase`（仓库根） | 多阶段构建：前端 `npm build` → 后端 Maven `package` → 运行时镜像装 PostgreSQL + nginx |
| `entrypoint.sh` | 初始化并启动 PostgreSQL、以 `--server.port=8081` 启动后端、启动 nginx，并用 `wait -n` 做双进程监管 |
| `default.conf` | nginx 监听 **8080**，静态资源 + `/api`、`/actuator` 反代到 `127.0.0.1:8081` |

⚠️ **构建上下文必须是仓库根目录**。Dockerfile 里的 `COPY frontend/…`、`COPY backend/…`、
`COPY deploy/cloudbase/…` 全部相对仓库根书写，而 Docker 的 `COPY` 源路径不能超出构建上下文
（`../` 会被剥离，指向上下文外的符号链接也不跟随）。所以构建时始终在仓库根执行，
用 `-f Dockerfile.cloudbase` 指定 Dockerfile（本仓库刻意把 Dockerfile 放在根目录，
这样 CloudBase 控制台表单式构建也能直接用，详见下文）。

端口分配：**nginx = 8080（对外）**，**后端 = 8081（仅容器内）**。
后端必须让出 8080，否则会和 nginx 抢端口，表现为服务 502、日志 `Port 8080 was already in use`。

---

## ⚠️ 必读：镜像必须是 linux/amd64

CloudBase 云托管节点是 **linux/amd64**。
在 Apple Silicon（M 系列，arm64）的 Mac 上直接 `docker build` 会产出**只有 arm64 的镜像**，推上去后 pod 拉取失败：

```
failed to pull and unpack image ".../zija:latest":
no match for platform in manifest: not found
Error: ErrImagePull
Error: ImagePullBackOff
```

排查命令：

```bash
# 看远端镜像有哪些平台（正常应包含 linux/amd64）
docker manifest inspect ccr.ccs.tencentyun.com/<命名空间>/zija:latest

# 看本地镜像架构
docker image inspect <镜像> --format '{{.Os}}/{{.Architecture}}'
```

> manifest 里出现的 `unknown/unknown` 条目是构建证明（attestation），不是可运行平台，不能算数。

---

## 方式 A：云端构建（推荐）

把源码交给 CloudBase，由平台在 amd64 机器上构建，从根本上不会出现架构问题，也不用本地跑 QEMU。

1. 确认目标环境已**开通云托管**（新环境需要单独开通一次）。
2. 通过 **CLI / MCP** 部署（推荐）。这条链路会把整个仓库作为上下文上传，
   再用参数指定 Dockerfile 路径，两者解耦，因此 Dockerfile 可以留在 `deploy/cloudbase/` 下：

   ```
   serverName:  zija
   serverType:  container
   targetPath:  <仓库根目录>
   Dockerfile:  Dockerfile.cloudbase
   Port:        8080
   OpenAccessTypes: ["PUBLIC"]
   规格参考:     Cpu=1 / Mem=2 / MinNum=1 / MaxNum=3
   ```

   > 「未配置 VPC」的提示可忽略——数据库在容器内部（`127.0.0.1:5432`），不走 VPC。

3. 控制台表单式构建也可以走：`Dockerfile.cloudbase` 已在仓库根，
   与「目标目录 = `.`」同级，控制台「Dockerfile 名称」直接填 `Dockerfile.cloudbase` 即可，
   与 CLI / MCP 部署共用同一份 Dockerfile。

---

## 方式 B：本地交叉构建 amd64 并推送

适用于不方便走云端构建时。**必须**显式指定平台。

```bash
cd <仓库根目录>

# 1) 登录腾讯云镜像仓库（凭证在 CloudBase 控制台 → 云托管 → 镜像仓库）
docker login ccr.ccs.tencentyun.com

# 2) 交叉构建 amd64 并直接推送
docker buildx build \
  --platform linux/amd64 \
  --provenance=false \
  -f Dockerfile.cloudbase \
  -t ccr.ccs.tencentyun.com/<命名空间>/zija:<版本号> \
  --push .

# 3) 验证远端 manifest 里确实有 amd64
docker manifest inspect ccr.ccs.tencentyun.com/<命名空间>/zija:<版本号>
```

要点说明：

- `--platform linux/amd64`：核心参数，缺了就会复现 ImagePullBackOff。
- `--provenance=false`：去掉 `unknown/unknown` 的 attestation manifest，避免部分运行时解析异常。
- 末尾的 `.`：构建上下文必须是**仓库根目录**，不是本目录。
- Apple Silicon 上跑 amd64 走 QEMU 模拟，镜像内含 npm build 与 Maven package，**耗时通常 20–40 分钟**，属正常现象。
- 建议**用递增版本号代替 `latest`**（如 `20260731-1`）。`latest` 容易被节点缓存，推了新镜像也可能还在跑旧的。
- 推送完成后回控制台**更新服务镜像版本并重新发布**，让节点重新拉取。

---

## 环境变量（EnvParams）

在云托管服务配置中设置以下 7 项：

| 变量 | 值 | 说明 |
| --- | --- | --- |
| `ZIJA_DB_URL` | `jdbc:postgresql://127.0.0.1:5432/zija` | 指向**容器内自建** PostgreSQL，不是 CloudBase 托管 PG |
| `ZIJA_DB_USERNAME` | `postgres` | 容器内 PG 用户，仅本机可达 |
| `ZIJA_DB_PASSWORD` | `postgres` | 同上；未对外暴露 |
| `ZIJA_DB_NAME` | `zija` | `entrypoint.sh` 建库使用 |
| `ZIJA_VERSION` | `cloudbase` | 版本标识，展示于 `/api/v1/system/info` |
| `ZIJA_FILE_STORAGE_PATH` | `/var/lib/zija/files` | 上传文件目录 |
| `MANAGEMENT_HEALTH_MAIL_ENABLED` | `false` | **必须设置**。未配 SMTP 时邮件健康检查会让 readiness 探针 DOWN，导致 503 |

另有 `BACKEND_PORT`（默认 `8081`），已写在 `entrypoint.sh` 里，一般无需覆盖。

因为数据库跑在容器内部，**不需要配置 `VpcConf`**；部署工具提示「未配置 VPC」可忽略。

---

## 部署后验证

```bash
URL=https://<你的服务域名>

# 健康检查，应为 200 且 status=UP
curl -s -m 15 -w '\nHTTP %{http_code}\n' "$URL/actuator/health"

# 系统信息，应返回 installationId 与 databaseTime
curl -s -m 15 "$URL/api/v1/system/info"
```

首次部署后访问首页进行家庭初始化（`POST /api/v1/household/bootstrap`，字段为 `householdName` / `username` / `password` / `displayName`）。
接口写操作需带 CSRF：先 `GET /api/v1/auth/csrf` 拿 Cookie，再把 **`XSRF-TOKEN` Cookie 的原始值**放进 `X-XSRF-TOKEN` 请求头（响应体里的 token 是掩码值，仅用于表单参数）。

---

## 数据持久化

容器内持久化目录统一收敛在 `/var/lib/zija/` 下：PostgreSQL 数据为 `/var/lib/zija/postgresql/data`，
上传文件为 `/var/lib/zija/files`。

- **重新部署 / 发布新镜像**：实例磁盘保留，数据**不会**丢失（实测：重部署后 bootstrap 返回 `409 家庭已初始化`，`installationId` 保持不变）。
- **平台新建实例**（节点维护、实例回收、缩容到 0 后重新拉起、跨可用区调度）：新实例使用全新镜像、磁盘为空，数据**会**丢失。
- **环境被删除**：服务与数据一并消失，访问域名返回 404。

如需对抗上述后两种情况，在服务配置的 `VolumesConf` 中把 CFS 文件存储卷挂载到
`/var/lib/zija/postgresql/data` 与 `/var/lib/zija/files`（两个挂载点同源时也可共用一份卷）。

---

## 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `ImagePullBackOff` + `no match for platform` | 镜像是 arm64，节点是 amd64 | 用云端构建，或 `buildx --platform linux/amd64` 重新推送 |
| 服务 502，日志 `Port 8080 was already in use` | 后端未让出 8080 | 确认 `entrypoint.sh` 以 `--server.port=8081` 启动后端 |
| 健康检查 503、readiness DOWN | 未配 SMTP 但邮件健康检查开启 | 设置 `MANAGEMENT_HEALTH_MAIL_ENABLED=false` |
| 中文响应变成 `????` | Servlet 响应默认 ISO-8859-1 | 已在代码侧修复（`setCharacterEncoding(UTF_8)` + `server.servlet.encoding.force=true`） |
| 构建报 `tar: Cannot mkdir: Function not implemented` | `mvnw` 无 `unzip` 时降级用 tar，被构建机 seccomp 拦截 | 已在 Dockerfile 中改为显式下载 Maven zip 并用 `unzip` 解压 |
| 写接口返回 403 CSRF 无效 | 用了响应体里的掩码 token | 改用 `XSRF-TOKEN` Cookie 的原始值 |
