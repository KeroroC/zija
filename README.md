# 知家 · zija

[![zread](https://img.shields.io/badge/Ask_Zread-_.svg?style=flat&color=00b0aa&labelColor=000000&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB3aWR0aD0iMTYiIGhlaWdodD0iMTYiIHZpZXdCb3g9IjAgMCAxNiAxNiIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPHBhdGggZD0iTTQuOTYxNTYgMS42MDAxSDIuMjQxNTZDMS44ODgxIDEuNjAwMSAxLjYwMTU2IDEuODg2NjQgMS42MDE1NiAyLjI0MDFWNC45NjAxQzEuNjAxNTYgNS4zMTM1NiAxLjg4ODEgNS42MDAxIDIuMjQxNTYgNS42MDAxSDQuOTYxNTZDNS4zMTUwMiA1LjYwMDEgNS42MDE1NiA1LjMxMzU2IDUuNjAxNTYgNC45NjAxVjIuMjQwMUM1LjYwMTU2IDEuODg2NjQgNS4zMTUwMiAxLjYwMDEgNC45NjE1NiAxLjYwMDFaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00Ljk2MTU2IDEwLjM5OTlIMi4yNDE1NkMxLjg4ODEgMTAuMzk5OSAxLjYwMTU2IDEwLjY4NjQgMS42MDE1NiAxMS4wMzk5VjEzLjc1OTlDMS42MDE1NiAxNC4xMTM0IDEuODg4MSAxNC4zOTk5IDIuMjQxNTYgMTQuMzk5OUg0Ljk2MTU2QzUuMzE1MDIgMTQuMzk5OSA1LjYwMTU2IDE0LjExMzQgNS42MDE1NiAxMy43NTk5VjExLjAzOTlDNS42MDE1NiAxMC42ODY0IDUuMzE1MDIgMTAuMzk5OSA0Ljk2MTU2IDEwLjM5OTlaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik0xMy43NTg0IDEuNjAwMUgxMS4wMzg0QzEwLjY4NSAxLjYwMDEgMTAuMzk4NCAxLjg4NjY0IDEwLjM5ODQgMi4yNDAxVjQuOTYwMUMxMC4zOTg0IDUuMzEzNTYgMTAuNjg1IDUuNjAwMSAxMS4wMzg0IDUuNjAwMUgxMy43NTg0QzE0LjExMTkgNS42MDAxIDE0LjM5ODQgNS4zMTM1NiAxNC4zOTg0IDQuOTYwMVYyLjI0MDFDMTQuMzk4NCAxLjg4NjY0IDE0LjExMTkgMS42MDAxIDEzLjc1ODQgMS42MDAxWiIgZmlsbD0iI2ZmZiIvPgo8cGF0aCBkPSJNNCwxMkwxMiw0TDQsMTJaIiBmaWxsPSIjZmZmIi8%2BCjxwYXRoIGQ9Ik00LDEyTDEyLDQiIHN0cm9rZT0iI2ZmZiIgc3Ryb2tlLXdpZHRoPSIxLjUiIHN0cm9rZS1saW5lY2FwPSJyb3VuZCIvPgo8L3N2Zz4K&logoColor=ffffff)](https://zread.ai/KeroroC/zija)

![Alt](https://repobeats.axiom.co/api/embed/b5549e5a100a32c5946e4946037a784bec797f82.svg "Repobeats analytics image")

知家是面向单个家庭、多位成员的私有化物品与库存管理系统。记录日常耐用品与消耗品的批次、位置与库存数量，所有变更以不可变流水作为事实来源，支持盘点、过期提醒、报表与 CSV 导出、文件完整性检查。

- 模块化单体架构（Spring Modulith），按业务能力划分 9 个模块
- 私有部署友好的单机 Docker Compose 方案
- 单容器自包含的腾讯云 CloudBase 云托管方案，免运维
- 完整的备份 / 恢复 / 所有者账户恢复机制

---

## 目录

- [部署方式](#部署方式)
- [方式一：本地开发运行](#方式一本地开发运行)
- [方式二：Docker Compose 私有部署](#方式二docker-compose-私有部署)
- [方式三：CloudBase 云托管](#方式三cloudbase-云托管)
- [首次使用](#首次使用)
- [日常使用](#日常使用)
- [备份与恢复](#备份与恢复)
- [验证部署](#验证部署)
- [文档地图](#文档地图)

---

## 部署方式

| 场景 | 方式 | 复杂度 | 适合谁 |
|---|---|---|---|
| 本机开发与调试 | [方式一](#方式一本地开发运行) | 低 | 开发者，二次贡献者 |
| 家庭服务器 / NAS 长期托管 | [方式二](#方式二docker-compose-私有部署) | 中 | 想完全掌控自己的数据 |
| 不愿意运维服务器 | [方式三](#方式三cloudbase-云托管) | 低 | 只想要一个稳定运行的家端实例 |

---

## 方式一：本地开发运行

需要 JDK 25、Node.js 24、Docker Engine（含 Compose v2）。

```bash
# 1. 准备环境
cp .env.example .env              # 按需修改密码
npm --prefix frontend install
npm --prefix frontend exec -- playwright install chromium

# 2. 启动数据库
make dev-db

# 3. 在两个终端分别启动后端与前端
make dev-backend                 # Spring Boot，http://localhost:8080
make dev-frontend                # Vite，http://localhost:5173
```

浏览器访问 <http://localhost:5173>，首次访问会自动进入家庭初始化流程。

数据隔离预期：Postgres 17 容器由 `make dev-db` 启动，存储在临时卷中，停容器即丢——适合开发，不适合长期保存。

---

## 方式二：Docker Compose 私有部署

适合家庭服务器 / NAS 长期托管。只需要一台 Linux 主机 + Docker Engine 24+。

### 硬件建议

- CPU 2 核+
- 内存 4 GB+
- 磁盘 20 GB+（数据库 + 文件存储，按家庭物品数量估算）

### 部署步骤

```bash
# 1. 拉取代码
git clone https://github.com/KeroroC/zija.git
cd zija

# 2. 准备环境变量
cp .env.example .env
# 至少修改以下两项：
#   ZIJA_POSTGRES_PASSWORD / ZIJA_DB_PASSWORD  设强随机密码
#   ZIJA_PROFILES_ACTIVE=prod                   生产模式（关闭 Swagger；Secure Cookie 由 TLS 传输层自动决定）

# 3. 构建并启动
docker compose --env-file .env up -d --build

# 4. 验证
docker compose ps                # 三个服务应均为 healthy
```

### 必读配置

- **`ZIJA_PROFILES_ACTIVE=prod`**：生产环境必须设置。关闭 Swagger UI；会话 Cookie 的 `Secure` 标志由传输层自动决定（TLS 反代透传 `X-Forwarded-Proto: https` 时生效，见下）。
- **`ZIJA_POSTGRES_PASSWORD` 与 `ZIJA_DB_PASSWORD`**：须改为强随机值并保持一致。
- **`ZIJA_DB_URL`** 用 `postgres:5432`（Compose 服务名），不要改。
- **TLS 反向代理**：知家应用本身不处理 TLS 终止，需在前面部署 Nginx / Caddy / Traefik（并透传 `X-Forwarded-Proto: https`、下发 HSTS）。详见 [`docs/deploy/deploy.md`](docs/deploy/deploy.md) §5。

### 升级

```bash
git pull
docker compose --env-file .env up -d --build
```

数据库迁移由 Flyway 在应用启动时自动应用，无需手动操作。

### 数据持久化

| 卷名 | 容器内路径 | 用途 |
|---|---|---|
| `postgres-data` | `/var/lib/postgresql/data` | PostgreSQL 数据 |
| `zija-files` | `/var/lib/zija/files` | 用户上传文件 |

如需使用宿主机的指定目录（便于直接备份），可在 `compose.yaml` 中改为绑定挂载。

完整配置示例、TLS 反代模板、常见问题：[`docs/deploy/deploy.md`](docs/deploy/deploy.md)。

---

## 方式三：CloudBase 云托管

适合不想自己运维的家庭用户：单个 CloudRun 容器内同时运行 PostgreSQL + Spring Boot + nginx + 前端静态资源，**前后端同源同域，无需外部数据库 / VPC / CORS**。

### 特点

- 单域同源：nginx 监听 8080，静态资源与 `/api` 反向代理到同容器后端（`127.0.0.1:8081`）
- 部署文件：`deploy/cloudbase/` 下的 `default.conf` 与 `entrypoint.sh`，多阶段 Dockerfile 在仓库根 `Dockerfile.cloudbase`
- 数据库位于容器内（`127.0.0.1:5432`），不配置 VPC

### ⚠️ 镜像必须是 linux/amd64

CloudBase 节点是 amd64。在 Apple Silicon Mac 上直接 `docker build` 会产出 arm64 镜像，推上去 pod 拉取失败（`ImagePullBackOff: no match for platform`）。两条解决路径：

- **方式 A（推荐）：云端构建**。把源码交给 CloudBase，平台在 amd64 机器上构建。
- **方式 B：本地交叉构建**：`docker buildx build --platform linux/amd64 --provenance=false -f Dockerfile.cloudbase -t <镜像地址>:<版本号> --push .`。Apple Silicon 走 QEMU，**单次构建 20–40 分钟**。

### 部署步骤

1. 确认 CloudBase 环境已开通「云托管（CloudRun）」（体验版套餐默认不含，需手动开通）。
2. 服务配置：
   - 构建上下文 = 仓库根目录
   - Dockerfile 名称 = `Dockerfile.cloudbase`
   - 服务端口 = 8080
   - 规格参考：CPU 1 / 内存 2 GB / 最小实例 1 / 最大实例 3
3. 环境变量（直接全量复制）：

   ```
   ZIJA_DB_URL=jdbc:postgresql://127.0.0.1:5432/zija
   ZIJA_DB_USERNAME=postgres
   ZIJA_DB_PASSWORD=postgres
   ZIJA_DB_NAME=zija
   ZIJA_VERSION=cloudbase
   ZIJA_FILE_STORAGE_PATH=/var/lib/zija/files
   MANAGEMENT_HEALTH_MAIL_ENABLED=false
   ```

4. 部署完成后访问公网域名，自动跳转 `/bootstrap` 完成家庭初始化。

### 数据持久化

- **重新部署 / 发布新镜像**：实例磁盘保留，数据**不丢失**（实测：重部署后 `bootstrap` 返回 `409 家庭已初始化`，`installationId` 保持不变）。
- **平台新建实例**（节点维护、实例回收、缩容到 0 后重新拉起、跨可用区调度）：新实例使用全新镜像，**数据丢失**。
- **环境被删除**：服务与数据一并消失。

如需对抗后两种情况，在服务配置的 `VolumesConf` 中把 CFS 文件存储卷挂载到 `/var/lib/zija/postgresql/data` 与 `/var/lib/zija/files`（同源时也可共用一份卷）。

完整说明、平台开通、云端/交叉构建对比、常见问题：[`docs/deploy/cloudbase.md`](docs/deploy/cloudbase.md)。

---

## 首次使用

第一次访问任意部署方式启动的实例，浏览器会自动跳转到 `/bootstrap`：

1. 填写家庭名称、所有者用户名、密码、显示名
2. 提交后自动登录并进入首页

**安全提示**：`POST /api/v1/household/bootstrap` 仅在系统未初始化时开放，初始化后自动关闭。建议部署完成后立即完成引导。

---

## 日常使用

### 登录与会话

- 登录页：`/login`
- 会话 Cookie：`ZIJA_SESSION`（HttpOnly、SameSite=Lax；Secure 标志跟随传输层，TLS 反代透传 `X-Forwarded-Proto: https` 后自动带 Secure）
- 密码修改后当前账户全部会话失效，需重新登录

### 邀请成员

1. 以 Owner/Admin 登录 →「成员管理」
2.「创建邀请」→ 选择角色与有效期
3. 复制一次性链接（Token 在 URL fragment 内，不进入 Nginx 访问日志）
4. 受邀人打开链接完成账户创建并自动登录

### 所有者恢复

如忘记密码或账户被锁定，在 Compose 栈运行时执行：

```bash
make recover-owner
```

命令在容器内以非 Web 模式运行，向终端输出一次性恢复链接（`/owner-recovery#token=...`，15 分钟有效），访问后重置密码，旧会话立即失效。

### 核心功能

- 物品（Item）描述「是什么」；批次（Lot）描述某次购入或独立资产，独立到期与库存
- 库存位（Stock Position）是某批次在某位置的当前数量，由不可变流水（Movement）作为事实来源
- 流水类型：入库 / 领用 / 报损 / 盘点调整 / 移位 / 冲正
- 提醒规则：最低库存阈值、过期提醒等，由后台定时扫描触发
- 报表与 CSV 导出：报表模块通过只读查询端口提供数据
- 文件完整性：`GET /api/v1/files/integrity-report`（仅 Owner）

完整功能定义见 [`CONTEXT.md`](CONTEXT.md)。

---

## 备份与恢复

```bash
make backup-test             # 快照当前运行栈到 ./backups/
make restore-smoke           # 用最近备份恢复临时空栈并验证
```

`make backup-test` 把 PostgreSQL 转储与文件存储打包到 `ZIJA_BACKUP_DIR`（默认 `./backups/`）。`make restore-smoke` 在临时卷上恢复并验证完整性。详细流程、灾难恢复策略、自动化 cron 配置：[`docs/deploy/backup-restore.md`](docs/deploy/backup-restore.md)。

---

## 验证部署

```bash
make verify                  # 完整验证：布局检查 + 所有测试 + 生产构建 + git diff --check
make compose-smoke           # Docker Compose 全栈健康检查（启动 → 验证 → 自动清理）
make e2e-smoke               # Playwright 浏览器烟雾测试
```

`make verify` 适合开发者与 CI；`make compose-smoke` 适合部署后快速验证。两者都会创建临时卷并在结束时清理。

---

## 文档地图

### 用户与运维

- 部署指南（Docker Compose）：[`docs/deploy/deploy.md`](docs/deploy/deploy.md)
- CloudBase 云托管（详细）：[`docs/deploy/cloudbase.md`](docs/deploy/cloudbase.md)
- 备份与恢复：[`docs/deploy/backup-restore.md`](docs/deploy/backup-restore.md)
- 故障排除：[`docs/deploy/troubleshooting.md`](docs/deploy/troubleshooting.md)
- 发行说明：[`docs/deploy/release-notes.md`](docs/deploy/release-notes.md)

### 开发者

- [开发者指南](docs/developer/developers.md)（技术栈、模块架构、测试、CI、代码风格）
- [架构与模块划分](docs/developer/architecture.md)
- 架构决策记录（ADR）：[`docs/adr/`](docs/adr/)（15 份）
- 领域词汇表：[`CONTEXT.md`](CONTEXT.md)
- 系统设计：[`docs/design/system-design.md`](docs/design/system-design.md)
- 视觉规范：[`docs/design/redesign-visual-spec.md`](docs/design/redesign-visual-spec.md)
- AI 协作约定：[`CLAUDE.md`](CLAUDE.md)

---

## 许可证

详见 [LICENSE](LICENSE)。
