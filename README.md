# 知家 · zija

知家是面向单个家庭、多位成员的私有化物品与库存管理系统。采用 Spring Modulith 模块化单体架构，覆盖物品分类、存储位置、库存批次、文件管理等功能。

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

- **system** - 健康检查、安装信息、审计日志
- **identity** - 认证、用户管理
- **household** - 家庭管理
- **catalog** - 物品分类
- **location** - 存储位置
- **file** - 文件存储

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
make compose-smoke           # Docker Compose 全栈健康检查
make e2e-smoke               # Playwright 浏览器烟雾测试
~~~

`make verify` 运行后端、前端、模块边界、PostgreSQL Testcontainers、类型检查和生产构建。两个 smoke 命令会创建临时 Compose 数据卷并在结束时删除。

## 测试

~~~bash
make backend-test            # 运行后端测试
make frontend-test           # 运行前端测试
cd backend && ./mvnw test -Dtest=ClassName          # 单个后端测试类
cd backend && ./mvnw test -Dtest=ClassName#method    # 单个测试方法
npm --prefix frontend test -- --reporter=verbose     # 前端测试（详细输出）
~~~

## 方案与计划

- 设计方案：`docs/superpowers/specs/2026-07-18-zija-design.md`
- 阶段二设计：`docs/superpowers/specs/2026-07-20-phase2-identity-household-design.md`
- 交付路线：`docs/superpowers/plans/2026-07-19-delivery-roadmap.md`
- 阶段二计划：`docs/superpowers/plans/2026-07-20-phase2-identity-household.md`
- 工程基础计划：`docs/superpowers/plans/2026-07-19-foundation-baseline.md`

## 代码风格

- **Java：** 4 空格缩进，`@Configuration` 类禁用 `proxyBeanMethods`（使用 `@Configuration(proxyBeanMethods = false)`）
- **TypeScript/Vue：** 2 空格缩进
- **通用：** LF 换行符、UTF-8 编码、文件末尾换行、自动裁剪尾随空格（`.editorconfig` 强制执行）
- **提交信息：** 中文主体，英文技术前缀（如 `fix:`、`chore:`、`docs:`）
