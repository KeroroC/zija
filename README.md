# 知家 · zija

知家是面向单个家庭、多位成员的私有化物品与库存管理系统。采用 Spring Modulith 模块化单体架构，覆盖物品分类、存储位置、库存批次与流水、盘点、提醒、报表与 CSV 导出、文件存储与完整性检查等功能。

## 技术栈

- **后端：** Java 25, Spring Boot 4.1.x, Spring Modulith 2.0.5, MyBatis-Plus 3.5.16, Flyway, PostgreSQL 17
- **前端：** Vue 3, TypeScript, Vite 7, Vue Router 4, Pinia 3, Element Plus, Vitest, Playwright
- **基础设施：** Docker Compose (postgres + app + web/nginx), Maven Wrapper, npm

## 本地要求

- JDK 25
- Node.js 24
- Docker Engine 与 Docker Compose v2
- curl

## 首次准备

~~~bash
cp .env.example .env
npm --prefix frontend install
npm --prefix frontend exec -- playwright install chromium
~~~

将 `.env` 中的数据库密码改为仅用于本机开发的值。

## 本地开发

先启动数据库：

~~~bash
make dev-db
~~~

分别在两个终端启动后端和前端：

~~~bash
make dev-backend
make dev-frontend
~~~

浏览器访问 <http://localhost:5173>。

## 模块架构

系统采用 Spring Modulith 模块化单体架构，按业务能力划分模块：

- **system** - 健康检查、安装信息、审计日志查询
- **identity** - 认证、用户与会话管理
- **household** - 家庭管理、引导、邀请
- **catalog** - 物品分类
- **location** - 存储位置
- **file** - 文件存储与完整性检查
- **inventory** - 批次（Lot）、库存位、流水（Movement）、盘点（Stocktake）、幂等与一致性
- **reminder** - 提醒规则与通知
- **reporting** - 读模型投影、报表查询端口、CSV 导出

每个模块遵循 `com.zija.<module>` 包结构，包含公共 API 接口和内部实现。模块边界通过 `ModularityTests` 自动验证。

## 核心功能

### 首次初始化

1. 空库启动后访问站点，会自动跳转到 `/bootstrap`
2. 填写家庭名称、所有者用户名/密码/显示名
3. 提交后自动登录并进入首页

### 登录与会话

- 登录页：`/login`
- 会话 Cookie：`ZIJA_SESSION`（HttpOnly、SameSite=Lax；生产 HTTPS 下 Secure）
- CSRF：Cookie `XSRF-TOKEN` + Header `X-XSRF-TOKEN`
- 个人资料页可修改密码；成功后当前账户全部会话失效，需重新登录

### 邀请成员

1. 以 Owner/Admin 登录，打开「成员管理」
2. 点击「创建邀请」，选择角色与有效期
3. 复制一次性链接（Token 位于 URL fragment，不会进入 Nginx 访问日志）
4. 受邀人打开链接完成账户创建并自动登录

### 成员管理

- Owner 可任命/撤销 Admin、停用 Admin/Member、转移所有权
- Admin 只能管理普通成员
- 停用成员会同步禁用账户并清理其全部会话

### 所有者恢复

在 Compose 栈运行时执行：

~~~bash
make recover-owner
~~~

命令以非 Web 模式运行，向终端输出一次性恢复链接（`/owner-recovery#token=...`，15 分钟有效）。访问链接重置密码后，旧会话立即失效。

### 库存

- 物品（Item）描述「是什么」；批次（Lot）描述某次购入或独立资产，独立到期与库存
- 库存位（Stock Position）是某批次在某位置的当前数量，由不可变流水（Movement）作为事实来源
- 流水类型：入库 / 领用 / 报损 / 盘点调整 / 移位 / 冲正
- 盘点（Stocktake）通过投影重建（Snapshot Ports）支持重新生成读模型

### 提醒与通知

- Owner/Admin 在「提醒规则」页配置规则（最低库存阈值、过期提醒等）
- 通知中心（`/notifications`）展示未读提醒
- 后端通过 SMTP 发送邮件提醒（可选）

### 报表与数据交换

- 报表模块提供只读查询端口（Query Port），外部模块通过公开 API 获取数据，不暴露原始实体或 Mapper
- 支持 CSV 导出
- 公共领域事件（Public Domain Event）字段只能追加，不可重排或删除

### 文件完整性

- `GET /api/v1/files/integrity-report` 检查已上传文件的完整性
- 与备份恢复流程配合使用

### API 规范

- 所有业务端点位于 `/api/v1` 下
- 错误响应采用 RFC 7807 Problem Details 格式，包含稳定的 `errorCode`、`requestId` 和字段级验证错误
- 请求头 `X-Request-Id` 用于请求追踪（UUID 格式）

## 环境配置

所有配置通过 `ZIJA_` 前缀的环境变量设置（参见 `.env.example`）：

- `ZIJA_DB_URL` - 数据库连接 URL
- `ZIJA_DB_USERNAME` - 数据库用户名
- `ZIJA_DB_PASSWORD` - 数据库密码
- `ZIJA_VERSION` - 应用版本号
- `ZIJA_POSTGRES_PORT` - PostgreSQL 端口
- `ZIJA_HTTP_PORT` - HTTP 服务端口

## 验证

~~~bash
make verify                  # 运行布局检查、所有测试、生产构建、git diff --check
make verify-layout           # 仅布局/模块边界检查
make compose-smoke           # Docker Compose 全栈健康检查
make e2e-smoke               # Playwright 浏览器烟雾测试
~~~

`make verify` 依次执行：布局检查（`verify-layout`）→ 后端测试 → 前端测试 → 后端生产构建 → 前端生产构建 → `git diff --check`。`make verify-layout` 可单独运行布局/模块边界检查。两个 smoke 命令会创建临时 Compose 数据卷并在结束时删除。

### 备份与恢复

~~~bash
make backup-test             # 快照当前运行栈到 ./backups/
make restore-smoke           # 用最近备份恢复临时空栈并验证
~~~

详见 [备份与恢复文档](docs/deploy/backup-restore.md)。

## 测试

~~~bash
make backend-test            # 运行后端测试
make frontend-test           # 运行前端测试
cd backend && ./mvnw test -Dtest=ClassName          # 单个后端测试类
cd backend && ./mvnw test -Dtest=ClassName#method    # 单个测试方法
npm --prefix frontend test -- --reporter=verbose     # 前端测试（详细输出）
~~~

## 部署与运维

- [部署指南](docs/deploy/deploy.md)
- [备份与恢复](docs/deploy/backup-restore.md)
- [故障排除](docs/deploy/troubleshooting.md)
- [发行说明](docs/deploy/release-notes.md)

### 腾讯云 CloudBase 云托管（单容器自包含部署）

适合希望免运维、单域同源部署的家庭用户：一个 CloudRun 容器内同时运行 PostgreSQL + Spring Boot + nginx + Vue 静态资源。

**特点**
- 单域同源：nginx 监听 `8080`，静态资源与 `/api` 反向代理到同容器后端（`127.0.0.1:8081`），无需 CORS、VPC 或外部数据库。
- 部署文件：`deploy/cloudbase/` 下放 `default.conf` 与 `entrypoint.sh`，多阶段 Dockerfile 落在仓库根 `Dockerfile.cloudbase`（这样 CloudBase 控制台表单式构建与 CLI / MCP 部署共用同一份）。
  ⚠️ 构建上下文必须是**仓库根目录**（Dockerfile 中的 `COPY frontend/…`、`COPY backend/…` 均相对仓库根），构建时用 `-f Dockerfile.cloudbase` 指定。
- 后端构建阶段显式安装 Maven 3.9.11（避免 Maven Wrapper 在无 `unzip` 的环境中降级导致构建失败）。

**部署步骤**
1. 确认 CloudBase 环境已开通「云托管（CloudRun）」（体验版套餐默认不含，需手动开通）。
2. 通过云托管控制台 / `manageCloudRun` 创建容器服务：
   - 构建配置：上下文 = 仓库根目录，`Dockerfile` 指向仓库根的 `Dockerfile.cloudbase`，服务端口 `8080`
     （控制台表单式构建与 CLI / MCP 部署共用同一份 Dockerfile；详见 `deploy/cloudbase/README.md`）
   - 环境变量（示例）：
     ```
     ZIJA_DB_URL=jdbc:postgresql://127.0.0.1:5432/zija
     ZIJA_DB_USERNAME=postgres
     ZIJA_DB_PASSWORD=postgres
     ZIJA_DB_NAME=zija
     ZIJA_VERSION=cloudbase
     ZIJA_FILE_STORAGE_PATH=/var/lib/zija/files
     MANAGEMENT_HEALTH_MAIL_ENABLED=false   # 未配置 SMTP 时关闭邮件健康检查，避免 readiness=DOWN
     ```
3. 部署完成后访问公网域名，前端会自动跳转 `/bootstrap` 完成家庭初始化。

**已知限制（重要）**
- ⚠️ **数据持久化（已实测纠正）**：CloudBase 云托管容器实例的**可写磁盘（含 `/var/lib/zija/postgresql/data` 与 `/var/lib/zija/files`）在「重新部署 / 镜像发布」时是保留的**——本项目实测：重新部署后 `POST /api/v1/household/bootstrap` 直接返回 `409 家庭已初始化`（空库应为 `201`），且 `installationId` 跨部署保持不变，证明容器磁盘未被重置。**但这是实例级持久化**：当平台因节点维护、实例回收、缩容到 0 后再拉起、或跨可用区调度而**新建实例**时，新实例使用全新镜像、磁盘为空，数据会丢失。因此：
  - 日常「重新部署」：数据保留，无需担心；
  - 需要对抗「实例被平台重建」导致的数据丢失：给容器挂载 CFS 文件存储卷（`VolumesConf`）到 `/var/lib/zija/postgresql/data` 与 `/var/lib/zija/files`，并相应增大实例规格。
- 未配置 SMTP 时，邮件提醒不可用（不影响站内通知）。

## 方案与计划

- **设计规范：** [`docs/design/redesign-visual-spec.md`](docs/design/redesign-visual-spec.md)（松间账册 / Pine Ledger 视觉规范）
- **领域词汇表：** [`CONTEXT.md`](CONTEXT.md)
- **架构决策记录（ADR）：** [`docs/adr/`](docs/adr/)（9 份）
- **历史 spec 与 plan：** [`docs/superpowers/specs/`](docs/superpowers/specs/) 与 [`docs/superpowers/plans/`](docs/superpowers/plans/)（覆盖阶段一至阶段七）
- **AI 协作约定：** [`CLAUDE.md`](CLAUDE.md)（架构、命令、风格、Agent 技能）

## 代码风格

- **Java：** 4 空格缩进，`@Configuration` 类禁用 `proxyBeanMethods`（使用 `@Configuration(proxyBeanMethods = false)`）
- **TypeScript/Vue：** 2 空格缩进
- **通用：** LF 换行符、UTF-8 编码、文件末尾换行、自动裁剪尾随空格（`.editorconfig` 强制执行）
- **提交信息：** 中文主体，英文技术前缀（如 `fix:`、`chore:`、`docs:`）
