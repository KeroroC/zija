# 知家 · zija

知家是面向单个家庭、多位成员的私有化物品与库存管理系统。首个交付阶段提供 Java 25 Spring Boot 模块化后端、MyBatis-Plus、PostgreSQL、Vue 3 + Element Plus 桌面壳层和 Docker Compose。

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

## 验证

~~~bash
make verify
make compose-smoke
make e2e-smoke
~~~

`make verify` 运行后端、前端、模块边界、PostgreSQL Testcontainers、类型检查和生产构建。两个 smoke 命令会创建临时 Compose 数据卷并在结束时删除。

## 方案与计划

- 设计方案：`docs/superpowers/specs/2026-07-18-zija-design.md`
- 交付路线：`docs/superpowers/plans/2026-07-19-delivery-roadmap.md`
- 工程基础计划：`docs/superpowers/plans/2026-07-19-foundation-baseline.md`
