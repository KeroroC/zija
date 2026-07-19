# 工程基础审查问题修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复工程基础阶段审查发现的 Flyway 未执行、readiness 未检查数据库、异常被转成 403，以及 Testcontainers 与 Docker Engine 29 不兼容的问题。

**Architecture:** 继续使用现有 Spring Boot 模块化单体结构，仅补齐 Spring Boot 4 所需的官方 starter、健康组配置和统一错误处理。测试以真实 PostgreSQL Testcontainers 为边界，覆盖空库迁移、数据库断连和真实 SecurityFilterChain 行为，避免 MockMvc mock 掩盖部署问题。

**Tech Stack:** Java 25、Spring Boot 4.1.0、Spring Security 7.1、Spring Boot Actuator、Flyway 12、Testcontainers 2、PostgreSQL 17、JUnit 5、MockMvc、Maven

---

### Task 1: 恢复 Spring Boot 依赖版本对齐

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/test/java/com/zija/DependencyAlignmentTests.java`

- [ ] **Step 1: 编写失败测试**

新增测试，断言运行时使用 Spring Boot 4.1 管理的 Testcontainers 2.x，而不是项目覆盖的 1.21.3。

- [ ] **Step 2: 验证测试按预期失败**

Run: `cd backend && ./mvnw -q -Dtest=DependencyAlignmentTests test`

Expected: FAIL，实际版本为 `1.21.3`。

- [ ] **Step 3: 实施最小修复**

删除 `backend/pom.xml` 中手工导入的 Testcontainers 1.21.3 BOM，并将测试依赖切换为 Testcontainers 2 的模块坐标，让 Spring Boot parent 统一管理版本。

- [ ] **Step 4: 验证依赖对齐测试通过**

Run: `cd backend && ./mvnw -q -Dtest=DependencyAlignmentTests test`

Expected: PASS，Testcontainers 版本为 2.x。

### Task 2: 恢复空库 Flyway 自动迁移

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/zija/system/internal/persistence/PostgresUuidTypeHandler.java`
- Modify: `backend/src/main/java/com/zija/system/internal/persistence/SystemInstallationEntity.java`
- Modify: `backend/src/test/java/com/zija/system/internal/persistence/SystemInstallationMapperIntegrationTest.java`

- [ ] **Step 1: 强化现有集成测试**

在读取安装记录前，查询 `flyway_schema_history`，明确断言 V1 迁移已由应用启动自动执行。

- [ ] **Step 2: 验证测试按预期失败**

Run: `cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test`

Expected: FAIL，空库中不存在 `flyway_schema_history` 或 `system_installation`。

- [ ] **Step 3: 实施最小修复**

使用 `org.springframework.boot:spring-boot-starter-flyway` 替代裸 `flyway-core`，保留 PostgreSQL Flyway 数据库模块；为 PostgreSQL UUID 增加持久化层 TypeHandler，并让实体生成包含非主键字段的 ResultMap。

- [ ] **Step 4: 验证空库迁移测试通过**

Run: `cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test`

Expected: PASS，Flyway V1 已执行，安装记录可读取。

### Task 3: 让 readiness 反映数据库状态

**Files:**
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/java/com/zija/system/internal/SystemReadinessIntegrationTest.java`

- [ ] **Step 1: 编写数据库断连失败测试**

启动真实 PostgreSQL 和完整应用，先断言 readiness 为 200，再停止 PostgreSQL 并断言 readiness 为 503。

- [ ] **Step 2: 验证测试按预期失败**

Run: `cd backend && ./mvnw -q -Dtest=SystemReadinessIntegrationTest test`

Expected: FAIL，数据库停止后 readiness 仍为 200。

- [ ] **Step 3: 实施最小修复**

配置 `management.endpoint.health.group.readiness.include` 为 `readinessState,db`。

- [ ] **Step 4: 验证 readiness 测试通过**

Run: `cd backend && ./mvnw -q -Dtest=SystemReadinessIntegrationTest test`

Expected: PASS，数据库停止后 readiness 返回 503。

### Task 4: 保留真实异常状态和 Problem Detail

**Files:**
- Modify: `backend/src/main/java/com/zija/ZijaSecurityConfiguration.java`
- Modify: `backend/src/main/java/com/zija/system/internal/SystemExceptionHandler.java`
- Modify: `backend/src/test/java/com/zija/system/internal/SystemControllerTest.java`

- [ ] **Step 1: 编写错误分派与数据库异常失败测试**

覆盖 `/error` 的 ERROR dispatcher 可进入错误处理链，并覆盖数据库访问失败时返回 HTTP 500、`application/problem+json`、稳定 `errorCode` 和 `requestId`。

- [ ] **Step 2: 验证测试按预期失败**

Run: `cd backend && ./mvnw -q -Dtest=SystemControllerTest test`

Expected: FAIL，当前异常被安全配置转换为空 403，或数据库异常没有稳定 Problem Detail。

- [ ] **Step 3: 实施最小修复**

允许 `DispatcherType.ERROR`，并将数据访问/事务连接异常统一映射为 `SystemStateUnavailableException` 对应的 Problem Detail；不暴露数据库内部信息。

- [ ] **Step 4: 验证控制器测试通过**

Run: `cd backend && ./mvnw -q -Dtest=SystemControllerTest test`

Expected: PASS。

### Task 5: 完整验证与提交

**Files:**
- Verify all modified files

- [ ] **Step 1: 运行完整静态和自动化验证**

Run: `make verify`

Expected: PASS。

- [ ] **Step 2: 运行真实部署验证**

Run: `make compose-smoke && make e2e-smoke`

Expected: 两个 smoke 均 PASS。

- [ ] **Step 3: 检查差异和工作区边界**

Run: `git diff --check && git status --short`

Expected: 无空白错误；现有路线图翻译改动仍未暂存。

- [ ] **Step 4: 精确暂存并提交**

只暂存本计划和本次修复文件，提交信息使用中文：

```bash
git commit -m "fix: 修复工程基础运行与健康检查问题"
```
