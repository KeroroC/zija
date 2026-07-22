# 知家 · zija

知家是面向单个家庭、多位成员的私有化物品与库存管理系统。当前阶段已覆盖工程基础，以及身份与家庭（初始化、登录会话、邀请、成员角色、所有者恢复）。

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

## 阶段二工作流

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

## 验证

~~~bash
make verify
make compose-smoke
make e2e-smoke
~~~

`make verify` 运行后端、前端、模块边界、PostgreSQL Testcontainers、类型检查和生产构建。两个 smoke 命令会创建临时 Compose 数据卷并在结束时删除。

## 方案与计划

- 设计方案：`docs/superpowers/specs/2026-07-18-zija-design.md`
- 阶段二设计：`docs/superpowers/specs/2026-07-20-phase2-identity-household-design.md`
- 交付路线：`docs/superpowers/plans/2026-07-19-delivery-roadmap.md`
- 阶段二计划：`docs/superpowers/plans/2026-07-20-phase2-identity-household.md`
- 工程基础计划：`docs/superpowers/plans/2026-07-19-foundation-baseline.md`
