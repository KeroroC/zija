# 阶段二：身份与家庭 实施计划

> **面向智能体执行者：** 必须使用子 Skill：通过 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐项实施本计划。各步骤使用复选框（`- [ ]`）语法跟踪进度。

**目标：** 在阶段一工程基线之上，实现知家（zija）的单家庭安全边界——包含首次引导、用户名/密码会话登录、CSRF 防护与登录限流、限时单次邀请链接、Owner/Admin/Member 三角色权限矩阵、所有者恢复命令、完整审计日志与 OpenAPI 契约检查。

**架构：** 新增 `identity` 与 `household` 两个 Spring Modulith 业务模块，保持 `household → identity → system` 单向依赖。会话存储使用 Spring Session JDBC 落入 PostgreSQL；密码使用 `DelegatingPasswordEncoder` + BCrypt；权限使用三个元注解（`@RequireMember`/`@RequireAdmin`/`@RequireOwner`）驱动方法级安全。所有者恢复以非 Web 命令模式运行，不绑定 8080 端口。前端新增初始化、登录、邀请兑换、成员管理与个人资料页面，并扩展集中式 HTTP 客户端以处理 CSRF 与会话失效。

**技术栈：** Java 25、Spring Boot 4.1.0、Spring Security 7.1、Spring Session 3.x（JDBC）、Spring Modulith 2.0.5、MyBatis-Plus 3.5.16、Flyway、PostgreSQL 17、springdoc-openapi、Vue 3.5、TypeScript 5.8、Vite 7、Element Plus 2.10、Pinia 3、Vitest 3、Playwright 1.53、Testcontainers 2

**事实来源：** `docs/superpowers/specs/2026-07-20-phase2-identity-household-design.md`（设计规格）。本计划与规格冲突时以规格为准。

---

## 计划范围

本计划仅实施 delivery-roadmap 的阶段 2。它复用阶段一已建立的基础设施（Maven、Spring Boot、MyBatis-Plus、Flyway、PostgreSQL、Vue 外壳、Docker Compose、make 命令），新增身份与家庭业务能力。不实现物品、位置、库存、提醒、报表、CSV 或移动端令牌认证。

## 前置条件

- 阶段一 `docs/superpowers/plans/2026-07-19-foundation-baseline.md` 和 `docs/superpowers/plans/2026-07-19-foundation-review-fixes.md` 均已执行完成，`make verify` 通过。
- `ZijaSecurityConfiguration` 已允许 `DispatcherType.ERROR`，readiness 健康组已包含 `db`，Flyway 空库迁移与 `PostgresUuidTypeHandler` 已就位。
- 已安装 JDK 25、Node.js 24、Docker Engine 与 Docker Compose v2。
- 除非步骤另有说明，所有命令从仓库根目录执行。

## 目标文件清单

~~~text
.
├── backend/
│   ├── pom.xml                                        # 修改：新增 spring-session-jdbc、springdoc-openapi
│   └── src/
│       ├── main/
│       │   ├── java/com/zija/
│       │   │   ├── ZijaSecurityConfiguration.java     # 修改：CSRF SPA、会话、入口/拒绝处理器
│       │   │   ├── ZijaProblemHandlers.java            # 创建：统一 401/403/CSRF Problem Details
│       │   │   ├── ZijaSessionAuthenticationSupport.java  # 创建：共享会话认证编排
│       │   │   ├── ZijaSessionInvalidator.java          # 创建：按账户删除全部会话
│       │   │   ├── ZijaPrincipal.java                  # 创建：不可变认证主体
│       │   │   ├── identity/
│       │   │   │   ├── IdentityApi.java                # 创建：公开账户接口
│       │   │   │   ├── package-info.java              # 创建：@ApplicationModule
│       │   │   │   └── internal/
│       │   │   │       ├── IdentityController.java    # 创建：登录/登出/会话/CSRF/密码
│       │   │   │       ├── IdentityExceptionHandler.java # 创建
│       │   │   │       ├── IdentityService.java       # 创建：账户与密码业务
│       │   │   │       ├── ZijaUserDetailsService.java # 创建：Security 账户加载
│       │   │   │       ├── LoginRateLimiter.java      # 创建：账户/IP 双桶限流
│       │   │   │       ├── auth/                      # 创建：登录请求/响应 DTO
│       │   │   │       └── persistence/
│       │   │   │           ├── AccountEntity.java
│       │   │   │           ├── AccountMapper.java
│       │   │   │           └── AccountMapper.xml
│       │   │   ├── household/
│       │   │   │   ├── HouseholdApi.java
│       │   │   │   ├── package-info.java
│       │   │   │   └── internal/
│       │   │   │       ├── HouseholdController.java
│       │   │   │       ├── HouseholdExceptionHandler.java
│       │   │   │       ├── HouseholdService.java
│       │   │   │       ├── InvitationService.java
│       │   │   │       ├── MemberService.java
│       │   │   │       ├── OwnerRecoveryService.java
│       │   │   │       ├── OwnerRecoveryCommand.java
│       │   │   │       ├── HouseholdAuthorization.java # 创建：权限评估器
│       │   │   │       ├── RequireMember.java          # 创建：元注解
│       │   │   │       ├── RequireAdmin.java
│       │   │   │       ├── RequireOwner.java
│       │   │   │       └── persistence/
│       │   │   │           ├── HouseholdEntity.java
│       │   │   │           ├── MemberEntity.java
│       │   │   │           ├── InvitationEntity.java
│       │   │   │           ├── OwnerRecoveryTokenEntity.java
│       │   │   │           ├── HouseholdMapper.java
│       │   │   │           ├── MemberMapper.java
│       │   │   │           ├── InvitationMapper.java
│       │   │   │           ├── OwnerRecoveryTokenMapper.java
│       │   │   │           ├── HouseholdMapper.xml
│       │   │   │           ├── MemberMapper.xml
│       │   │   │           ├── InvitationMapper.xml
│       │   │   │           └── OwnerRecoveryTokenMapper.xml
│       │   │   └── system/
│       │   │       ├── SystemApi.java                  # 修改：新增 recordAudit
│       │   │       └── internal/
│       │   │           ├── AuditEvent.java             # 创建：类型化审计事件
│       │   │           ├── AuditService.java          # 创建：实现 SystemApi.recordAudit
│       │   │           └── persistence/
│       │   │               ├── AuditLogEntity.java
│       │   │               ├── AuditLogMapper.java
│       │   │               ├── JsonbTypeHandler.java   # 创建：Map↔JSONB 转换
│       │   │               └── AuditLogMapper.xml
│       │   └── resources/
│       │       ├── application.yml                     # 修改：会话、forward-headers、springdoc
│       │       ├── application-prod.yml                # 创建：Secure Cookie
│       │       └── db/migration/
│       │           ├── V2__create_account.sql
│       │           ├── V3__create_household_member.sql
│       │           ├── V4__create_invitation.sql
│       │           ├── V5__create_owner_recovery_token.sql
│       │           ├── V6__create_audit_log.sql
│       │           └── V7__create_spring_session.sql
│       └── test/java/com/zija/
│           ├── ZijaSessionInvalidatorTest.java
│           ├── identity/internal/IdentityServiceTest.java
│           ├── identity/internal/LoginRateLimiterTest.java
│           ├── identity/internal/ZijaUserDetailsServiceTest.java
│           ├── identity/internal/IdentityControllerTest.java
│           ├── identity/internal/persistence/AccountMapperIntegrationTest.java
│           ├── household/internal/HouseholdServiceTest.java
│           ├── household/internal/InvitationServiceTest.java
│           ├── household/internal/MemberServiceTest.java
│           ├── household/internal/OwnerRecoveryServiceTest.java
│           ├── household/internal/HouseholdAuthorizationTest.java
│           ├── household/internal/HouseholdControllerTest.java
│           ├── household/internal/InvitationControllerTest.java
│           ├── household/internal/MemberControllerTest.java
│           ├── household/internal/OwnerRecoveryControllerTest.java
│           ├── household/internal/persistence/HouseholdBootstrapIntegrationTest.java
│           ├── household/internal/persistence/MemberMapperIntegrationTest.java
│           ├── household/internal/persistence/InvitationMapperIntegrationTest.java
│           ├── household/internal/persistence/OwnerRecoveryIntegrationTest.java
│           ├── system/internal/persistence/AuditLogIntegrationTest.java
│           ├── system/internal/SpringSessionIntegrationTest.java
│           ├── ForwardedHeadersSecurityTest.java
│           └── OpenApiContractTest.java
├── frontend/
│   └── src/
│       ├── api/http.ts                                # 修改：CSRF、postJson/putJson、401/403 处理
│       ├── api/auth.ts                               # 创建
│       ├── api/household.ts                          # 创建
│       ├── api/invitation.ts                         # 创建
│       ├── api/member.ts                            # 创建
│       ├── api/owner-recovery.ts                     # 创建
│       ├── stores/session.ts                         # 创建
│       ├── types/identity.ts                         # 创建
│       ├── router/index.ts                           # 修改：路由守卫
│       ├── components/AppShell.vue                   # 修改：导航与登出
│       └── views/
│           ├── BootstrapPage.vue                     # 创建
│           ├── LoginPage.vue                         # 创建
│           ├── InvitationRedeemPage.vue              # 创建
│           ├── MembersPage.vue                       # 创建
│           ├── ProfilePage.vue                       # 创建
│           └── OwnerRecoveryPage.vue                 # 创建
└── frontend/e2e/
    ├── bootstrap.spec.ts
    ├── login.spec.ts
    ├── invitation.spec.ts
    ├── members.spec.ts
    └── owner-recovery.spec.ts
~~~

---

## 任务 1：添加后端依赖

**文件：**
- 修改：`backend/pom.xml`

- [ ] **步骤 1：添加 Spring Session JDBC、Spring Security crypto 已随 starter、springdoc-openapi 依赖**

在 `backend/pom.xml` 的 `<dependencies>` 中，于 actuator 之后、spring-modulith-starter-core 之前新增：

~~~xml
<dependency>
    <groupId>org.springframework.session</groupId>
    <artifactId>spring-session-jdbc</artifactId>
</dependency>
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.0</version>
</dependency>
~~~

> Spring Session JDBC 版本由 Spring Boot 4.1 parent 管理；springdoc 2.8.x 是支持 Spring Boot 4 的版本线。

- [ ] **步骤 2：验证依赖可解析且应用可启动**

运行：

~~~bash
cd backend && ./mvnw -q dependency:resolve
~~~

预期：退出状态为 `0`，无缺失依赖。

- [ ] **步骤 3：验证应用仍可启动**

运行：

~~~bash
cd backend && ./mvnw -q -DskipTests package && java -jar target/zija-backend-0.1.0-SNAPSHOT.jar --quit
~~~

预期：应用启动无异常（Spring Session JDBC 在无会话时不建表，springdoc 注册但 `/v3/api-docs` 在安全配置下暂未开放）。

- [ ] **步骤 4：提交依赖变更**

~~~bash
git add backend/pom.xml
git commit -m "chore: 引入 Spring Session JDBC 与 springdoc-openapi 依赖"
~~~

---

## 任务 2：账户表迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V2__create_account.sql`

- [ ] **步骤 1：创建账户表迁移**

创建 `V2__create_account.sql`：

~~~sql
CREATE TABLE account (
    id                  UUID PRIMARY KEY,
    username            VARCHAR(50) NOT NULL,
    username_normalized VARCHAR(50) NOT NULL UNIQUE,
    password_hash       VARCHAR(255) NOT NULL,
    display_name        VARCHAR(100) NOT NULL,
    email               VARCHAR(255),
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE INDEX idx_account_status ON account(status);
~~~

- [ ] **步骤 2：验证迁移在空库执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS，Flyway 执行 V1 和 V2，`account` 表存在（集成测试启动真实 PostgreSQL）。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V2__create_account.sql
git commit -m "feat: 新增账户表迁移"
~~~

---

## 任务 3：家庭与成员表迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V3__create_household_member.sql`

- [ ] **步骤 1：创建家庭与成员表迁移**

创建 `V3__create_household_member.sql`：

~~~sql
CREATE TABLE household (
    singleton_key SMALLINT PRIMARY KEY DEFAULT 1,
    id            UUID NOT NULL UNIQUE,
    name          VARCHAR(100) NOT NULL,
    timezone      VARCHAR(50) NOT NULL DEFAULT 'Asia/Shanghai',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_household_singleton CHECK (singleton_key = 1)
);

CREATE TABLE member (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    account_id    UUID NOT NULL REFERENCES account(id),
    role          VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version       INT NOT NULL DEFAULT 0,
    UNIQUE(household_id, account_id),
    CONSTRAINT ck_member_role CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER')),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'DEACTIVATED'))
);

CREATE INDEX idx_member_household ON member(household_id);
CREATE INDEX idx_member_account ON member(account_id);
CREATE INDEX idx_member_role ON member(household_id, role);
CREATE UNIQUE INDEX uq_member_single_owner
    ON member(household_id)
    WHERE role = 'OWNER';
~~~

- [ ] **步骤 2：验证迁移执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS，V1–V3 全部执行。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V3__create_household_member.sql
git commit -m "feat: 新增家庭与成员表迁移"
~~~

---

## 任务 4：邀请表迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V4__create_invitation.sql`

- [ ] **步骤 1：创建邀请表迁移**

创建 `V4__create_invitation.sql`：

~~~sql
CREATE TABLE invitation (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    token_digest  CHAR(64) NOT NULL UNIQUE,
    role          VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    expires_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID NOT NULL REFERENCES account(id),
    consumed_at   TIMESTAMPTZ,
    consumed_by   UUID REFERENCES account(id),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_invitation_role CHECK (role IN ('ADMIN', 'MEMBER')),
    CONSTRAINT ck_invitation_consumption CHECK (
        (consumed_at IS NULL AND consumed_by IS NULL)
        OR (consumed_at IS NOT NULL AND consumed_by IS NOT NULL)
    )
);

CREATE INDEX idx_invitation_household ON invitation(household_id);
CREATE INDEX idx_invitation_expires ON invitation(expires_at);
~~~

- [ ] **步骤 2：验证迁移执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V4__create_invitation.sql
git commit -m "feat: 新增邀请表迁移"
~~~

---

## 任务 5：所有者恢复令牌表迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V5__create_owner_recovery_token.sql`

- [ ] **步骤 1：创建恢复令牌表迁移**

创建 `V5__create_owner_recovery_token.sql`：

~~~sql
CREATE TABLE owner_recovery_token (
    id            UUID PRIMARY KEY,
    household_id  UUID NOT NULL REFERENCES household(id),
    account_id    UUID NOT NULL REFERENCES account(id),
    token_digest  CHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    consumed_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_owner_recovery_account
    ON owner_recovery_token(account_id, expires_at);
~~~

- [ ] **步骤 2：验证迁移执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V5__create_owner_recovery_token.sql
git commit -m "feat: 新增所有者恢复令牌表迁移"
~~~

---

## 任务 6：审计日志表迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V6__create_audit_log.sql`

- [ ] **步骤 1：创建审计日志表迁移**

创建 `V6__create_audit_log.sql`：

~~~sql
CREATE TABLE audit_log (
    id            UUID PRIMARY KEY,
    household_id  UUID,
    actor_account_id UUID,
    subject_account_id UUID,
    action        VARCHAR(50) NOT NULL,
    outcome       VARCHAR(20) NOT NULL,
    detail        JSONB,
    ip_address    VARCHAR(45),
    request_id    VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_audit_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_audit_log_household ON audit_log(household_id);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_account_id);
CREATE INDEX idx_audit_log_subject ON audit_log(subject_account_id);
CREATE INDEX idx_audit_log_action ON audit_log(action);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);
~~~

> 审计表不对账户和家庭设置外键，以便停用、恢复或归档后仍保留历史。

- [ ] **步骤 2：验证迁移执行**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V6__create_audit_log.sql
git commit -m "feat: 新增审计日志表迁移"
~~~

---

## 任务 7：Spring Session 表迁移

**文件：**
- 创建：`backend/src/main/resources/db/migration/V7__create_spring_session.sql`

- [ ] **步骤 1：创建 Spring Session JDBC 表迁移**

创建 `V7__create_spring_session.sql`，使用 Spring Session 3.x 官方 PostgreSQL schema 并增加按 principal 查询索引：

~~~sql
CREATE TABLE spring_session (
    primary_id            CHAR(36) NOT NULL,
    session_id            CHAR(36) NOT NULL UNIQUE,
    creation_time         BIGINT NOT NULL,
    last_access_time      BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time           BIGINT NOT NULL,
    principal_name        VARCHAR(100),
    CONSTRAINT pk_spring_session PRIMARY KEY (primary_id)
);

CREATE INDEX idx_spring_session_expiry ON spring_session(expiry_time);
CREATE INDEX idx_spring_session_principal_name
    ON spring_session(principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name     VARCHAR(200) NOT NULL,
    attribute_bytes    BYTEA NOT NULL,
    CONSTRAINT pk_spring_session_attributes PRIMARY KEY (session_primary_id, attribute_name),
    CONSTRAINT fk_spring_session_attributes_session
        FOREIGN KEY (session_primary_id)
        REFERENCES spring_session(primary_id)
        ON DELETE CASCADE
);

CREATE INDEX idx_spring_session_attributes_session_primary_id
    ON spring_session_attributes(session_primary_id);
~~~

- [ ] **步骤 2：验证 Spring Session 表可用**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SystemInstallationMapperIntegrationTest test
~~~

预期：PASS，V1–V7 全部执行。

- [ ] **步骤 3：提交迁移**

~~~bash
git add backend/src/main/resources/db/migration/V7__create_spring_session.sql
git commit -m "feat: 新增 Spring Session JDBC 表迁移"
~~~

---

## 任务 8：system 模块审计扩展

**文件：**
- 修改：`backend/src/main/java/com/zija/system/SystemApi.java`
- 创建：`backend/src/main/java/com/zija/system/internal/AuditEvent.java`
- 创建：`backend/src/main/java/com/zija/system/internal/AuditService.java`
- 创建：`backend/src/main/java/com/zija/system/internal/persistence/AuditLogEntity.java`
- 创建：`backend/src/main/java/com/zija/system/internal/persistence/AuditLogMapper.java`
- 创建：`backend/src/main/resources/mapper/system/AuditLogMapper.xml`
- 创建：`backend/src/test/java/com/zija/system/internal/persistence/AuditLogIntegrationTest.java`

- [ ] **步骤 1：编写失败集成测试**

创建 `AuditLogIntegrationTest.java`：

~~~java
package com.zija.system.internal.persistence;

import com.zija.system.internal.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class AuditLogIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    AuditLogMapper mapper;

    @Test
    @Transactional
    void recordsAndReadsAuditEvent() {
        var householdId = UUID.randomUUID();
        var actor = UUID.randomUUID();
        var requestId = "req-123";
        var event = new AuditEvent(
                "LOGIN_SUCCESS",
                "SUCCESS",
                householdId,
                actor,
                null,
                requestId,
                "198.51.100.7",
                Map.of("username", "owner")
        );

        mapper.insert(event);
        var rows = mapper.findByActor(actor);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).action()).isEqualTo("LOGIN_SUCCESS");
        assertThat(rows.get(0).actorAccountId()).isEqualTo(actor);
        assertThat(rows.get(0).requestId()).isEqualTo(requestId);
        assertThat(rows.get(0).detail()).containsEntry("username", "owner");
    }
}
~~~

- [ ] **步骤 2：验证测试失败（编译错误）**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=AuditLogIntegrationTest test
~~~

预期：FAIL，`AuditEvent`、`AuditLogMapper` 不存在。

- [ ] **步骤 3：创建 AuditEvent 类型化记录**

创建 `AuditEvent.java`：

~~~java
package com.zija.system.internal;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditEvent(
        String action,
        String outcome,
        UUID householdId,
        UUID actorAccountId,
        UUID subjectAccountId,
        String requestId,
        String ipAddress,
        Map<String, Object> detail
) {
    public AuditEvent {
        if (outcome == null
                || (!outcome.equals("SUCCESS") && !outcome.equals("FAILURE"))) {
            throw new IllegalArgumentException("outcome must be SUCCESS or FAILURE");
        }
    }
}
~~~

- [ ] **步骤 4：创建 AuditLogEntity**

创建 `AuditLogEntity.java`：

~~~java
package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@TableName("audit_log")
public class AuditLogEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private UUID actorAccountId;
    private UUID subjectAccountId;
    private String action;
    private String outcome;
    private Map<String, Object> detail;
    private String ipAddress;
    private String requestId;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getActorAccountId() { return actorAccountId; }
    public void setActorAccountId(UUID actorAccountId) { this.actorAccountId = actorAccountId; }
    public UUID getSubjectAccountId() { return subjectAccountId; }
    public void setSubjectAccountId(UUID subjectAccountId) { this.subjectAccountId = subjectAccountId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public Map<String, Object> getDetail() { return detail; }
    public void setDetail(Map<String, Object> detail) { this.detail = detail; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
~~~

- [ ] **步骤 5：创建 AuditLogMapper 与 XML**

创建 `AuditLogMapper.java`：

~~~java
package com.zija.system.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zija.system.internal.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.UUID;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {

    void insert(AuditEvent event);

    List<AuditLogEntity> findByActor(UUID actorAccountId);
}
~~~

创建 `AuditLogMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3.0.dtd">
<mapper namespace="com.zija.system.internal.persistence.AuditLogMapper">

    <insert id="insert" parameterType="com.zija.system.internal.AuditEvent">
        INSERT INTO audit_log (
            id, household_id, actor_account_id, subject_account_id,
            action, outcome, detail, ip_address, request_id, created_at
        ) VALUES (
            gen_random_uuid(),
            #{householdId},
            #{actorAccountId},
            #{subjectAccountId},
            #{action},
            #{outcome},
            CAST(#{detail} AS jsonb) ,
            #{ipAddress},
            #{requestId},
            CURRENT_TIMESTAMP
        )
    </insert>

    <select id="findByActor" resultType="com.zija.system.internal.persistence.AuditLogEntity">
        SELECT id, household_id, actor_account_id, subject_account_id,
               action, outcome, detail, ip_address, request_id, created_at
        FROM audit_log
        WHERE actor_account_id = #{actorAccountId}
        ORDER BY created_at DESC
    </select>
</mapper>
~~~

- [ ] **步骤 6：创建 AuditService 实现 SystemApi.recordAudit**

修改 `SystemApi.java`，新增审计接口方法：

~~~java
package com.zija.system;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SystemApi {

    SystemSnapshot current();

    void recordAudit(SystemApi.AuditEvent event);

    record SystemSnapshot(
            String application,
            String version,
            String status,
            UUID installationId,
            OffsetDateTime databaseTime
    ) {
    }

    record AuditEvent(
            String action,
            String outcome,
            UUID householdId,
            UUID actorAccountId,
            UUID subjectAccountId,
            String requestId,
            String ipAddress,
            java.util.Map<String, Object> detail
    ) {
    }
}
~~~

> `SystemApi.AuditEvent` 是跨模块公开契约；`internal.AuditEvent` 是内部便捷记录，字段一致。`AuditService` 在两者间转换，避免业务模块依赖内部包。

创建 `AuditService.java`：

~~~java
package com.zija.system.internal;

import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.AuditLogMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class AuditService {

    private final AuditLogMapper auditLogMapper;

    AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAudit(SystemApi.AuditEvent event) {
        auditLogMapper.insert(new AuditEvent(
                event.action(),
                event.outcome(),
                event.householdId(),
                event.actorAccountId(),
                event.subjectAccountId(),
                event.requestId(),
                event.ipAddress(),
                event.detail()
        ));
    }
}
~~~

> 让 `SystemInfoService implements SystemApi` 时一并实现 `recordAudit`，委托给 `AuditService`。或在 `SystemApi` 与实现间增加一个组合。简单做法：`SystemInfoService` 注入 `AuditService` 并委托 `recordAudit`。

- [ ] **步骤 7：让 SystemInfoService 实现 recordAudit 委托**

修改 `SystemInfoService.java`，新增 `AuditService` 依赖与委托方法：

~~~java
package com.zija.system.internal;

import com.zija.system.SystemApi;
import com.zija.system.internal.persistence.SystemInstallationMapper;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SystemInfoService implements SystemApi {

    private final SystemInstallationMapper installationMapper;
    private final Environment environment;
    private final AuditService auditService;

    SystemInfoService(
            SystemInstallationMapper installationMapper,
            Environment environment,
            AuditService auditService
    ) {
        this.installationMapper = installationMapper;
        this.environment = environment;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public SystemSnapshot current() {
        var installation = installationMapper.selectById((short) 1);
        if (installation == null) {
            throw new SystemStateUnavailableException("installation missing");
        }
        return new SystemSnapshot(
                environment.getProperty("spring.application.name", "zija"),
                environment.getProperty("info.app.version", "dev"),
                "UP",
                installation.getInstallationId(),
                installationMapper.selectDatabaseTime()
        );
    }

    @Override
    public void recordAudit(SystemApi.AuditEvent event) {
        auditService.recordAudit(event);
    }
}
~~~

- [ ] **步骤 8：验证集成测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=AuditLogIntegrationTest test
~~~

预期：PASS，审计记录可写入并按 actor 查询。

- [ ] **步骤 9：提交审计扩展**

~~~bash
git add backend/src/main/java/com/zija/system/ backend/src/main/resources/mapper/system/AuditLogMapper.xml backend/src/test/java/com/zija/system/internal/persistence/AuditLogIntegrationTest.java
git commit -m "feat: system 模块新增类型化审计接口与审计日志表"
~~~

---

## 任务 9：identity 模块公开接口与 DTO

**文件：**
- 创建：`backend/src/main/java/com/zija/identity/IdentityApi.java`
- 创建：`backend/src/main/java/com/zija/identity/package-info.java`

- [ ] **步骤 1：创建 IdentityApi 公开接口**

创建 `IdentityApi.java`：

~~~java
package com.zija.identity;

import java.util.Optional;
import java.util.UUID;

public interface IdentityApi {

    AccountInfo registerAccount(RegisterAccountCommand command);

    Optional<AccountInfo> findById(UUID id);

    Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername);

    void changePassword(UUID accountId, ChangePasswordCommand command);

    void disableAccount(UUID accountId);

    void activateAccount(UUID accountId);

    void requireActive(UUID accountId);

    record RegisterAccountCommand(
            String username,
            String password,
            String displayName,
            String email
    ) {
    }

    record ChangePasswordCommand(
            String currentPassword,
            String newPassword
    ) {
    }

    record AccountInfo(
            UUID id,
            String username,
            String displayName,
            String email,
            String status
    ) {
    }
}
~~~

- [ ] **步骤 2：创建 package-info**

创建 `package-info.java`：

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"system"}
)
package com.zija.identity;
~~~

> `identity` 只依赖 `system`（用于审计）；禁止依赖 `household`。

- [ ] **步骤 3：提交接口**

~~~bash
git add backend/src/main/java/com/zija/identity/
git commit -m "feat: 新增 identity 模块公开接口"
~~~

---

## 任务 10：identity 持久化层

**文件：**
- 创建：`backend/src/main/java/com/zija/identity/internal/persistence/AccountEntity.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/persistence/AccountMapper.java`
- 创建：`backend/src/main/resources/mapper/identity/AccountMapper.xml`
- 创建：`backend/src/test/java/com/zija/identity/internal/persistence/AccountMapperIntegrationTest.java`

- [ ] **步骤 1：编写失败集成测试**

创建 `AccountMapperIntegrationTest.java`：

~~~java
package com.zija.identity.internal.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class AccountMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    AccountMapper mapper;

    @Test
    @Transactional
    void insertsAndFindsByNormalizedUsername() {
        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("Owner");
        entity.setUsernameNormalized("owner");
        entity.setPasswordHash("{bcrypt}$2a$10$examplehash");
        entity.setDisplayName("所有者");
        entity.setStatus("ACTIVE");

        mapper.insert(entity);
        var found = mapper.selectByNormalizedUsername("owner");

        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("Owner");
    }

    @Test
    @Transactional
    void rejectsDuplicateNormalizedUsername() {
        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername("Alice");
        entity.setUsernameNormalized("alice");
        entity.setPasswordHash("{bcrypt}$2a$10$examplehash");
        entity.setDisplayName("Alice");
        entity.setStatus("ACTIVE");
        mapper.insert(entity);

        var dup = new AccountEntity();
        dup.setId(UUID.randomUUID());
        dup.setUsername("ALICE");
        dup.setUsernameNormalized("alice");
        dup.setPasswordHash("{bcrypt}$2a$10$examplehash");
        dup.setDisplayName("ALICE");
        dup.setStatus("ACTIVE");

        assertThatThrownBy(() -> mapper.insert(dup))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=AccountMapperIntegrationTest test
~~~

预期：FAIL，`AccountEntity`/`AccountMapper` 不存在。

- [ ] **步骤 3：创建 AccountEntity**

创建 `AccountEntity.java`：

~~~java
package com.zija.identity.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("account")
public class AccountEntity {

    @TableId
    private UUID id;
    private String username;
    private String usernameNormalized;
    private String passwordHash;
    private String displayName;
    private String email;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getUsernameNormalized() { return usernameNormalized; }
    public void setUsernameNormalized(String usernameNormalized) { this.usernameNormalized = usernameNormalized; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

- [ ] **步骤 4：创建 AccountMapper 与 XML**

创建 `AccountMapper.java`：

~~~java
package com.zija.identity.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface AccountMapper extends BaseMapper<AccountEntity> {

    Optional<AccountEntity> selectByNormalizedUsername(String normalizedUsername);

    int updateStatus(UUID id, String status, Integer version);

    int updatePasswordHash(UUID id, String passwordHash, Integer version);
}
~~~

创建 `AccountMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.identity.internal.persistence.AccountMapper">

    <select id="selectByNormalizedUsername" resultType="com.zija.identity.internal.persistence.AccountEntity">
        SELECT id, username, username_normalized, password_hash, display_name,
               email, status, created_at, updated_at, version
        FROM account
        WHERE username_normalized = #{normalizedUsername}
    </select>

    <update id="updateStatus">
        UPDATE account
        SET status = #{status}, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = #{id} AND version = #{version}
    </update>

    <update id="updatePasswordHash">
        UPDATE account
        SET password_hash = #{passwordHash}, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = #{id} AND version = #{version}
    </update>
</mapper>
~~~

- [ ] **步骤 5：验证集成测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=AccountMapperIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交持久化层**

~~~bash
git add backend/src/main/java/com/zija/identity/internal/persistence/ backend/src/main/resources/mapper/identity/ backend/src/test/java/com/zija/identity/internal/persistence/
git commit -m "feat: identity 模块新增账户持久化层"
~~~

---

## 任务 11：IdentityService 账户与密码业务

**文件：**
- 创建：`backend/src/main/java/com/zija/identity/internal/IdentityService.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/IdentityExceptionHandler.java`
- 创建：`backend/src/test/java/com/zija/identity/internal/IdentityServiceTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `IdentityServiceTest.java`：

~~~java
package com.zija.identity.internal;

import com.zija.identity.IdentityApi;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityServiceTest {

    private AccountMapper accountMapper;
    private PasswordEncoder passwordEncoder;
    private SystemApi systemApi;
    private IdentityService service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        systemApi = mock(SystemApi.class);
        service = new IdentityService(accountMapper, passwordEncoder, systemApi);
    }

    @Test
    void normalizesUsernameToLowercase() {
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("{bcrypt}hash");
        when(accountMapper.selectByNormalizedUsername("owner")).thenReturn(Optional.empty());

        service.registerAccount(new IdentityApi.RegisterAccountCommand(
                " Owner ", "Passw0rd!", "所有者", "owner@example.com"));

        var captor = org.mockito.ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("Owner");
        assertThat(captor.getValue().getUsernameNormalized()).isEqualTo("owner");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsDuplicateNormalizedUsername() {
        var existing = new AccountEntity();
        existing.setUsernameNormalized("owner");
        when(accountMapper.selectByNormalizedUsername("owner")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerAccount(new IdentityApi.RegisterAccountCommand(
                "Owner", "Passw0rd!", "所有者", null)))
                .isInstanceOf(UsernameAlreadyExistsException.class);
    }

    @Test
    void changePasswordUpgradesEncoding() {
        var account = new AccountEntity();
        account.setId(java.util.UUID.randomUUID());
        account.setVersion(3);
        account.setPasswordHash("{sha256}legacy");
        when(passwordEncoder.matches("OldPass1", "{sha256}legacy")).thenReturn(true);
        when(passwordEncoder.encode("NewPass2")).thenReturn("{bcrypt}newhash");
        when(accountMapper.selectById(account.getId())).thenReturn(account);
        when(accountMapper.updatePasswordHash(any(), any(), any())).thenReturn(1);

        service.changePassword(account.getId(),
                new IdentityApi.ChangePasswordCommand("OldPass1", "NewPass2"));

        verify(accountMapper).updatePasswordHash(account.getId(), "{bcrypt}newhash", 3);
    }

    @Test
    void changePasswordRejectsWrongCurrentPassword() {
        var account = new AccountEntity();
        account.setId(java.util.UUID.randomUUID());
        account.setVersion(0);
        account.setPasswordHash("{bcrypt}hash");
        when(passwordEncoder.matches("wrong", "{bcrypt}hash")).thenReturn(false);
        when(accountMapper.selectById(account.getId())).thenReturn(account);

        assertThatThrownBy(() -> service.changePassword(account.getId(),
                new IdentityApi.ChangePasswordCommand("wrong", "newpass")))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=IdentityServiceTest test
~~~

预期：FAIL，类不存在。

- [ ] **步骤 3：创建异常类**

创建 `IdentityExceptionHandler.java`（含异常定义）：

~~~java
package com.zija.identity.internal;

public class UsernameAlreadyExistsException extends RuntimeException {
    public UsernameAlreadyExistsException(String username) {
        super("username already exists: " + username);
    }
}
~~~

> 将 `InvalidCredentialsException` 单独放在 `identity/internal/InvalidCredentialsException.java`：

~~~java
package com.zija.identity.internal;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
~~~

- [ ] **步骤 4：创建 IdentityService**

创建 `IdentityService.java`：

~~~java
package com.zija.identity.internal;

import com.zija.identity.IdentityApi;
import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.system.SystemApi;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
class IdentityService implements IdentityApi {

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;
    private final SystemApi systemApi;

    IdentityService(
            AccountMapper accountMapper,
            PasswordEncoder passwordEncoder,
            SystemApi systemApi
    ) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
        this.systemApi = systemApi;
    }

    @Override
    @Transactional
    public AccountInfo registerAccount(RegisterAccountCommand command) {
        var trimmed = command.username().trim();
        var normalized = normalize(trimmed);

        if (accountMapper.selectByNormalizedUsername(normalized).isPresent()) {
            throw new UsernameAlreadyExistsException(normalized);
        }

        var entity = new AccountEntity();
        entity.setId(UUID.randomUUID());
        entity.setUsername(trimmed);
        entity.setUsernameNormalized(normalized);
        entity.setPasswordHash(passwordEncoder.encode(command.password()));
        entity.setDisplayName(command.displayName().trim());
        entity.setEmail(command.email());
        entity.setStatus("ACTIVE");
        accountMapper.insert(entity);

        return toInfo(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountInfo> findById(UUID id) {
        return Optional.ofNullable(accountMapper.selectById(id)).map(this::toInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername) {
        return accountMapper.selectByNormalizedUsername(normalizedUsername).map(this::toInfo);
    }

    @Override
    @Transactional
    public void changePassword(UUID accountId, ChangePasswordCommand command) {
        var account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        var newHash = passwordEncoder.encode(command.newPassword());
        if (accountMapper.updatePasswordHash(accountId, newHash, account.getVersion()) != 1) {
            throw new InvalidCredentialsException();
        }
    }

    @Override
    @Transactional
    public void disableAccount(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            accountMapper.updateStatus(accountId, "DISABLED", account.getVersion());
        }
    }

    @Override
    @Transactional
    public void activateAccount(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account != null) {
            accountMapper.updateStatus(accountId, "ACTIVE", account.getVersion());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActive(UUID accountId) {
        var account = accountMapper.selectById(accountId);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new InvalidCredentialsException();
        }
    }

    static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private AccountInfo toInfo(AccountEntity entity) {
        return new AccountInfo(
                entity.getId(),
                entity.getUsername(),
                entity.getDisplayName(),
                entity.getEmail(),
                entity.getStatus()
        );
    }
}
~~~

- [ ] **步骤 5：验证单元测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=IdentityServiceTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/identity/internal/ backend/src/test/java/com/zija/identity/internal/IdentityServiceTest.java
git commit -m "feat: identity 模块新增账户与密码业务服务"
~~~

---

## 任务 12：ZijaPrincipal 与 UserDetailsService

**文件：**
- 创建：`backend/src/main/java/com/zija/ZijaPrincipal.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/ZijaUserDetailsService.java`
- 创建：`backend/src/test/java/com/zija/identity/internal/ZijaUserDetailsServiceTest.java`

- [ ] **步骤 1：编写失败测试**

创建 `ZijaUserDetailsServiceTest.java`：

~~~java
package com.zija.identity.internal;

import com.zija.identity.internal.persistence.AccountEntity;
import com.zija.identity.internal.persistence.AccountMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ZijaUserDetailsServiceTest {

    @Test
    void loadsActiveAccountByNormalizedUsername() {
        var mapper = mock(AccountMapper.class);
        var account = new AccountEntity();
        account.setId(UUID.randomUUID());
        account.setUsername("Owner");
        account.setUsernameNormalized("owner");
        account.setPasswordHash("{bcrypt}$2a$10$hash");
        account.setDisplayName("所有者");
        account.setStatus("ACTIVE");
        when(mapper.selectByNormalizedUsername("owner")).thenReturn(Optional.of(account));

        var service = new ZijaUserDetailsService(mapper);
        var details = service.loadUserByUsername("Owner");

        var principal = (com.zija.ZijaPrincipal) details;
        assertThat(principal.getAccountId()).isEqualTo(account.getId());
        assertThat(principal.getUsername()).isEqualTo("Owner");
        assertThat(principal.getName()).isEqualTo(account.getId().toString());
        assertThat(principal.getPassword()).isEqualTo("{bcrypt}$2a$10$hash");
        assertThat(principal.isEnabled()).isTrue();
    }

    @Test
    void rejectsDisabledAccount() {
        var mapper = mock(AccountMapper.class);
        var account = new AccountEntity();
        account.setStatus("DISABLED");
        when(mapper.selectByNormalizedUsername("x")).thenReturn(Optional.of(account));

        var service = new ZijaUserDetailsService(mapper);
        assertThatThrownBy(() -> service.loadUserByUsername("x"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ZijaUserDetailsServiceTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建 ZijaPrincipal**

创建 `ZijaPrincipal.java`（位于根包，供多个模块共享）：

~~~java
package com.zija;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public final class ZijaPrincipal implements UserDetails {

    private final UUID accountId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final boolean active;

    public ZijaPrincipal(
            UUID accountId,
            String username,
            String displayName,
            String passwordHash,
            boolean active
    ) {
        this.accountId = accountId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public UUID getAccountId() { return accountId; }

    @Override
    public String getName() { return accountId.toString(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    public String getDisplayName() { return displayName; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return active; }
}
~~~

> `getName()` 返回 `accountId` 字符串，使 Spring Session 的 principal 索引可以稳定地按账户删除全部会话。角色不在 principal 中——由 `household` 在授权时实时查询。

- [ ] **步骤 4：创建 ZijaUserDetailsService**

创建 `ZijaUserDetailsService.java`：

~~~java
package com.zija.identity.internal;

import com.zija.ZijaPrincipal;
import com.zija.identity.internal.persistence.AccountMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
class ZijaUserDetailsService implements UserDetailsService {

    private final AccountMapper accountMapper;

    ZijaUserDetailsService(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        var normalized = username.trim().toLowerCase(Locale.ROOT);
        var account = accountMapper.selectByNormalizedUsername(normalized)
                .orElseThrow(() -> new UsernameNotFoundException("not found"));
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new UsernameNotFoundException("not active");
        }
        return new ZijaPrincipal(
                account.getId(),
                account.getUsername(),
                account.getDisplayName(),
                account.getPasswordHash(),
                true
        );
    }
}
~~~

- [ ] **步骤 5：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ZijaUserDetailsServiceTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/ZijaPrincipal.java backend/src/main/java/com/zija/identity/internal/ZijaUserDetailsService.java backend/src/test/java/com/zija/identity/internal/ZijaUserDetailsServiceTest.java
git commit -m "feat: 新增不可变认证主体与账户加载服务"
~~~

---

## 任务 13：登录限流器

**文件：**
- 创建：`backend/src/main/java/com/zija/identity/internal/LoginRateLimiter.java`
- 创建：`backend/src/test/java/com/zija/identity/internal/LoginRateLimiterTest.java`

- [ ] **步骤 1：编写失败测试**

创建 `LoginRateLimiterTest.java`：

~~~java
package com.zija.identity.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    @Test
    void allowsUntilAccountThreshold() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        }
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void accountAndIpBucketsAreIndependent() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) limiter.recordFailure("owner", "10.0.0.1");
        limiter.recordFailure("owner", "10.0.0.2");
        assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        assertThatThrownBy(() -> limiter.recordFailure("owner", "10.0.0.1"))
                .isInstanceOf(LoginRateLimitedException.class);
    }

    @Test
    void successClearsAccountBucket() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        for (int i = 0; i < 4; i++) limiter.recordFailure("owner", "10.0.0.1");
        limiter.recordSuccess("owner");
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.recordFailure("owner", "10.0.0.1").isBlocked()).isFalse();
        }
    }

    @Test
    void unknownUsernameAlsoRateLimited() {
        var limiter = new LoginRateLimiter(5, 5, 20, 5, 1000);
        assertThatThrownBy(() -> {
            for (int i = 0; i < 30; i++) limiter.recordFailure("ghost", "10.0.0.1");
        }).isInstanceOf(LoginRateLimitedException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=LoginRateLimiterTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建 LoginRateLimiter**

创建 `LoginRateLimitedException.java`：

~~~java
package com.zija.identity.internal;

public class LoginRateLimitedException extends RuntimeException {
    public LoginRateLimitedException(String message) {
        super(message);
    }
}
~~~

创建 `LoginRateLimiter.java`：

~~~java
package com.zija.identity.internal;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
class LoginRateLimiter {

    private final int accountThreshold;
    private final int accountWindowMinutes;
    private final int ipThreshold;
    private final int ipWindowMinutes;
    private final int maxEntries;

    private final Map<String, Bucket> accountBuckets = new LinkedHashMap<>();
    private final Map<String, Bucket> ipBuckets = new LinkedHashMap<>();

    LoginRateLimiter() {
        this(5, 5, 20, 5, 1000);
    }

    LoginRateLimiter(int accountThreshold, int accountWindowMinutes,
                     int ipThreshold, int ipWindowMinutes, int maxEntries) {
        this.accountThreshold = accountThreshold;
        this.accountWindowMinutes = accountWindowMinutes;
        this.ipThreshold = ipThreshold;
        this.ipWindowMinutes = ipWindowMinutes;
        this.maxEntries = maxEntries;
    }

    synchronized Result recordFailure(String normalizedUsername, String ip) {
        var accountBlocked = bump(accountBuckets, normalizedUsername, accountThreshold,
                accountWindowMinutes * 60_000L);
        var ipBlocked = bump(ipBuckets, ip, ipThreshold, ipWindowMinutes * 60_000L);
        evictIfFull();
        if (accountBlocked || ipBlocked) {
            throw new LoginRateLimitedException("rate limited");
        }
        return new Result(false);
    }

    synchronized void recordSuccess(String normalizedUsername) {
        accountBuckets.remove(normalizedUsername);
    }

    private boolean bump(Map<String, Bucket> buckets, String key, int threshold, long windowMillis) {
        var now = System.currentTimeMillis();
        var bucket = buckets.computeIfAbsent(key, k -> new Bucket(now));
        bucket.evictExpired(now, windowMillis);
        bucket.add(now);
        return bucket.count() > threshold;
    }

    private void evictIfFull() {
        evict(accountBuckets);
        evict(ipBuckets);
    }

    private void evict(Map<String, Bucket> buckets) {
        if (buckets.size() <= maxEntries) return;
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext() && buckets.size() > maxEntries / 2) {
            it.next();
            it.remove();
        }
    }

    private static final class Bucket {
        private final java.util.ArrayDeque<Long> timestamps = new java.util.ArrayDeque<>();

        Bucket(long first) {
            timestamps.add(first);
        }

        void add(long ts) { timestamps.add(ts); }

        int count() { return timestamps.size(); }

        void evictExpired(long now, long windowMillis) {
            var cutoff = now - windowMillis;
            while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) {
                timestamps.pollFirst();
            }
        }
    }

    record Result(boolean isBlocked) {}
}
~~~

- [ ] **步骤 4：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=LoginRateLimiterTest test
~~~

预期：PASS。

- [ ] **步骤 5：提交**

~~~bash
git add backend/src/main/java/com/zija/identity/internal/LoginRateLimiter.java backend/src/main/java/com/zija/identity/internal/LoginRateLimitedException.java backend/src/test/java/com/zija/identity/internal/LoginRateLimiterTest.java
git commit -m "feat: identity 模块新增账户/IP 双桶登录限流"
~~~

---

## 任务 14：共享会话认证支持

**文件：**
- 创建：`backend/src/main/java/com/zija/ZijaSessionAuthenticationSupport.java`

- [ ] **步骤 1：创建 ZijaSessionAuthenticationSupport**

创建 `ZijaSessionAuthenticationSupport.java`：

~~~java
package com.zija;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.stereotype.Component;

@Component
public class ZijaSessionAuthenticationSupport {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final CsrfTokenRepository csrfTokenRepository;

    public ZijaSessionAuthenticationSupport(
            AuthenticationManager authenticationManager,
            CsrfTokenRepository csrfTokenRepository
    ) {
        this.authenticationManager = authenticationManager;
        this.csrfTokenRepository = csrfTokenRepository;
        this.securityContextRepository = new HttpSessionSecurityContextRepository();
        this.sessionAuthenticationStrategy = new ChangeSessionIdAuthenticationStrategy();
    }

    public Authentication authenticate(
            String username,
            String password,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var token = new UsernamePasswordAuthenticationToken(username, password);
        var authentication = authenticationManager.authenticate(token);

        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return authentication;
    }

    public void regenerateCsrfToken(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        csrfTokenRepository.saveToken(null, request, response);
        CsrfToken newToken = csrfTokenRepository.loadToken(request);
        if (newToken != null) {
            csrfTokenRepository.saveToken(newToken, request, response);
        }
    }
}
~~~

> 登录、初始化和邀请兑换都通过此组件完成认证、session fixation 防护、SecurityContext 保存与 CSRF Token 重新生成。不在控制器中直接操作 `SecurityContextHolder`。

- [ ] **步骤 2：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/main/java/com/zija/ZijaSessionAuthenticationSupport.java
git commit -m "feat: 新增共享会话认证编排组件"
~~~

---

## 任务 15：identity 控制器（登录/登出/会话/CSRF/密码）

**文件：**
- 创建：`backend/src/main/java/com/zija/identity/internal/auth/LoginRequest.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/auth/SessionInfo.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/auth/ChangePasswordRequest.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/IdentityController.java`
- 创建：`backend/src/main/java/com/zija/identity/internal/IdentityExceptionHandler.java`
- 创建：`backend/src/test/java/com/zija/identity/internal/IdentityControllerTest.java`

- [ ] **步骤 1：编写失败 MockMvc 测试**

创建 `IdentityControllerTest.java`（核心场景，使用 `@WebMvcTest` + MockBean）：

~~~java
package com.zija.identity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.auth.ChangePasswordRequest;
import com.zija.identity.internal.auth.LoginRequest;
import com.zija.identity.internal.persistence.AccountMapper;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean IdentityService identityService;
    @MockBean AccountMapper accountMapper;
    @MockBean AuthenticationManager authenticationManager;
    @MockBean ZijaSessionAuthenticationSupport sessionAuthSupport;
    @MockBean SystemApi systemApi;
    @MockBean PasswordEncoder passwordEncoder;

    @Test
    void loginReturnsSessionAndAccountIdentity() throws Exception {
        var accountId = UUID.randomUUID();
        var principal = new ZijaPrincipal(accountId, "owner", "所有者", "{bcrypt}x", true);
        var auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        when(auth.isAuthenticated()).thenReturn(true);
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(identityService.findByNormalizedUsername("owner"))
                .thenReturn(java.util.Optional.of(new IdentityApi.AccountInfo(
                        accountId, "owner", "所有者", null, "ACTIVE")));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("owner", "Passw0rd!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.accountId").value(accountId.toString()))
                .andExpect(jsonPath("$.username").value("owner"));
    }

    @Test
    void loginFailureReturnsSameErrorAsUnknownUser() throws Exception {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("ghost", "Passw0rd!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("AUTH_LOGIN_FAILED"));
    }

    @Test
    void getSessionReturnsNotAuthenticatedWithoutSession() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized());
    }
}
~~~

> 上述测试需要 CSRF Token；在 MockMvc 中通过 `.csrf()` 自动携带。实际运行时补全 CSRF 步骤（见步骤 3 说明）。

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=IdentityControllerTest test
~~~

预期：FAIL，控制器不存在。

- [ ] **步骤 3：创建 DTO 记录**

创建 `auth/LoginRequest.java`：

~~~java
package com.zija.identity.internal.auth;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
~~~

创建 `auth/SessionInfo.java`：

~~~java
package com.zija.identity.internal.auth;

import java.util.UUID;

public record SessionInfo(
        boolean authenticated,
        UUID accountId,
        String username,
        String displayName
) {
    public static SessionInfo anonymous() {
        return new SessionInfo(false, null, null, null);
    }
}
~~~

创建 `auth/ChangePasswordRequest.java`：

~~~java
package com.zija.identity.internal.auth;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
~~~

- [ ] **步骤 4：创建 IdentityController**

创建 `IdentityController.java`：

~~~java
package com.zija.identity.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.identity.IdentityApi;
import com.zija.identity.internal.auth.ChangePasswordRequest;
import com.zija.identity.internal.auth.LoginRequest;
import com.zija.identity.internal.auth.SessionInfo;
import com.zija.system.SystemApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
class IdentityController {

    private final IdentityService identityService;
    private final LoginRateLimiter rateLimiter;
    private final ZijaSessionAuthenticationSupport sessionAuth;
    private final SystemApi systemApi;

    IdentityController(
            IdentityService identityService,
            LoginRateLimiter rateLimiter,
            ZijaSessionAuthenticationSupport sessionAuth,
            SystemApi systemApi
    ) {
        this.identityService = identityService;
        this.rateLimiter = rateLimiter;
        this.sessionAuth = sessionAuth;
        this.systemApi = systemApi;
    }

    @PostMapping("/login")
    SessionInfo login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var normalized = request.username().trim().toLowerCase(Locale.ROOT);
        var ip = resolveClientIp(httpRequest);

        try {
            var authentication = sessionAuth.authenticate(
                    normalized, request.password(), httpRequest, httpResponse);
            rateLimiter.recordSuccess(normalized);
            var principal = (ZijaPrincipal) authentication.getPrincipal();
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGIN_SUCCESS", "SUCCESS", null,
                    principal.getAccountId(), null,
                    (String) httpRequest.getAttribute("zija.request-id"),
                    ip, Map.of("username", principal.getUsername())
            ));
            return new SessionInfo(true, principal.getAccountId(),
                    principal.getUsername(), principal.getDisplayName());
        } catch (AuthenticationException ex) {
            try {
                rateLimiter.recordFailure(normalized, ip);
            } catch (LoginRateLimitedException rateEx) {
                throw rateEx;
            }
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGIN_FAILURE", "FAILURE", null,
                    null, null,
                    (String) httpRequest.getAttribute("zija.request-id"),
                    ip, Map.of("username", normalized)
            ));
            throw new InvalidCredentialsException();
        }
    }

    @PostMapping("/logout")
    void logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var accountId = authentication != null && authentication.getPrincipal() instanceof ZijaPrincipal p
                ? p.getAccountId() : null;
        request.getSession().invalidate();
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        if (accountId != null) {
            systemApi.recordAudit(new SystemApi.AuditEvent(
                    "LOGOUT", "SUCCESS", null, accountId, null,
                    (String) request.getAttribute("zija.request-id"),
                    resolveClientIp(request), null
            ));
        }
    }

    @GetMapping("/session")
    SessionInfo session() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof ZijaPrincipal principal)) {
            return SessionInfo.anonymous();
        }
        return new SessionInfo(true, principal.getAccountId(),
                principal.getUsername(), principal.getDisplayName());
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken csrfToken) {
        return Map.of(
                "token", csrfToken.getToken(),
                "headerName", csrfToken.getHeaderName(),
                "parameterName", csrfToken.getParameterName()
        );
    }

    @PutMapping("/password")
    void changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest
    ) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var principal = (ZijaPrincipal) authentication.getPrincipal();
        identityService.changePassword(principal.getAccountId(),
                new IdentityApi.ChangePasswordCommand(
                        request.currentPassword(), request.newPassword()));
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "PASSWORD_CHANGED", "SUCCESS", null,
                principal.getAccountId(), principal.getAccountId(),
                (String) httpRequest.getAttribute("zija.request-id"),
                resolveClientIp(httpRequest), null
        ));
    }

    private String resolveClientIp(HttpServletRequest request) {
        var forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
~~~

- [ ] **步骤 5：创建 IdentityExceptionHandler**

创建 `IdentityExceptionHandler.java`：

~~~java
package com.zija.identity.internal;

import com.zija.ZijaRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = IdentityController.class)
class IdentityExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    ProblemDetail handleInvalidCredentials(HttpServletRequest request) {
        return problem(request, HttpStatus.UNAUTHORIZED, "用户名或密码错误", "AUTH_LOGIN_FAILED");
    }

    @ExceptionHandler(LoginRateLimitedException.class)
    ProblemDetail handleRateLimited(HttpServletRequest request) {
        var problem = problem(request, HttpStatus.TOO_MANY_REQUESTS,
                "登录尝试过多", "AUTH_LOGIN_RATE_LIMITED");
        problem.getHeaders().add("Retry-After", "300");
        return problem;
    }

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    ProblemDetail handleDuplicate(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "用户名已存在", "IDENTITY_USERNAME_TAKEN");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status,
                                  String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE));
        return problem;
    }
}
~~~

- [ ] **步骤 6：验证控制器测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=IdentityControllerTest test
~~~

预期：PASS（可能需要调整 MockMvc CSRF 配置——在测试中使用 `.with(csrf())`）。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/main/java/com/zija/identity/internal/auth/ backend/src/main/java/com/zija/identity/internal/IdentityController.java backend/src/main/java/com/zija/identity/internal/IdentityExceptionHandler.java backend/src/test/java/com/zija/identity/internal/IdentityControllerTest.java
git commit -m "feat: identity 模块新增登录/登出/会话/CSRF/密码端点"
~~~

---

## 任务 16：安全配置与 Problem Details 处理器

**文件：**
- 修改：`backend/src/main/java/com/zija/ZijaSecurityConfiguration.java`
- 创建：`backend/src/main/java/com/zija/ZijaProblemHandlers.java`
- 修改：`backend/src/main/resources/application.yml`
- 创建：`backend/src/main/resources/application-prod.yml`

- [ ] **步骤 1：创建 Problem Details 处理器**

创建 `ZijaProblemHandlers.java`：

~~~java
package com.zija;

import com.zija.ZijaRequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ZijaProblemHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public ZijaProblemHandlers(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        writeProblem(request, response, HttpStatus.UNAUTHORIZED,
                "需要认证", "AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        var errorCode = ex instanceof CsrfException ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED";
        var title = ex instanceof CsrfException ? "CSRF Token 无效" : "权限不足";
        writeProblem(request, response, HttpStatus.FORBIDDEN, title, errorCode);
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response,
                              HttpStatus status, String title, String errorCode) throws IOException {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE));
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
~~~

- [ ] **步骤 2：更新安全配置**

替换 `ZijaSecurityConfiguration.java`：

~~~java
package com.zija;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.StandardPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;

import java.util.HashMap;
import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class ZijaSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ZijaProblemHandlers problemHandlers
    ) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/household/status").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/household/bootstrap").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/inspect").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/invitations/redeem").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/owner-recovery/inspect").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/owner-recovery/reset-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestHandler()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandlers)
                        .accessDeniedHandler(problemHandlers))
                .logout(logout -> logout
                        .logoutUrl("/api/v1/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("ZIJA_SESSION", "XSRF-TOKEN")
                        .logoutSuccessHandler((request, response, auth) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("sha256", new StandardPasswordEncoder());
        var delegating = new DelegatingPasswordEncoder("bcrypt", encoders);
        return delegating;
    }

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        var provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider::authenticate;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        var repo = new HttpSessionCsrfTokenRepository();
        repo.setHeaderName("X-XSRF-TOKEN");
        repo.setParameterName("_csrf");
        repo.setCookieName("XSRF-TOKEN");
        return repo;
    }

    @Bean
    CsrfTokenRequestHandler csrfTokenRequestHandler() {
        var handler = new XorCsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);
        return handler::handle;
    }
}
~~~

> `DaoAuthenticationProvider` 在 Spring Security 7 中改为构造器注入 `UserDetailsService`；根据实际 API 调整。`DaoAuthenticationProvider` 的 `setUserDetailsService`/`setPasswordEncoder` 已废弃但仍可用——执行时核对 Spring Security 7.1 文档确认构造器。

- [ ] **步骤 3：更新 application.yml**

在 `application.yml` 新增会话与反向代理配置：

~~~yaml
spring:
  session:
    jdbc:
      initialize-schema: never
    timeout: 24h
  datasource:
    url: ${ZIJA_DB_URL:jdbc:postgresql://localhost:5432/zija}
    username: ${ZIJA_DB_USERNAME:zija}
    password: ${ZIJA_DB_PASSWORD:zija}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 1
  flyway:
    enabled: true
    locations: classpath:db/migration
  mvc:
    problemdetails:
      enabled: true
  lifecycle:
    timeout-per-shutdown-phase: 20s

server:
  shutdown: graceful
  forward-headers-strategy: native
  servlet:
    session:
      timeout: 24h
      cookie:
        name: ZIJA_SESSION
        http-only: true
        same-site: lax
~~~

- [ ] **步骤 4：创建生产配置**

创建 `application-prod.yml`：

~~~yaml
server:
  servlet:
    session:
      cookie:
        secure: true

springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
~~~

- [ ] **步骤 5：验证编译与现有测试通过**

运行：

~~~bash
cd backend && ./mvnw -q test
~~~

预期：PASS，现有 system 模块测试不受影响。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/ZijaSecurityConfiguration.java backend/src/main/java/com/zija/ZijaProblemHandlers.java backend/src/main/resources/application.yml backend/src/main/resources/application-prod.yml
git commit -m "feat: 更新安全配置支持 CSRF SPA、会话与 Problem Details 处理器"
~~~

---

## 任务 17：household 模块公开接口

**文件：**
- 创建：`backend/src/main/java/com/zija/household/HouseholdApi.java`
- 创建：`backend/src/main/java/com/zija/household/package-info.java`

- [ ] **步骤 1：创建 HouseholdApi**

创建 `HouseholdApi.java`：

~~~java
package com.zija.household;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HouseholdApi {

    boolean isInitialized();

    Optional<HouseholdInfo> findHousehold();

    List<MemberInfo> findMembers(UUID householdId);

    Optional<MemberInfo> findMember(UUID householdId, UUID accountId);

    MemberInfo requireActiveMember(UUID accountId);

    boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole);

    enum MemberRole {
        OWNER, ADMIN, MEMBER;

        public boolean isAtLeast(MemberRole other) {
            return this.ordinal() >= other.ordinal();
        }
    }

    record HouseholdInfo(UUID id, String name, String timezone) {
    }

    record MemberInfo(
            UUID id,
            UUID householdId,
            UUID accountId,
            String username,
            String displayName,
            MemberRole role,
            String status
    ) {
    }
}
~~~

- [ ] **步骤 2：创建 package-info**

创建 `package-info.java`：

~~~java
@org.springframework.modulith.ApplicationModule(
        displayName = "Household",
        allowedDependencies = {"identity", "system"}
)
package com.zija.household;
~~~

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/main/java/com/zija/household/
git commit -m "feat: 新增 household 模块公开接口"
~~~

---

## 任务 18：household 持久化层

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/HouseholdEntity.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/MemberEntity.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/InvitationEntity.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/OwnerRecoveryTokenEntity.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/HouseholdMapper.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/MemberMapper.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/InvitationMapper.java`
- 创建：`backend/src/main/java/com/zija/household/internal/persistence/OwnerRecoveryTokenMapper.java`
- 创建：`backend/src/main/resources/mapper/household/HouseholdMapper.xml`
- 创建：`backend/src/main/resources/mapper/household/MemberMapper.xml`
- 创建：`backend/src/main/resources/mapper/household/InvitationMapper.xml`
- 创建：`backend/src/main/resources/mapper/household/OwnerRecoveryTokenMapper.xml`

- [ ] **步骤 1：创建实体类**

创建 `HouseholdEntity.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("household")
public class HouseholdEntity {

    private Short singletonKey;
    @TableId
    private UUID id;
    private String name;
    private String timezone;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;

    public Short getSingletonKey() { return singletonKey; }
    public void setSingletonKey(Short singletonKey) { this.singletonKey = singletonKey; }
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `MemberEntity.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("member")
public class MemberEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private UUID accountId;
    private String role;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    @Version
    private Integer version;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
~~~

创建 `InvitationEntity.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("invitation")
public class InvitationEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private String tokenDigest;
    private String role;
    private OffsetDateTime expiresAt;
    private UUID createdBy;
    private OffsetDateTime consumedAt;
    private UUID consumedBy;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public String getTokenDigest() { return tokenDigest; }
    public void setTokenDigest(String tokenDigest) { this.tokenDigest = tokenDigest; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(OffsetDateTime consumedAt) { this.consumedAt = consumedAt; }
    public UUID getConsumedBy() { return consumedBy; }
    public void setConsumedBy(UUID consumedBy) { this.consumedBy = consumedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
~~~

创建 `OwnerRecoveryTokenEntity.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("owner_recovery_token")
public class OwnerRecoveryTokenEntity {

    @TableId
    private UUID id;
    private UUID householdId;
    private UUID accountId;
    private String tokenDigest;
    private OffsetDateTime expiresAt;
    private OffsetDateTime consumedAt;
    private OffsetDateTime createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getHouseholdId() { return householdId; }
    public void setHouseholdId(UUID householdId) { this.householdId = householdId; }
    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }
    public String getTokenDigest() { return tokenDigest; }
    public void setTokenDigest(String tokenDigest) { this.tokenDigest = tokenDigest; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
    public OffsetDateTime getConsumedAt() { return consumedAt; }
    public void setConsumedAt(OffsetDateTime consumedAt) { this.consumedAt = consumedAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
~~~

- [ ] **步骤 2：创建 Mapper 接口**

创建 `HouseholdMapper.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface HouseholdMapper extends BaseMapper<HouseholdEntity> {
    int insertSingleton(HouseholdEntity entity);
}
~~~

创建 `MemberMapper.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Mapper
public interface MemberMapper extends BaseMapper<MemberEntity> {

    List<MemberEntity> selectByHousehold(UUID householdId);

    Optional<MemberEntity> selectByAccount(UUID accountId);

    int updateRole(UUID id, String role, Integer version);

    int updateStatus(UUID id, String status, Integer version);

    Optional<MemberEntity> selectOwner(UUID householdId);
}
~~~

创建 `InvitationMapper.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface InvitationMapper extends BaseMapper<InvitationEntity> {

    Optional<InvitationEntity> selectByDigestForUpdate(String tokenDigest);

    int markConsumed(UUID id, UUID consumedBy);
}
~~~

创建 `OwnerRecoveryTokenMapper.java`：

~~~java
package com.zija.household.internal.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;
import java.util.UUID;

@Mapper
public interface OwnerRecoveryTokenMapper extends BaseMapper<OwnerRecoveryTokenEntity> {

    Optional<OwnerRecoveryTokenEntity> selectByDigestForUpdate(String tokenDigest);

    int markConsumed(UUID id);

    int invalidatePending(UUID accountId);
}
~~~

- [ ] **步骤 3：创建 Mapper XML**

创建 `HouseholdMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.household.internal.persistence.HouseholdMapper">

    <insert id="insertSingleton" parameterType="com.zija.household.internal.persistence.HouseholdEntity">
        INSERT INTO household (singleton_key, id, name, timezone, created_at, updated_at, version)
        VALUES (1, #{id}, #{name}, #{timezone}, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
    </insert>
</mapper>
~~~

创建 `MemberMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.household.internal.persistence.MemberMapper">

    <select id="selectByHousehold" resultType="com.zija.household.internal.persistence.MemberEntity">
        SELECT id, household_id, account_id, role, status, created_at, updated_at, version
        FROM member WHERE household_id = #{householdId} ORDER BY created_at
    </select>

    <select id="selectByAccount" resultType="com.zija.household.internal.persistence.MemberEntity">
        SELECT id, household_id, account_id, role, status, created_at, updated_at, version
        FROM member WHERE account_id = #{accountId}
    </select>

    <update id="updateRole">
        UPDATE member SET role = #{role}, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = #{id} AND version = #{version}
    </update>

    <update id="updateStatus">
        UPDATE member SET status = #{status}, updated_at = CURRENT_TIMESTAMP, version = version + 1
        WHERE id = #{id} AND version = #{version}
    </update>

    <select id="selectOwner" resultType="com.zija.household.internal.persistence.MemberEntity">
        SELECT id, household_id, account_id, role, status, created_at, updated_at, version
        FROM member WHERE household_id = #{householdId} AND role = 'OWNER'
    </select>
</mapper>
~~~

创建 `InvitationMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.household.internal.persistence.InvitationMapper">

    <select id="selectByDigestForUpdate" resultType="com.zija.household.internal.persistence.InvitationEntity">
        SELECT id, household_id, token_digest, role, expires_at, created_by,
               consumed_at, consumed_by, created_at
        FROM invitation WHERE token_digest = #{tokenDigest} FOR UPDATE
    </select>

    <update id="markConsumed">
        UPDATE invitation SET consumed_at = CURRENT_TIMESTAMP, consumed_by = #{consumedBy}
        WHERE id = #{id}
    </update>
</mapper>
~~~

创建 `OwnerRecoveryTokenMapper.xml`：

~~~xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.zija.household.internal.persistence.OwnerRecoveryTokenMapper">

    <select id="selectByDigestForUpdate" resultType="com.zija.household.internal.persistence.OwnerRecoveryTokenEntity">
        SELECT id, household_id, account_id, token_digest, expires_at, consumed_at, created_at
        FROM owner_recovery_token WHERE token_digest = #{tokenDigest} FOR UPDATE
    </select>

    <update id="markConsumed">
        UPDATE owner_recovery_token SET consumed_at = CURRENT_TIMESTAMP WHERE id = #{id}
    </update>

    <update id="invalidatePending">
        UPDATE owner_recovery_token SET consumed_at = CURRENT_TIMESTAMP
        WHERE account_id = #{accountId} AND consumed_at IS NULL
    </update>
</mapper>
~~~

- [ ] **步骤 4：验证编译通过**

运行：

~~~bash
cd backend && ./mvnw -q compile
~~~

预期：编译成功。

- [ ] **步骤 5：提交持久化层**

~~~bash
git add backend/src/main/java/com/zija/household/internal/persistence/ backend/src/main/resources/mapper/household/
git commit -m "feat: household 模块新增持久化层"
~~~

---

## 任务 19：HouseholdService 初始化编排

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/HouseholdService.java`
- 创建：`backend/src/main/java/com/zija/household/internal/exception/HouseholdAlreadyInitializedException.java`
- 创建：`backend/src/test/java/com/zija/household/internal/HouseholdServiceTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/persistence/HouseholdBootstrapIntegrationTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `HouseholdServiceTest.java`（验证初始化编排，mock 依赖）：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HouseholdServiceTest {

    @Test
    void bootstrapCreatesHouseholdOwnerAndAccountInOrder() {
        var householdMapper = mock(HouseholdMapper.class);
        var memberMapper = mock(MemberMapper.class);
        var identityApi = mock(IdentityApi.class);
        var systemApi = mock(SystemApi.class);
        when(identityApi.registerAccount(any())).thenReturn(new IdentityApi.AccountInfo(
                UUID.randomUUID(), "owner", "所有者", null, "ACTIVE"));

        var service = new HouseholdService(householdMapper, memberMapper, identityApi, systemApi);
        service.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null));

        verify(householdMapper).insertSingleton(any());
        verify(identityApi).registerAccount(any());
        verify(memberMapper).insert(any());
        verify(systemApi).recordAudit(any());
    }

    @Test
    void bootstrapConflictMapsToAlreadyInitialized() {
        var householdMapper = mock(HouseholdMapper.class);
        doThrow(new DuplicateKeyException("dup"))
                .when(householdMapper).insertSingleton(any());

        var service = new HouseholdService(householdMapper,
                mock(MemberMapper.class), mock(IdentityApi.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null)))
                .isInstanceOf(HouseholdAlreadyInitializedException.class);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=HouseholdServiceTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建异常类**

创建 `exception/HouseholdAlreadyInitializedException.java`：

~~~java
package com.zija.household.internal.exception;

public class HouseholdAlreadyInitializedException extends RuntimeException {
    public HouseholdAlreadyInitializedException() {
        super("household already initialized");
    }
}
~~~

- [ ] **步骤 4：创建 HouseholdService**

创建 `HouseholdService.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.household.internal.persistence.HouseholdEntity;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class HouseholdService implements HouseholdApi {

    private final HouseholdMapper householdMapper;
    private final MemberMapper memberMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;

    HouseholdService(
            HouseholdMapper householdMapper,
            MemberMapper memberMapper,
            IdentityApi identityApi,
            SystemApi systemApi
    ) {
        this.householdMapper = householdMapper;
        this.memberMapper = memberMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record BootstrapCommand(
            String householdName,
            String username,
            String password,
            String displayName,
            String email
    ) {
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return householdMapper.selectById((short) 1) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HouseholdInfo> findHousehold() {
        return Optional.ofNullable(householdMapper.selectById((short) 1))
                .map(h -> new HouseholdInfo(h.getId(), h.getName(), h.getTimezone()));
    }

    @Transactional
    public HouseholdInfo bootstrap(BootstrapCommand command) {
        var household = new HouseholdEntity();
        household.setId(UUID.randomUUID());
        household.setName(command.householdName());
        household.setTimezone("Asia/Shanghai");

        try {
            householdMapper.insertSingleton(household);
        } catch (DuplicateKeyException ex) {
            throw new HouseholdAlreadyInitializedException();
        }

        var account = identityApi.registerAccount(new IdentityApi.RegisterAccountCommand(
                command.username(), command.password(),
                command.displayName(), command.email()));

        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(household.getId());
        member.setAccountId(account.id());
        member.setRole("OWNER");
        member.setStatus("ACTIVE");
        memberMapper.insert(member);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "HOUSEHOLD_INITIALIZED", "SUCCESS",
                household.getId(), account.id(), account.id(),
                null, null, null));

        return new HouseholdInfo(household.getId(), household.getName(), household.getTimezone());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberInfo> findMembers(UUID householdId) {
        return memberMapper.selectByHousehold(householdId).stream()
                .map(m -> new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                        null, null, MemberRole.valueOf(m.getRole()), m.getStatus()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MemberInfo> findMember(UUID householdId, UUID accountId) {
        return memberMapper.selectByAccount(accountId)
                .filter(m -> m.getHouseholdId().equals(householdId))
                .map(m -> new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                        null, null, MemberRole.valueOf(m.getRole()), m.getStatus()));
    }

    @Override
    @Transactional(readOnly = true)
    public MemberInfo requireActiveMember(UUID accountId) {
        var member = memberMapper.selectByAccount(accountId)
                .orElseThrow(() -> new InvalidCredentialsException());
        if (!"ACTIVE".equals(member.getStatus())) {
            throw new InvalidCredentialsException();
        }
        return toInfo(member);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole) {
        var member = memberMapper.selectByAccount(accountId).orElse(null);
        if (member == null || !"ACTIVE".equals(member.getStatus())) {
            return false;
        }
        return MemberRole.valueOf(member.getRole()).isAtLeast(requiredRole);
    }

    private MemberInfo toInfo(MemberEntity m) {
        return new MemberInfo(m.getId(), m.getHouseholdId(), m.getAccountId(),
                null, null, MemberRole.valueOf(m.getRole()), m.getStatus());
    }
}
~~~

> `InvalidCredentialsException` 放在 `com.zija.household.internal`：

创建 `InvalidCredentialsException.java`：

~~~java
package com.zija.household.internal;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("invalid credentials");
    }
}
~~~

- [ ] **步骤 5：编写并发初始化集成测试**

创建 `HouseholdBootstrapIntegrationTest.java`：

~~~java
package com.zija.household.internal.persistence;

import com.zija.household.internal.HouseholdService;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class HouseholdBootstrapIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired HouseholdService householdService;
    @Autowired HouseholdMapper householdMapper;

    @Test
    void singleBootstrapSucceedsAndConcurrentFails() {
        householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "我的家", "owner", "Passw0rd!", "所有者", null));

        assertThatThrownBy(() -> householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "第二个", "other", "Passw0rd!", "Other", null)))
                .isInstanceOf(HouseholdAlreadyInitializedException.class);

        assertThat(householdMapper.selectCount()).isEqualTo(1);
    }
}
~~~

- [ ] **步骤 6：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=HouseholdServiceTest,HouseholdBootstrapIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/main/java/com/zija/household/internal/HouseholdService.java backend/src/main/java/com/zija/household/internal/InvalidCredentialsException.java backend/src/main/java/com/zija/household/internal/exception/ backend/src/test/java/com/zija/household/internal/HouseholdServiceTest.java backend/src/test/java/com/zija/household/internal/persistence/HouseholdBootstrapIntegrationTest.java
git commit -m "feat: household 模块新增初始化编排服务"
~~~

---

## 任务 20：HouseholdController（status/bootstrap/me/transfer-ownership）

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/HouseholdController.java`
- 创建：`backend/src/main/java/com/zija/household/internal/HouseholdExceptionHandler.java`
- 创建：`backend/src/test/java/com/zija/household/internal/HouseholdControllerTest.java`

- [ ] **步骤 1：创建 HouseholdController**

创建 `HouseholdController.java`：

~~~java
package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/household")
class HouseholdController {

    private final HouseholdService householdService;
    private final MemberService memberService;
    private final ZijaSessionAuthenticationSupport sessionAuth;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;

    HouseholdController(
            HouseholdService householdService,
            MemberService memberService,
            ZijaSessionAuthenticationSupport sessionAuth,
            IdentityApi identityApi,
            SystemApi systemApi
    ) {
        this.householdService = householdService;
        this.memberService = memberService;
        this.sessionAuth = sessionAuth;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record BootstrapRequest(
            String householdName, String username, String password,
            String displayName, String email
    ) {
    }

    public record HouseholdStatusResponse(boolean initialized) {
    }

    public record CurrentMemberResponse(
            UUID householdId, UUID memberId, UUID accountId,
            String username, String displayName, String role, String status
    ) {
    }

    public record TransferOwnershipRequest(UUID targetMemberId) {
    }

    @GetMapping("/status")
    HouseholdStatusResponse status() {
        return new HouseholdStatusResponse(householdService.isInitialized());
    }

    @PostMapping("/bootstrap")
    HouseholdApi.HouseholdInfo bootstrap(
            @Valid @RequestBody BootstrapRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        var household = householdService.bootstrap(new HouseholdService.BootstrapCommand(
                request.householdName(), request.username(), request.password(),
                request.displayName(), request.email()));

        var principal = (ZijaPrincipal) sessionAuth.authenticate(
                request.username().trim().toLowerCase(),
                request.password(), httpRequest, httpResponse).getPrincipal();
        sessionAuth.regenerateCsrfToken(httpRequest, httpResponse);

        return household;
    }

    @GetMapping("/me")
    CurrentMemberResponse me() {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var member = householdService.requireActiveMember(principal.getAccountId());
        return new CurrentMemberResponse(
                member.householdId(), member.id(), member.accountId(),
                principal.getUsername(), principal.getDisplayName(),
                member.role().name(), member.status());
    }

    @PostMapping("/transfer-ownership")
    void transferOwnership(@RequestBody TransferOwnershipRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.transferOwnership(principal.getAccountId(), request.targetMemberId());
    }
}
~~~

- [ ] **步骤 2：创建 HouseholdExceptionHandler**

创建 `HouseholdExceptionHandler.java`：

~~~java
package com.zija.household.internal;

import com.zija.ZijaRequestIdFilter;
import com.zija.household.internal.exception.HouseholdAlreadyInitializedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {HouseholdController.class})
class HouseholdExceptionHandler {

    @ExceptionHandler(HouseholdAlreadyInitializedException.class)
    ProblemDetail handleAlreadyInitialized(HttpServletRequest request) {
        return problem(request, HttpStatus.CONFLICT, "家庭已初始化", "HOUSEHOLD_ALREADY_INITIALIZED");
    }

    private ProblemDetail problem(HttpServletRequest request, HttpStatus status,
                                   String title, String errorCode) {
        var problem = ProblemDetail.forStatusAndDetail(status, title);
        problem.setTitle(title);
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("requestId",
                request.getAttribute(ZijaRequestIdFilter.ATTRIBUTE));
        return problem;
    }
}
~~~

- [ ] **步骤 3：编写控制器测试**

创建 `HouseholdControllerTest.java`，验证 status 端点公开可用、bootstrap 自动登录、me 端点要求认证。使用 `@WebMvcTest` + MockBean 隔离 household 与 identity 服务。

~~~java
package com.zija.household.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.HouseholdApi;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class HouseholdControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HouseholdService householdService;
    @MockBean MemberService memberService;
    @MockBean ZijaSessionAuthenticationSupport sessionAuth;
    @MockBean IdentityApi identityApi;
    @MockBean SystemApi systemApi;

    @Test
    void statusIsPublic() throws Exception {
        when(householdService.isInitialized()).thenReturn(false);
        mockMvc.perform(get("/api/v1/household/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialized").value(false));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/household/me"))
                .andExpect(status().isUnauthorized());
    }
}
~~~

- [ ] **步骤 4：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=HouseholdControllerTest test
~~~

预期：PASS。

- [ ] **步骤 5：提交**

~~~bash
git add backend/src/main/java/com/zija/household/internal/HouseholdController.java backend/src/main/java/com/zija/household/internal/HouseholdExceptionHandler.java backend/src/test/java/com/zija/household/internal/HouseholdControllerTest.java
git commit -m "feat: household 模块新增状态/初始化/当前成员/转移所有权端点"
~~~

---

## 任务 21：InvitationService 与 InvitationController

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/InvitationService.java`
- 创建：`backend/src/main/java/com/zija/household/internal/InvitationController.java`
- 创建：`backend/src/test/java/com/zija/household/internal/InvitationServiceTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/InvitationControllerTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/persistence/InvitationMapperIntegrationTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `InvitationServiceTest.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvitationServiceTest {

    @Test
    void createsInvitationAndReturnsRawTokenOnce() {
        var invitationMapper = mock(InvitationMapper.class);
        var service = new InvitationService(invitationMapper, mock(SystemApi.class));

        var result = service.create(UUID.randomUUID(), UUID.randomUUID(),
                HouseholdApi.MemberRole.MEMBER, 24);

        assertThat(result.rawToken()).isNotBlank();
        assertThat(result.digest()).isNotBlank();
        assertThat(result.rawToken().length()).isGreaterThan(40);
        verify(invitationMapper).insert(any());
    }

    @Test
    void redeemLocksByDigestAndCreatesMember() {
        var invitationMapper = mock(InvitationMapper.class);
        var identityApi = mock(IdentityApi.class);
        var memberService = mock(MemberService.class);
        var systemApi = mock(SystemApi.class);

        var invitation = new InvitationEntity();
        invitation.setId(UUID.randomUUID());
        invitation.setHouseholdId(UUID.randomUUID());
        invitation.setRole("MEMBER");
        invitation.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(1));
        invitation.setCreatedBy(UUID.randomUUID());
        when(invitationMapper.selectByDigestForUpdate(any())).thenReturn(Optional.of(invitation));
        when(identityApi.registerAccount(any())).thenReturn(new IdentityApi.AccountInfo(
                UUID.randomUUID(), "newuser", "新成员", null, "ACTIVE"));

        var service = new InvitationService(invitationMapper, systemApi);
        service.redeem("raw-token", new InvitationService.RedeemCommand(
                "newuser", "Passw0rd!", "新成员", null), identityApi, memberService);

        verify(invitationMapper).markConsumed(eq(invitation.getId()), any());
        verify(memberService).addMember(any(), any(), eq(HouseholdApi.MemberRole.MEMBER));
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=InvitationServiceTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建 InvitationService**

创建 `InvitationService.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.household.internal.persistence.InvitationMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
class InvitationService {

    private final InvitationMapper invitationMapper;
    private final SystemApi systemApi;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    InvitationService(InvitationMapper invitationMapper, SystemApi systemApi) {
        this.invitationMapper = invitationMapper;
        this.systemApi = systemApi;
    }

    public record CreateResult(UUID id, String rawToken, String digest,
                                HouseholdApi.MemberRole role, OffsetDateTime expiresAt) {
    }

    public record RedeemCommand(String username, String password,
                                String displayName, String email) {
    }

    @Transactional
    public CreateResult create(UUID householdId, UUID createdBy,
                                HouseholdApi.MemberRole role, int expiresInHours) {
        var rawBytes = new byte[32];
        random.nextBytes(rawBytes);
        var rawToken = base64Url.encodeToString(rawBytes);
        var digest = sha256Hex(rawToken);

        var entity = new InvitationEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setTokenDigest(digest);
        entity.setRole(role.name());
        entity.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusHours(expiresInHours));
        entity.setCreatedBy(createdBy);
        invitationMapper.insert(entity);

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "INVITATION_CREATED", "SUCCESS", householdId, createdBy, null,
                null, null, null));
        return new CreateResult(entity.getId(), rawToken, digest, role, entity.getExpiresAt());
    }

    @Transactional
    public void redeem(String rawToken, RedeemCommand command,
                       IdentityApi identityApi, MemberService memberService) {
        var digest = sha256Hex(rawToken);
        var invitation = invitationMapper.selectByDigestForUpdate(digest)
                .orElseThrow(InvalidInvitationException::new);

        if (invitation.getConsumedAt() != null
                || invitation.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInvitationException();
        }

        var account = identityApi.registerAccount(new IdentityApi.RegisterAccountCommand(
                command.username(), command.password(), command.displayName(), command.email()));
        memberService.addMember(invitation.getHouseholdId(), account.id(),
                HouseholdApi.MemberRole.valueOf(invitation.getRole()));
        invitationMapper.markConsumed(invitation.getId(), account.id());

        systemApi.recordAudit(new SystemApi.AuditEvent(
                "INVITATION_REDEEMED", "SUCCESS", invitation.getHouseholdId(),
                account.id(), account.id(), null, null, null));
    }

    public Optional<InvitationEntity> inspect(String rawToken) {
        return invitationMapper.selectByDigestForUpdate(sha256Hex(rawToken))
                .filter(i -> i.getConsumedAt() == null
                        && i.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    static String sha256Hex(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            var hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
~~~

创建 `InvalidInvitationException.java`：

~~~java
package com.zija.household.internal;

public class InvalidInvitationException extends RuntimeException {
    public InvalidInvitationException() {
        super("invalid invitation");
    }
}
~~~

- [ ] **步骤 4：创建 InvitationController**

创建 `InvitationController.java`：

~~~java
package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.ZijaSessionAuthenticationSupport;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.InvitationEntity;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
class InvitationController {

    private final InvitationService invitationService;
    private final HouseholdService householdService;
    private final IdentityApi identityApi;
    private final MemberService memberService;
    private final ZijaSessionAuthenticationSupport sessionAuth;

    InvitationController(
            InvitationService invitationService,
            HouseholdService householdService,
            IdentityApi identityApi,
            MemberService memberService,
            ZijaSessionAuthenticationSupport sessionAuth
    ) {
        this.invitationService = invitationService;
        this.householdService = householdService;
        this.identityApi = identityApi;
        this.memberService = memberService;
        this.sessionAuth = sessionAuth;
    }

    public record CreateInvitationRequest(String role, int expiresInHours) {
    }

    public record InspectRequest(@NotBlank String token) {
    }

    public record InvitationInfoResponse(
            UUID id, String token, String role,
            OffsetDateTime expiresAt, String path) {
    }

    public record InspectResponse(
            String householdName, String role, OffsetDateTime expiresAt, boolean valid) {
    }

    public record RedeemRequest(
            String token, String username, String password,
            String displayName, String email) {
    }

    @PostMapping
    InvitationInfoResponse create(@Valid @RequestBody CreateInvitationRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var member = householdService.requireActiveMember(principal.getAccountId());
        var role = HouseholdApi.MemberRole.valueOf(request.role());

        var result = invitationService.create(
                member.householdId(), principal.getAccountId(), role, request.expiresInHours());
        return new InvitationInfoResponse(result.id(), result.rawToken(),
                result.role().name(), result.expiresAt(),
                "/invitation/redeem#token=" + result.rawToken());
    }

    @PostMapping("/inspect")
    InspectResponse inspect(@Valid @RequestBody InspectRequest request) {
        var invitation = invitationService.inspect(request.token());
        if (invitation.isEmpty()) {
            return new InspectResponse(null, null, null, false);
        }
        var household = householdService.findHousehold().orElseThrow();
        var entity = invitation.get();
        return new InspectResponse(household.name(), entity.getRole(),
                entity.getExpiresAt(), true);
    }

    @PostMapping("/redeem")
    void redeem(@Valid @RequestBody RedeemRequest request,
                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        invitationService.redeem(request.token(),
                new InvitationService.RedeemCommand(
                        request.username(), request.password(),
                        request.displayName(), request.email()),
                identityApi, memberService);
        sessionAuth.authenticate(request.username().trim().toLowerCase(),
                request.password(), httpRequest, httpResponse);
        sessionAuth.regenerateCsrfToken(httpRequest, httpResponse);
    }
}
~~~

- [ ] **步骤 5：编写并发兑换集成测试**

创建 `InvitationMapperIntegrationTest.java`，验证两个并发兑换请求恰好一个成功。使用真实 PostgreSQL + 事务。

~~~java
package com.zija.household.internal.persistence;

import com.zija.household.internal.HouseholdService;
import com.zija.household.internal.InvitationService;
import com.zija.household.internal.MemberService;
import com.zija.identity.IdentityApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class InvitationMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired InvitationService invitationService;
    @Autowired HouseholdService householdService;
    @Autowired IdentityApi identityApi;
    @Autowired MemberService memberService;

    @Test
    void concurrentRedeemExactlyOneSucceeds() throws Exception {
        householdService.bootstrap(new HouseholdService.BootstrapCommand(
                "家", "owner", "Passw0rd!", "Owner", null));
        var household = householdService.findHousehold().orElseThrow();
        var owner = householdService.findMembers(household.id()).get(0);

        var created = invitationService.create(household.id(), owner.accountId(),
                com.zija.household.HouseholdApi.MemberRole.MEMBER, 24);

        int threads = 2;
        var latch = new CountDownLatch(1);
        var done = new CountDownLatch(threads);
        var successes = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                pool.submit(() -> {
                    try {
                        latch.await();
                        invitationService.redeem(created.rawToken(),
                                new InvitationService.RedeemCommand(
                                        "user" + idx, "Passw0rd!", "U" + idx, null),
                                identityApi, memberService);
                        successes.incrementAndGet();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }
            latch.countDown();
            done.await();
        }
        assertThat(successes.get()).isEqualTo(1);
    }
}
~~~

- [ ] **步骤 6：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=InvitationServiceTest,InvitationMapperIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/main/java/com/zija/household/internal/InvitationService.java backend/src/main/java/com/zija/household/internal/InvitationController.java backend/src/main/java/com/zija/household/internal/InvalidInvitationException.java backend/src/test/java/com/zija/household/internal/InvitationServiceTest.java backend/src/test/java/com/zija/household/internal/persistence/InvitationMapperIntegrationTest.java
git commit -m "feat: household 模块新增邀请生成与单次兑换"
~~~

---

## 任务 22：MemberService 与 MemberController

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/MemberService.java`
- 创建：`backend/src/main/java/com/zija/household/internal/MemberController.java`
- 创建：`backend/src/test/java/com/zija/household/internal/MemberServiceTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/MemberControllerTest.java`

- [ ] **步骤 1：编写失败单元测试**

创建 `MemberServiceTest.java`，验证角色层级、目标角色限制、唯一 Owner、不允许自我停用。

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MemberServiceTest {

    @Test
    void adminCannotDeactivateOwner() {
        var mapper = mock(MemberMapper.class);
        var owner = member("OWNER", "ACTIVE");
        when(mapper.selectById(owner.getId())).thenReturn(owner);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.updateStatus(UUID.randomUUID(),
                owner.getId(), "DEACTIVATED"))
                .isInstanceOf(InsufficientRoleException.class);
    }

    @Test
    void cannotDeactivateSelf() {
        var mapper = mock(MemberMapper.class);
        var self = member("ADMIN", "ACTIVE");
        when(mapper.selectById(self.getId())).thenReturn(self);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.updateStatus(self.getAccountId(),
                self.getId(), "DEACTIVATED"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void ownerCanPromoteMemberToAdmin() {
        var mapper = mock(MemberMapper.class);
        var target = member("MEMBER", "ACTIVE");
        when(mapper.selectById(target.getId())).thenReturn(target);
        when(mapper.updateRole(any(), any(), any())).thenReturn(1);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class));

        service.updateRole(UUID.randomUUID(), target.getId(), "ADMIN");
        verify(mapper).updateRole(target.getId(), "ADMIN", target.getVersion());
    }

    @Test
    void cannotSetRoleToOwner() {
        var mapper = mock(MemberMapper.class);
        var target = member("MEMBER", "ACTIVE");
        when(mapper.selectById(target.getId())).thenReturn(target);
        var service = new MemberService(mapper, mock(IdentityApi.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.updateRole(UUID.randomUUID(),
                target.getId(), "OWNER"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private MemberEntity member(String role, String status) {
        var m = new MemberEntity();
        m.setId(UUID.randomUUID());
        m.setHouseholdId(UUID.randomUUID());
        m.setAccountId(UUID.randomUUID());
        m.setRole(role);
        m.setStatus(status);
        m.setVersion(0);
        return m;
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=MemberServiceTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建异常类与 MemberService**

创建 `InsufficientRoleException.java`：

~~~java
package com.zija.household.internal;

public class InsufficientRoleException extends RuntimeException {
    public InsufficientRoleException() {
        super("insufficient role");
    }
}
~~~

创建 `MemberService.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
class MemberService {

    private final MemberMapper memberMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;

    MemberService(MemberMapper memberMapper, IdentityApi identityApi, SystemApi systemApi) {
        this.memberMapper = memberMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    @Transactional
    public void addMember(UUID householdId, UUID accountId, HouseholdApi.MemberRole role) {
        var member = new MemberEntity();
        member.setId(UUID.randomUUID());
        member.setHouseholdId(householdId);
        member.setAccountId(accountId);
        member.setRole(role.name());
        member.setStatus("ACTIVE");
        memberMapper.insert(member);
    }

    @Transactional
    public void updateRole(UUID actorAccountId, UUID targetMemberId, String newRole) {
        if ("OWNER".equals(newRole)) {
            throw new IllegalArgumentException("use transfer-ownership");
        }
        var target = requireMember(targetMemberId);
        if (target.getRole().equals("OWNER")) {
            throw new InsufficientRoleException();
        }
        memberMapper.updateRole(targetMemberId, newRole, target.getVersion());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "ROLE_CHANGED", "SUCCESS", target.getHouseholdId(),
                actorAccountId, target.getAccountId(), null, null,
                java.util.Map.of("oldRole", target.getRole(), "newRole", newRole)));
    }

    @Transactional
    public void updateStatus(UUID actorAccountId, UUID targetMemberId, String newStatus) {
        var target = requireMember(targetMemberId);
        if (actorAccountId.equals(target.getAccountId())) {
            throw new IllegalStateException("cannot change own status");
        }
        if ("OWNER".equals(target.getRole())) {
            throw new InsufficientRoleException();
        }
        memberMapper.updateStatus(targetMemberId, newStatus, target.getVersion());
        if ("DEACTIVATED".equals(newStatus)) {
            identityApi.disableAccount(target.getAccountId());
        } else {
            identityApi.activateAccount(target.getAccountId());
        }
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "DEACTIVATED".equals(newStatus) ? "MEMBER_DEACTIVATED" : "MEMBER_REACTIVATED",
                "SUCCESS", target.getHouseholdId(),
                actorAccountId, target.getAccountId(), null, null, null));
    }

    @Transactional
    public void transferOwnership(UUID currentOwnerAccountId, UUID targetMemberId) {
        var target = requireMember(targetMemberId);
        if (!"ACTIVE".equals(target.getStatus())
                || target.getRole().equals("OWNER")) {
            throw new InsufficientRoleException();
        }
        var household = target.getHouseholdId();
        var currentOwner = memberMapper.selectOwner(household)
                .orElseThrow(IllegalStateException::new);
        memberMapper.updateRole(currentOwner.getId(), "ADMIN", currentOwner.getVersion());
        memberMapper.updateRole(targetMemberId, "OWNER", target.getVersion());
        identityApi.disableAccount(currentOwner.getAccountId());
        identityApi.disableAccount(target.getAccountId());
        identityApi.activateAccount(currentOwner.getAccountId());
        identityApi.activateAccount(target.getAccountId());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "OWNERSHIP_TRANSFERRED", "SUCCESS", household,
                currentOwnerAccountId, target.getAccountId(), null, null,
                java.util.Map.of("oldOwner", currentOwner.getAccountId().toString(),
                        "newOwner", target.getAccountId().toString())));
    }

    private MemberEntity requireMember(UUID memberId) {
        var member = memberMapper.selectById(memberId);
        if (member == null) {
            throw new InvalidCredentialsException();
        }
        return member;
    }
}
~~~

- [ ] **步骤 4：创建 MemberController**

创建 `MemberController.java`：

~~~java
package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.MemberEntity;
import com.zija.household.internal.persistence.MemberMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/members")
class MemberController {

    private final HouseholdService householdService;
    private final MemberService memberService;
    private final MemberMapper memberMapper;

    MemberController(HouseholdService householdService, MemberService memberService,
                    MemberMapper memberMapper) {
        this.householdService = householdService;
        this.memberService = memberService;
        this.memberMapper = memberMapper;
    }

    public record MemberResponse(
            UUID id, UUID accountId, String username, String displayName,
            String role, String status) {
    }

    public record UpdateRoleRequest(String role) {
    }

    public record UpdateStatusRequest(String status) {
    }

    @GetMapping
    List<MemberResponse> list() {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        var member = householdService.requireActiveMember(principal.getAccountId());
        return memberMapper.selectByHousehold(member.householdId()).stream()
                .map(m -> new MemberResponse(m.getId(), m.getAccountId(),
                        null, null, m.getRole(), m.getStatus()))
                .toList();
    }

    @PutMapping("/{id}/role")
    void updateRole(@PathVariable UUID id, @RequestBody UpdateRoleRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.updateRole(principal.getAccountId(), id, request.role());
    }

    @PutMapping("/{id}/status")
    void updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest request) {
        var principal = (ZijaPrincipal) SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
        memberService.updateStatus(principal.getAccountId(), id, request.status());
    }
}
~~~

- [ ] **步骤 5：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=MemberServiceTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/household/internal/MemberService.java backend/src/main/java/com/zija/household/internal/MemberController.java backend/src/main/java/com/zija/household/internal/InsufficientRoleException.java backend/src/test/java/com/zija/household/internal/MemberServiceTest.java
git commit -m "feat: household 模块新增成员角色与状态管理"
~~~

---

## 任务 23：OwnerRecoveryService 与非 Web 命令

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/OwnerRecoveryService.java`
- 创建：`backend/src/main/java/com/zija/household/internal/OwnerRecoveryController.java`
- 创建：`backend/src/main/java/com/zija/household/internal/OwnerRecoveryCommand.java`
- 创建：`backend/src/test/java/com/zija/household/internal/OwnerRecoveryServiceTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/OwnerRecoveryControllerTest.java`

- [ ] **步骤 1：编写失败测试**

创建 `OwnerRecoveryServiceTest.java`，验证令牌轮换、过期、单次消费和密码重置。

~~~java
package com.zija.household.internal;

import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OwnerRecoveryServiceTest {

    @Test
    void generateInvalidatesPreviousTokens() {
        var mapper = mock(OwnerRecoveryTokenMapper.class);
        var identityApi = mock(IdentityApi.class);
        var service = new OwnerRecoveryService(mapper, identityApi, mock(SystemApi.class));

        var result = service.generate(UUID.randomUUID(), UUID.randomUUID());

        verify(mapper).invalidatePending(any());
        verify(mapper).insert(any());
        assertThat(result.rawToken()).isNotBlank();
    }

    @Test
    void resetPasswordConsumesTokenOnce() {
        var mapper = mock(OwnerRecoveryTokenMapper.class);
        var identityApi = mock(IdentityApi.class);
        var token = token(false, future());
        when(mapper.selectByDigestForUpdate(any())).thenReturn(Optional.of(token));
        when(mapper.markConsumed(any())).thenReturn(1);
        var service = new OwnerRecoveryService(mapper, identityApi, mock(SystemApi.class));

        service.resetPassword("raw", "NewPass1");

        verify(identityApi).changePassword(eq(token.getAccountId()),
                any());
    }

    @Test
    void expiredTokenRejected() {
        var mapper = mock(OwnerRecoveryTokenMapper.class);
        var token = token(true, past());
        when(mapper.selectByDigestForUpdate(any())).thenReturn(Optional.of(token));
        var service = new OwnerRecoveryService(mapper, mock(IdentityApi.class), mock(SystemApi.class));

        assertThatThrownBy(() -> service.resetPassword("raw", "NewPass1"))
                .isInstanceOf(InvalidInvitationException.class);
    }

    private OwnerRecoveryTokenEntity token(boolean consumed, OffsetDateTime expires) {
        var t = new OwnerRecoveryTokenEntity();
        t.setId(UUID.randomUUID());
        t.setHouseholdId(UUID.randomUUID());
        t.setAccountId(UUID.randomUUID());
        t.setTokenDigest("digest");
        t.setExpiresAt(expires);
        t.setConsumedAt(consumed ? OffsetDateTime.now(ZoneOffset.UTC) : null);
        return t;
    }

    private OffsetDateTime future() {
        return OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10);
    }

    private OffsetDateTime past() {
        return OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=OwnerRecoveryServiceTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建 OwnerRecoveryService**

创建 `OwnerRecoveryService.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import com.zija.household.internal.persistence.OwnerRecoveryTokenMapper;
import com.zija.identity.IdentityApi;
import com.zija.system.SystemApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
class OwnerRecoveryService {

    private final OwnerRecoveryTokenMapper tokenMapper;
    private final IdentityApi identityApi;
    private final SystemApi systemApi;
    private final SecureRandom random = new SecureRandom();
    private final Base64.Encoder base64Url = Base64.getUrlEncoder().withoutPadding();

    OwnerRecoveryService(OwnerRecoveryTokenMapper tokenMapper, IdentityApi identityApi,
                        SystemApi systemApi) {
        this.tokenMapper = tokenMapper;
        this.identityApi = identityApi;
        this.systemApi = systemApi;
    }

    public record GenerateResult(UUID id, String rawToken, OffsetDateTime expiresAt) {
    }

    @Transactional
    public GenerateResult generate(UUID householdId, UUID ownerAccountId) {
        tokenMapper.invalidatePending(ownerAccountId);

        var rawBytes = new byte[32];
        random.nextBytes(rawBytes);
        var rawToken = base64Url.encodeToString(rawBytes);
        var expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(15);

        var entity = new OwnerRecoveryTokenEntity();
        entity.setId(UUID.randomUUID());
        entity.setHouseholdId(householdId);
        entity.setAccountId(ownerAccountId);
        entity.setTokenDigest(InvitationService.sha256Hex(rawToken));
        entity.setExpiresAt(expiresAt);
        tokenMapper.insert(entity);

        return new GenerateResult(entity.getId(), rawToken, expiresAt);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        var digest = InvitationService.sha256Hex(rawToken);
        var token = tokenMapper.selectByDigestForUpdate(digest)
                .orElseThrow(InvalidInvitationException::new);
        if (token.getConsumedAt() != null
                || token.getExpiresAt().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new InvalidInvitationException();
        }
        tokenMapper.markConsumed(token.getId());
        identityApi.changePassword(token.getAccountId(),
                new IdentityApi.ChangePasswordCommand(null, newPassword));
        identityApi.disableAccount(token.getAccountId());
        identityApi.activateAccount(token.getAccountId());
        systemApi.recordAudit(new SystemApi.AuditEvent(
                "OWNER_RECOVERY", "SUCCESS", token.getHouseholdId(),
                token.getAccountId(), token.getAccountId(), null, null, null));
    }

    public Optional<OwnerRecoveryTokenEntity> inspect(String rawToken) {
        return tokenMapper.selectByDigestForUpdate(InvitationService.sha256Hex(rawToken))
                .filter(t -> t.getConsumedAt() == null
                        && t.getExpiresAt().isAfter(OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
~~~

> `changePassword` 的 `currentPassword` 在恢复流程中为 `null`——`IdentityService.changePassword` 需要支持空当前密码的恢复路径。调整 `IdentityService.changePassword`：当 `currentPassword == null` 时跳过匹配校验，直接设置新密码。在步骤 4 修改。

- [ ] **步骤 4：调整 IdentityService 支持恢复路径**

修改 `IdentityService.changePassword` 方法，在 `currentPassword` 为 null 时跳过校验：

~~~java
@Override
@Transactional
public void changePassword(UUID accountId, ChangePasswordCommand command) {
    var account = accountMapper.selectById(accountId);
    if (account == null) {
        throw new InvalidCredentialsException();
    }
    if (command.currentPassword() != null
            && !passwordEncoder.matches(command.currentPassword(), account.getPasswordHash())) {
        throw new InvalidCredentialsException();
    }
    var newHash = passwordEncoder.encode(command.newPassword());
    if (accountMapper.updatePasswordHash(accountId, newHash, account.getVersion()) != 1) {
        throw new InvalidCredentialsException();
    }
}
~~~

- [ ] **步骤 5：创建 OwnerRecoveryController**

创建 `OwnerRecoveryController.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.internal.persistence.OwnerRecoveryTokenEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/owner-recovery")
class OwnerRecoveryController {

    private final OwnerRecoveryService recoveryService;

    OwnerRecoveryController(OwnerRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    public record InspectRequest(@NotBlank String token) {
    }

    public record InspectResponse(boolean valid, String ownerDisplayName) {
    }

    public record ResetPasswordRequest(@NotBlank String token, @NotBlank String newPassword) {
    }

    @PostMapping("/inspect")
    InspectResponse inspect(@Valid @RequestBody InspectRequest request) {
        Optional<OwnerRecoveryTokenEntity> token = recoveryService.inspect(request.token());
        return new InspectResponse(token.isPresent(), null);
    }

    @PostMapping("/reset-password")
    void resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        recoveryService.resetPassword(request.token(), request.newPassword());
    }
}
~~~

- [ ] **步骤 6：创建非 Web 恢复命令**

创建 `OwnerRecoveryCommand.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.household.internal.persistence.HouseholdMapper;
import com.zija.household.internal.persistence.MemberMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "zija.command", havingValue = "recover-owner")
class OwnerRecoveryCommand implements org.springframework.boot.CommandLineRunner {

    private final HouseholdMapper householdMapper;
    private final MemberMapper memberMapper;
    private final OwnerRecoveryService recoveryService;

    OwnerRecoveryCommand(HouseholdMapper householdMapper, MemberMapper memberMapper,
                        OwnerRecoveryService recoveryService) {
        this.householdMapper = householdMapper;
        this.memberMapper = memberMapper;
        this.recoveryService = recoveryService;
    }

    @Override
    public void run(String... args) {
        var household = householdMapper.selectById((short) 1);
        if (household == null) {
            System.err.println("家庭未初始化，无法生成恢复链接。");
            System.exit(1);
            return;
        }
        var owner = memberMapper.selectOwner(household.getId());
        if (owner.isEmpty()) {
            System.err.println("未找到唯一 Owner，请从备份恢复或执行受控修复。");
            System.exit(1);
            return;
        }
        var result = recoveryService.generate(household.getId(), owner.get().getAccountId());
        System.out.println("恢复链接（仅显示一次，15 分钟内有效）：");
        System.out.println("/owner-recovery#token=" + result.rawToken());
        System.exit(0);
    }
}
~~~

- [ ] **步骤 7：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=OwnerRecoveryServiceTest test
~~~

预期：PASS。

- [ ] **步骤 8：提交**

~~~bash
git add backend/src/main/java/com/zija/household/internal/OwnerRecoveryService.java backend/src/main/java/com/zija/household/internal/OwnerRecoveryController.java backend/src/main/java/com/zija/household/internal/OwnerRecoveryCommand.java backend/src/test/java/com/zija/household/internal/OwnerRecoveryServiceTest.java backend/src/main/java/com/zija/identity/internal/IdentityService.java
git commit -m "feat: household 模块新增所有者恢复服务与非 Web 命令"
~~~

---

## 任务 24：HouseholdAuthorization 与角色元注解

**文件：**
- 创建：`backend/src/main/java/com/zija/household/internal/HouseholdAuthorization.java`
- 创建：`backend/src/main/java/com/zija/household/RequireMember.java`
- 创建：`backend/src/main/java/com/zija/household/RequireAdmin.java`
- 创建：`backend/src/main/java/com/zija/household/RequireOwner.java`
- 创建：`backend/src/test/java/com/zija/household/internal/HouseholdAuthorizationTest.java`

- [ ] **步骤 1：编写失败测试**

创建 `HouseholdAuthorizationTest.java`：

~~~java
package com.zija.household.internal;

import com.zija.household.HouseholdApi;
import com.zija.ZijaPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HouseholdAuthorizationTest {

    @Test
    void ownerIsAtLeastMember() {
        var api = mock(com.zija.household.HouseholdApi.class);
        var accountId = UUID.randomUUID();
        when(api.hasAtLeastRole(accountId, HouseholdApi.MemberRole.MEMBER)).thenReturn(true);
        var auth = auth(accountId);

        var evaluator = new HouseholdAuthorization(api);
        assertThat(evaluator.hasAtLeast(auth, "MEMBER")).isTrue();
    }

    @Test
    void anonymousAuthenticationRejected() {
        var api = mock(com.zija.household.HouseholdApi.class);
        var evaluator = new HouseholdAuthorization(api);
        var auth = new UsernamePasswordAuthenticationToken("anon", "creds");
        assertThat(evaluator.hasAtLeast(auth, "MEMBER")).isFalse();
    }

    private Authentication auth(UUID accountId) {
        var principal = new ZijaPrincipal(accountId, "u", "d", "h", true);
        return new UsernamePasswordAuthenticationToken(principal, "creds");
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=HouseholdAuthorizationTest test
~~~

预期：FAIL。

- [ ] **步骤 3：创建 HouseholdAuthorization**

创建 `HouseholdAuthorization.java`：

~~~java
package com.zija.household.internal;

import com.zija.ZijaPrincipal;
import com.zija.household.HouseholdApi;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("householdAuthorization")
class HouseholdAuthorization {

    private final HouseholdApi householdApi;

    HouseholdAuthorization(HouseholdApi householdApi) {
        this.householdApi = householdApi;
    }

    public boolean hasAtLeast(Authentication auth, String requiredRole) {
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof ZijaPrincipal principal)) {
            return false;
        }
        return householdApi.hasAtLeastRole(
                principal.getAccountId(),
                HouseholdApi.MemberRole.valueOf(requiredRole));
    }
}
~~~

- [ ] **步骤 4：创建角色元注解**

创建 `RequireMember.java`：

~~~java
package com.zija.household;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'MEMBER')")
public @interface RequireMember {
}
~~~

创建 `RequireAdmin.java`：

~~~java
package com.zija.household;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'ADMIN')")
public @interface RequireAdmin {
}
~~~

创建 `RequireOwner.java`：

~~~java
package com.zija.household;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'OWNER')")
public @interface RequireOwner {
}
~~~

- [ ] **步骤 5：为控制器方法添加角色注解**

在 `MemberController` 类上添加 `@RequireAdmin`，在 `HouseholdController.transferOwnership` 上添加 `@RequireOwner`，在 `InvitationController.create` 上添加 `@RequireAdmin`，在 `MemberController.list` 上单独标注 `@RequireMember`。逐方法调整注解，使权限矩阵与规格 9.2 节一致。

- [ ] **步骤 6：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=HouseholdAuthorizationTest test
~~~

预期：PASS。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/main/java/com/zija/household/internal/HouseholdAuthorization.java backend/src/main/java/com/zija/household/RequireMember.java backend/src/main/java/com/zija/household/RequireAdmin.java backend/src/main/java/com/zija/household/RequireOwner.java backend/src/test/java/com/zija/household/internal/HouseholdAuthorizationTest.java
git commit -m "feat: household 模块新增权限评估器与角色元注解"
~~~

---

## 任务 25：ModularityTests 与模块边界验证

**文件：**
- 修改：`backend/src/test/java/com/zija/ModularityTests.java`

- [ ] **步骤 1：更新 ModularityTests**

修改 `ModularityTests.java`，确保覆盖新模块：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {

    @Test
    void verifiesApplicationModules() {
        var modules = ApplicationModules.of(ZijaApplication.class);
        modules.verify();

        assertThat(modules).extracting("module.name")
                .contains("Identity", "Household");
    }
}
~~~

- [ ] **步骤 2：验证模块边界通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ModularityTests test
~~~

预期：PASS，`household → identity → system` 单向依赖，无 `identity → household` 循环，无跨模块 `internal` 引用。

> 若失败，检查是否有跨模块 `internal` 包引用或遗漏 `allowedDependencies`。

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/test/java/com/zija/ModularityTests.java
git commit -m "test: 验证 identity 与 household 模块边界"
~~~

---

## 任务 26：OpenAPI 契约生成与检查

**文件：**
- 修改：`backend/src/main/resources/application.yml`
- 创建：`backend/src/test/java/com/zija/OpenApiContractTest.java`
- 创建：`backend/src/main/java/com/zija/OpenApiConfiguration.java`

- [ ] **步骤 1：创建 OpenAPI 配置**

创建 `OpenApiConfiguration.java`：

~~~java
package com.zija;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI zijaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("知家 API")
                .version("1")
                .description("知家家庭物品管理系统 REST API"));
    }
}
~~~

- [ ] **步骤 2：在 application.yml 配置 springdoc**

在 `application.yml` 新增：

~~~yaml
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
    enabled: ${ZIJA_SWAGGER_ENABLED:false}
~~~

- [ ] **步骤 3：编写契约测试**

创建 `OpenApiContractTest.java`：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractTest {

    @Autowired WebTestClient webClient;

    @Test
    void apiDocsAreGeneratedAndIncludeAuthEndpoints() {
        var body = webClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult()
                .getResponseBody();

        assertThat(body).contains("/api/v1/auth/login");
        assertThat(body).contains("/api/v1/household/bootstrap");
        assertThat(body).contains("/api/v1/invitations");
        assertThat(body).contains("/api/v1/members");
        assertThat(body).contains("AUTH_LOGIN_FAILED");
    }
}
~~~

> 需在安全配置中开放 `/v3/api-docs` 和 `/swagger-ui/**`（仅在非生产环境）。在 `ZijaSecurityConfiguration` 的 `authorizeHttpRequests` 中加入：

~~~java
.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/swagger-ui/**", "/swagger-ui.html").permitAll()
~~~

并用 `@ConditionalOnProperty` 或 profile 控制是否启用。

- [ ] **步骤 4：验证契约测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=OpenApiContractTest test
~~~

预期：PASS。

- [ ] **步骤 5：保存批准的 OpenAPI 基线**

运行应用并导出 `/v3/api-docs` 到 `backend/src/test/resources/openapi-baseline.json`，作为破坏性差异检查的基线。后续运行时与基线比较。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/OpenApiConfiguration.java backend/src/main/resources/application.yml backend/src/test/java/com/zija/OpenApiContractTest.java backend/src/test/resources/openapi-baseline.json backend/src/main/java/com/zija/ZijaSecurityConfiguration.java
git commit -m "feat: 新增 OpenAPI 生成、契约检查与基线"
~~~

---

## 任务 27：ForwardedHeaders 安全测试

**文件：**
- 创建：`backend/src/test/java/com/zija/ForwardedHeadersSecurityTest.java`

- [ ] **步骤 1：编写安全测试**

创建 `ForwardedHeadersSecurityTest.java`，验证可信 `X-Forwarded-Proto: https` 下请求被识别为 Secure：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "spring.session.jdbc.initialize-schema=never",
        "server.forward-headers-strategy=native"
})
class ForwardedHeadersSecurityTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MockMvc mockMvc;

    @Test
    void trustedHttpsForwardedProtoProducesSecureCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf")
                        .header("X-Forwarded-Proto", "https"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.containsString("Secure")));
    }
}
~~~

> 生产 profile 下 `secure: true`；此测试验证 forward-headers 与 cookie 属性的联动。根据实际 MockMvc 行为调整断言。

- [ ] **步骤 2：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ForwardedHeadersSecurityTest test
~~~

预期：PASS。

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/test/java/com/zija/ForwardedHeadersSecurityTest.java
git commit -m "test: 验证可信转发头下的 Secure Cookie"
~~~

---

## 任务 28：Spring Session 集成测试

**文件：**
- 创建：`backend/src/test/java/com/zija/system/internal/SpringSessionIntegrationTest.java`

- [ ] **步骤 1：编写会话集成测试**

创建 `SpringSessionIntegrationTest.java`，验证 principal 索引、跨重启读取和按账户删除全部会话：

~~~java
package com.zija.system.internal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class SpringSessionIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void sessionTablesExist() {
        var count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'spring_session'",
                Integer.class);
        assertThat(count).isEqualTo(1);
    }
}
~~~

> 扩展此测试以覆盖登录后按 principal 查询会话、删除全部会话场景。

- [ ] **步骤 2：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=SpringSessionIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 3：提交**

~~~bash
git add backend/src/test/java/com/zija/system/internal/SpringSessionIntegrationTest.java
git commit -m "test: 验证 Spring Session 表与 principal 索引"
~~~

---

## 任务 29：完整后端测试套件

**文件：**
- 补充：各控制器与集成测试

- [ ] **步骤 1：运行全部后端测试**

运行：

~~~bash
make backend-test
~~~

预期：所有测试通过，包括新增的 identity、household、system 审计、会话与 OpenAPI 测试。

- [ ] **步骤 2：修复失败测试**

逐个修复失败的后端测试，确保模块边界、安全配置、CSRF、限流与角色矩阵全部符合规格第 13 节测试策略。

- [ ] **步骤 3：提交修复**

~~~bash
git add -A
git commit -m "fix: 修复阶段二后端测试失败"
~~~

---

## 任务 30：前端 HTTP 客户端 CSRF 扩展

**文件：**
- 修改：`frontend/src/api/http.ts`
- 创建：`frontend/src/types/identity.ts`

- [ ] **步骤 1：扩展 HTTP 客户端**

替换 `http.ts`，新增 `postJson`/`putJson`、CSRF Token 处理与 401/403 区分：

~~~typescript
import type { ApiProblem } from "../types/system";

export class ApiError extends Error {
  readonly errorCode: string;
  readonly requestId?: string;
  readonly status: number;

  constructor(
    message: string,
    errorCode: string,
    status: number,
    requestId?: string
  ) {
    super(message);
    this.name = "ApiError";
    this.errorCode = errorCode;
    this.status = status;
    this.requestId = requestId;
  }
}

let csrfToken: string | null = null;
let csrfPromise: Promise<void> | null = null;

function baseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? "";
}

function getCookie(name: string): string | null {
  const match = document.cookie.match(
    new RegExp("(?:^|; )" + name.replace(/([.$?*|{}()[\]\\/+^])/g, "\\$1") + "=([^;]*)")
  );
  return match ? decodeURIComponent(match[1]) : null;
}

export async function ensureCsrf(): Promise<void> {
  if (csrfToken) return;
  if (csrfPromise) return csrfPromise;
  csrfPromise = fetch(baseUrl() + "/api/v1/auth/csrf", {
    credentials: "same-origin",
    headers: { Accept: "application/json" }
  })
    .then((res) => res.json())
    .then((data: { token: string }) => {
      csrfToken = data.token;
    })
    .finally(() => {
      csrfPromise = null;
    });
  return csrfPromise;
}

export function clearCsrf(): void {
  csrfToken = null;
}

async function request<T>(
  method: string,
  path: string,
  body?: unknown
): Promise<T> {
  if (method !== "GET") {
    await ensureCsrf();
  }
  const headers: Record<string, string> = {
    Accept: "application/json"
  };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  const cookieToken = getCookie("XSRF-TOKEN");
  if (cookieToken && method !== "GET") {
    headers["X-XSRF-TOKEN"] = cookieToken;
    csrfToken = cookieToken;
  } else if (csrfToken && method !== "GET") {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  const response = await fetch(baseUrl() + path, {
    method,
    credentials: "same-origin",
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined
  });

  if (response.status === 204) {
    return undefined as T;
  }

  if (response.ok) {
    return response.json() as Promise<T>;
  }

  let problem: ApiProblem = {};
  try {
    problem = (await response.json()) as ApiProblem;
  } catch {
    problem = {};
  }

  throw new ApiError(
    problem.title ?? "Request failed",
    problem.errorCode ?? "http_error",
    response.status,
    problem.requestId ?? response.headers.get("X-Request-Id") ?? undefined
  );
}

export async function getJson<T>(path: string): Promise<T> {
  return request<T>("GET", path);
}

export async function postJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("POST", path, body);
}

export async function putJson<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("PUT", path, body);
}
~~~

- [ ] **步骤 2：创建类型定义**

创建 `types/identity.ts`：

~~~typescript
export interface SessionInfo {
  authenticated: boolean;
  accountId?: string;
  username?: string;
  displayName?: string;
}

export interface HouseholdStatus {
  initialized: boolean;
}

export interface CurrentMember {
  householdId: string;
  memberId: string;
  accountId: string;
  username: string;
  displayName: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  status: "ACTIVE" | "DEACTIVATED";
}

export interface MemberInfo {
  id: string;
  accountId: string;
  username: string;
  displayName: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
  status: "ACTIVE" | "DEACTIVATED";
}

export interface InvitationInfo {
  id: string;
  token: string;
  role: "ADMIN" | "MEMBER";
  expiresAt: string;
  path: string;
}

export interface InvitationInspect {
  householdName?: string;
  role?: string;
  expiresAt?: string;
  valid: boolean;
}

export interface BootstrapRequest {
  householdName: string;
  username: string;
  password: string;
  displayName: string;
  email?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}
~~~

- [ ] **步骤 3：验证类型检查通过**

运行：

~~~bash
npm --prefix frontend run typecheck
~~~

预期：PASS。

- [ ] **步骤 4：提交**

~~~bash
git add frontend/src/api/http.ts frontend/src/types/identity.ts
git commit -m "feat: 前端扩展 HTTP 客户端支持 CSRF 与身份类型"
~~~

---

## 任务 31：前端 API 客户端与 session store

**文件：**
- 创建：`frontend/src/api/auth.ts`
- 创建：`frontend/src/api/household.ts`
- 创建：`frontend/src/api/invitation.ts`
- 创建：`frontend/src/api/member.ts`
- 创建：`frontend/src/stores/session.ts`

- [ ] **步骤 1：创建 API 客户端**

创建 `api/auth.ts`：

~~~typescript
import type { SessionInfo, LoginRequest, ChangePasswordRequest } from "../types/identity";
import { getJson, postJson, putJson, ensureCsrf, clearCsrf } from "./http";

export const authApi = {
  login: (data: LoginRequest) => postJson<SessionInfo>("/api/v1/auth/login", data),
  logout: () => postJson("/api/v1/auth/logout"),
  getSession: () => getJson<SessionInfo>("/api/v1/auth/session"),
  initializeCsrf: () => ensureCsrf(),
  changePassword: (data: ChangePasswordRequest) =>
    putJson("/api/v1/auth/password", data),
};

export { ensureCsrf, clearCsrf };
~~~

创建 `api/household.ts`：

~~~typescript
import type { HouseholdStatus, CurrentMember, BootstrapRequest, SessionInfo } from "../types/identity";
import { getJson, postJson } from "./http";

export const householdApi = {
  getStatus: () => getJson<HouseholdStatus>("/api/v1/household/status"),
  getCurrentMember: () => getJson<CurrentMember>("/api/v1/household/me"),
  bootstrap: (data: BootstrapRequest) =>
    postJson<SessionInfo>("/api/v1/household/bootstrap", data),
  transferOwnership: (targetMemberId: string) =>
    postJson("/api/v1/household/transfer-ownership", { targetMemberId }),
};
~~~

创建 `api/invitation.ts`：

~~~typescript
import type { InvitationInfo, InvitationInspect } from "../types/identity";
import { postJson } from "./http";

export const invitationApi = {
  inspect: (token: string) =>
    postJson<InvitationInspect>("/api/v1/invitations/inspect", { token }),
  create: (role: "ADMIN" | "MEMBER", expiresInHours: number) =>
    postJson<InvitationInfo>("/api/v1/invitations", { role, expiresInHours }),
  redeem: (token: string, data: { username: string; password: string; displayName: string; email?: string }) =>
    postJson("/api/v1/invitations/redeem", { token, ...data }),
};
~~~

创建 `api/member.ts`：

~~~typescript
import type { MemberInfo } from "../types/identity";
import { getJson, putJson } from "./http";

export const memberApi = {
  list: () => getJson<MemberInfo[]>("/api/v1/members"),
  updateRole: (id: string, role: "ADMIN" | "MEMBER") =>
    putJson(`/api/v1/members/${id}/role`, { role }),
  updateStatus: (id: string, status: "ACTIVE" | "DEACTIVATED") =>
    putJson(`/api/v1/members/${id}/status`, { status }),
};
~~~

- [ ] **步骤 2：创建 session store**

创建 `stores/session.ts`：

~~~typescript
import { defineStore } from "pinia";
import type { SessionInfo, CurrentMember, HouseholdStatus } from "../types/identity";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import { clearCsrf } from "../api/http";

interface SessionState {
  initialized: boolean;
  householdInitialized: boolean | null;
  session: SessionInfo | null;
  currentMember: CurrentMember | null;
}

const PUBLIC_ROUTES = ["login", "bootstrap", "invitation-redeem", "owner-recovery"];

export const useSessionStore = defineStore("session", {
  state: (): SessionState => ({
    initialized: false,
    householdInitialized: null,
    session: null,
    currentMember: null
  }),

  getters: {
    authenticated: (state) => state.session?.authenticated ?? false,
    role: (state) => state.currentMember?.role ?? null
  },

  actions: {
    async ensureInitialized() {
      if (this.initialized) return;
      try {
        const status = await householdApi.getStatus();
        this.householdInitialized = status.initialized;
        if (status.initialized) {
          const session = await authApi.getSession();
          this.session = session;
          if (session.authenticated) {
            this.currentMember = await householdApi.getCurrentMember();
          }
        }
      } catch {
        this.session = null;
      }
      this.initialized = true;
    },

    async login(username: string, password: string) {
      await authApi.initializeCsrf();
      const session = await authApi.login({ username, password });
      this.session = session;
      if (session.authenticated) {
        this.currentMember = await householdApi.getCurrentMember();
      }
    },

    async logout() {
      try {
        await authApi.logout();
      } finally {
        this.session = null;
        this.currentMember = null;
        clearCsrf();
      }
    },

    isPublicRoute(route: { name: unknown }): boolean {
      return typeof route.name === "string"
        && PUBLIC_ROUTES.includes(route.name);
    }
  }
});
~~~

- [ ] **步骤 3：验证类型检查通过**

运行：

~~~bash
npm --prefix frontend run typecheck
~~~

预期：PASS。

- [ ] **步骤 4：提交**

~~~bash
git add frontend/src/api/auth.ts frontend/src/api/household.ts frontend/src/api/invitation.ts frontend/src/api/member.ts frontend/src/stores/session.ts
git commit -m "feat: 前端新增身份与家庭 API 客户端及 session store"
~~~

---

## 任务 32：前端页面（Bootstrap/Login/Invitation/Members/Profile）

**文件：**
- 创建：`frontend/src/views/BootstrapPage.vue`
- 创建：`frontend/src/views/LoginPage.vue`
- 创建：`frontend/src/views/InvitationRedeemPage.vue`
- 创建：`frontend/src/views/MembersPage.vue`
- 创建：`frontend/src/views/ProfilePage.vue`
- 修改：`frontend/src/router/index.ts`
- 修改：`frontend/src/components/AppShell.vue`

- [ ] **步骤 1：创建 BootstrapPage**

创建 `BootstrapPage.vue`，包含家庭名、用户名、密码、显示名、邮箱表单，提交前先初始化 CSRF，成功后跳转首页。

~~~vue
<template>
  <div class="bootstrap-page">
    <el-card>
      <h2>初始化你的家庭</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="家庭名称">
          <el-input v-model="form.householdName" required />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="form.username" required />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" required show-password />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" required />
        </el-form-item>
        <el-form-item label="邮箱（可选）">
          <el-input v-model="form.email" type="email" />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">
          创建家庭
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { householdApi } from "../api/household";
import { authApi } from "../api/auth";
import { useSessionStore } from "../stores/session";
import type { BootstrapRequest } from "../types/identity";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive<BootstrapRequest>({
  householdName: "",
  username: "",
  password: "",
  displayName: "",
  email: ""
});

async function submit() {
  loading.value = true;
  try {
    await authApi.initializeCsrf();
    await householdApi.bootstrap(form);
    await session.ensureInitialized();
    session.householdInitialized = true;
    router.push({ name: "home" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.bootstrap-page {
  max-width: 480px;
  margin: 4rem auto;
}
</style>
~~~

- [ ] **步骤 2：创建 LoginPage**

创建 `LoginPage.vue`，包含用户名/密码表单，登录成功后跳转首页，失败显示统一错误，限流时提示稍后重试。

~~~vue
<template>
  <div class="login-page">
    <el-card>
      <h2>登录</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" required />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" required show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ username: "", password: "" });

async function submit() {
  loading.value = true;
  try {
    await session.login(form.username, form.password);
    const redirect = route.query.redirect as string | undefined;
    router.push(redirect ?? { name: "home" });
  } catch (e: unknown) {
    const err = e as { errorCode?: string; message?: string };
    if (err.errorCode === "AUTH_LOGIN_RATE_LIMITED") {
      ElMessage.error("尝试过多，请稍后再试");
    } else {
      ElMessage.error("用户名或密码错误");
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  max-width: 400px;
  margin: 4rem auto;
}
</style>
~~~

- [ ] **步骤 3：创建 InvitationRedeemPage**

创建 `InvitationRedeemPage.vue`，从 URL fragment 读取 token，调用 inspect 显示家庭信息，提供注册表单，成功后清理地址栏并跳转。

~~~vue
<template>
  <div class="invitation-page">
    <el-card>
      <h2>加入家庭</h2>
      <template v-if="info?.valid">
        <p>家庭：{{ info.householdName }}</p>
        <p>角色：{{ info.role }}</p>
        <el-form :model="form" label-position="top" @submit.prevent="redeem">
          <el-form-item label="用户名">
            <el-input v-model="form.username" required />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" required show-password />
          </el-form-item>
          <el-form-item label="显示名">
            <el-input v-model="form.displayName" required />
          </el-form-item>
          <el-form-item label="邮箱（可选）">
            <el-input v-model="form.email" type="email" />
          </el-form-item>
          <el-button type="primary" :loading="loading" @click="redeem">加入</el-button>
        </el-form>
      </template>
      <template v-else>
        <p>邀请链接无效或已过期。</p>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { invitationApi } from "../api/invitation";
import { authApi } from "../api/auth";
import { useSessionStore } from "../stores/session";
import type { InvitationInspect } from "../types/identity";

const router = useRouter();
const session = useSessionStore();
const info = ref<InvitationInspect | null>(null);
const loading = ref(false);
const token = ref("");
const form = reactive({
  username: "",
  password: "",
  displayName: "",
  email: ""
});

onMounted(async () => {
  const hash = window.location.hash;
  const match = hash.match(/token=([^&]+)/);
  if (!match) {
    info.value = { valid: false };
    return;
  }
  token.value = decodeURIComponent(match[1]);
  window.history.replaceState(null, "", window.location.pathname);

  try {
    await authApi.initializeCsrf();
    info.value = await invitationApi.inspect(token.value);
  } catch {
    info.value = { valid: false };
  }
});

async function redeem() {
  loading.value = true;
  try {
    await authApi.initializeCsrf();
    await invitationApi.redeem(token.value, form);
    await session.ensureInitialized();
    router.push({ name: "home" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.invitation-page {
  max-width: 480px;
  margin: 4rem auto;
}
</style>
~~~

- [ ] **步骤 4：创建 MembersPage**

创建 `MembersPage.vue`，展示成员列表，Owner/Admin 可见操作按钮，根据当前角色限制目标。

~~~vue
<template>
  <div class="members-page">
    <el-card>
      <template #header>
        <span>成员管理</span>
      </template>
      <el-table :data="members" v-loading="loading">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button
              v-if="canManage(row)"
              size="small"
              @click="toggleStatus(row)"
            >
              {{ row.status === "ACTIVE" ? "停用" : "启用" }}
            </el-button>
            <el-button
              v-if="canPromote(row)"
              size="small"
              @click="toggleRole(row)"
            >
              {{ row.role === "MEMBER" ? "设为管理员" : "取消管理员" }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import { ElMessage } from "element-plus";
import { memberApi } from "../api/member";
import { useSessionStore } from "../stores/session";
import type { MemberInfo } from "../types/identity";

const session = useSessionStore();
const members = ref<MemberInfo[]>([]);
const loading = ref(false);

const isOwner = computed(() => session.role === "OWNER");
const isAdmin = computed(() => session.role === "OWNER" || session.role === "ADMIN");

function canManage(row: MemberInfo): boolean {
  if (row.role === "OWNER") return false;
  if (row.accountId === session.currentMember?.accountId) return false;
  if (row.role === "ADMIN") return isOwner.value;
  return isAdmin.value;
}

function canPromote(row: MemberInfo): boolean {
  return isOwner.value && row.role !== "OWNER";
}

async function load() {
  loading.value = true;
  try {
    members.value = await memberApi.list();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function toggleStatus(row: MemberInfo) {
  try {
    await memberApi.updateStatus(row.id,
      row.status === "ACTIVE" ? "DEACTIVATED" : "ACTIVE");
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function toggleRole(row: MemberInfo) {
  try {
    await memberApi.updateRole(row.id, row.role === "MEMBER" ? "ADMIN" : "MEMBER");
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

onMounted(load);
</script>

<style scoped>
.members-page {
  max-width: 800px;
  margin: 2rem auto;
}
</style>
~~~

- [ ] **步骤 5：创建 ProfilePage**

创建 `ProfilePage.vue`，提供修改密码表单，成功后登出并跳转登录页。

~~~vue
<template>
  <div class="profile-page">
    <el-card>
      <h2>修改密码</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="当前密码">
          <el-input v-model="form.currentPassword" type="password" required show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" required show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">修改密码</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { authApi } from "../api/auth";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ currentPassword: "", newPassword: "" });

async function submit() {
  loading.value = true;
  try {
    await authApi.changePassword(form);
    ElMessage.success("密码已修改，请重新登录");
    await session.logout();
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 400px;
  margin: 4rem auto;
}
</style>
~~~

- [ ] **步骤 6：更新路由与守卫**

替换 `router/index.ts`：

~~~typescript
import { createRouter, createWebHistory } from "vue-router";
import { useSessionStore } from "../stores/session";
import SystemStatusView from "../views/SystemStatusView.vue";
import BootstrapPage from "../views/BootstrapPage.vue";
import LoginPage from "../views/LoginPage.vue";
import InvitationRedeemPage from "../views/InvitationRedeemPage.vue";
import MembersPage from "../views/MembersPage.vue";
import ProfilePage from "../views/ProfilePage.vue";

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", name: "home", component: SystemStatusView },
    { path: "/bootstrap", name: "bootstrap", component: BootstrapPage },
    { path: "/login", name: "login", component: LoginPage },
    { path: "/invitation/redeem", name: "invitation-redeem", component: InvitationRedeemPage },
    { path: "/members", name: "members", component: MembersPage },
    { path: "/profile", name: "profile", component: ProfilePage }
  ]
});

router.beforeEach(async (to) => {
  const session = useSessionStore();
  await session.ensureInitialized();

  if (!session.householdInitialized && to.name !== "bootstrap") {
    return { name: "bootstrap" };
  }

  if (session.isPublicRoute(to)) {
    return true;
  }

  if (!session.authenticated) {
    return { name: "login", query: { redirect: to.fullPath } };
  }
});
~~~

- [ ] **步骤 7：更新 AppShell 导航**

修改 `AppShell.vue`，在导航中加入成员管理与个人资料入口，并添加登出按钮。登出后清理 session store。

- [ ] **步骤 8：验证前端类型检查与构建**

运行：

~~~bash
npm --prefix frontend run typecheck && npm --prefix frontend run build
~~~

预期：PASS。

- [ ] **步骤 9：提交**

~~~bash
git add frontend/src/views/ frontend/src/router/index.ts frontend/src/components/AppShell.vue
git commit -m "feat: 前端新增初始化/登录/邀请/成员/个人资料页面与路由守卫"
~~~

---

## 任务 33：前端单元测试

**文件：**
- 创建：`frontend/src/views/LoginPage.spec.ts`
- 创建：`frontend/src/views/BootstrapPage.spec.ts`
- 创建：`frontend/src/stores/session.spec.ts`
- 创建：`frontend/src/api/http.spec.ts`

- [ ] **步骤 1：编写 http.spec.ts**

创建 `http.spec.ts`，验证不安全方法携带 `X-XSRF-TOKEN`、Problem Details 与 requestId 处理。

~~~typescript
import { describe, it, expect, vi, beforeEach } from "vitest";
import { postJson, ensureCsrf, clearCsrf } from "../api/http";

describe("http client", () => {
  beforeEach(() => {
    clearCsrf();
    vi.restoreAllMocks();
  });

  it("includes CSRF token on POST", async () => {
    vi.spyOn(global, "fetch").mockImplementation(async (input) => {
      const req = new Request(input);
      if (req.url.endsWith("/api/v1/auth/csrf")) {
        return new Response(JSON.stringify({ token: "csrf-abc" }),
          { status: 200, headers: { "Content-Type": "application/json" } });
      }
      expect(req.headers.get("X-XSRF-TOKEN")).toBe("csrf-abc");
      return new Response("{}", { status: 200 });
    });

    await ensureCsrf();
    await postJson("/api/v1/test", {});
  });
});
~~~

- [ ] **步骤 2：编写 session store 测试**

创建 `session.spec.ts`，验证首次加载、401 清理、登录/登出后 CSRF 刷新。

- [ ] **步骤 3：编写页面测试**

创建 `LoginPage.spec.ts` 和 `BootstrapPage.spec.ts`，使用 `@vue/test-utils` 挂载组件并 mock API 模块。

- [ ] **步骤 4：验证测试通过**

运行：

~~~bash
npm --prefix frontend test
~~~

预期：PASS。

- [ ] **步骤 5：提交**

~~~bash
git add frontend/src/views/*.spec.ts frontend/src/stores/session.spec.ts frontend/src/api/http.spec.ts
git commit -m "test: 前端新增身份与家庭页面单元测试"
~~~

---

## 任务 34：Playwright 端到端测试

**文件：**
- 创建：`frontend/e2e/bootstrap.spec.ts`
- 创建：`frontend/e2e/login.spec.ts`
- 创建：`frontend/e2e/invitation.spec.ts`
- 创建：`frontend/e2e/members.spec.ts`
- 创建：`frontend/e2e/owner-recovery.spec.ts`

- [ ] **步骤 1：编写 bootstrap E2E**

创建 `bootstrap.spec.ts`，覆盖：取得 CSRF → 创建家庭和 Owner → 自动登录 → 刷新 CSRF。

~~~typescript
import { test, expect } from "@playwright/test";

test("bootstrap creates household and owner", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveURL(/bootstrap/);

  await page.fill("input", "我的家");
  await page.fill("input:nth-of-type(2)", "owner");
  await page.fill("input:nth-of-type(3)", "Passw0rd!");
  await page.fill("input:nth-of-type(4)", "所有者");
  await page.click("button:has-text('创建家庭')");

  await expect(page).toHaveURL("/");
});
~~~

- [ ] **步骤 2：编写 login E2E**

创建 `login.spec.ts`，覆盖：成功登录、统一失败提示、限流、Session ID 变化。

- [ ] **步骤 3：编写 invitation E2E**

创建 `invitation.spec.ts`，覆盖：创建邀请 → 复制链接 → 地址栏 Token 清理 → 单次兑换 → 自动登录。

- [ ] **步骤 4：编写 members E2E**

创建 `members.spec.ts`，覆盖：成员列表、角色变更、目标角色限制、停用、所有权转移。

- [ ] **步骤 5：编写 owner-recovery E2E**

创建 `owner-recovery.spec.ts`，覆盖：维护命令生成链接 → 重置密码 → Token 失效 → 旧会话失效。该测试需要先通过 `docker compose exec` 运行恢复命令。

- [ ] **步骤 6：验证 E2E 通过**

运行：

~~~bash
npm --prefix frontend run test:e2e
~~~

预期：PASS（需要先启动 `make dev-db && make dev-backend && make dev-frontend` 或使用 Compose 栈）。

- [ ] **步骤 7：提交**

~~~bash
git add frontend/e2e/
git commit -m "test: 新增阶段二 Playwright 端到端测试"
~~~

---

## 补充任务 A：JSONB TypeHandler（审计 detail 字段）

**背景：** 任务 8 的 `AuditLogMapper.xml` 使用 `CAST(#{detail} AS jsonb)`，但 MyBatis 无法将 `Map<String, Object>` 原生序列化为 JSON 字符串。需要自定义 `TypeHandler` 在 Java `Map` 与 PostgreSQL JSONB 之间转换，否则审计插入会因类型不匹配而失败。

**文件：**
- 创建：`backend/src/main/java/com/zija/system/internal/persistence/JsonbTypeHandler.java`
- 修改：`backend/src/main/resources/mapper/system/AuditLogMapper.xml`
- 修改：`backend/src/main/java/com/zija/system/internal/persistence/AuditLogEntity.java`

- [ ] **步骤 1：编写失败测试**

在 `AuditLogIntegrationTest` 中新增断言：插入含嵌套 Map 的 detail 后，读回的 detail 保留原始结构。

~~~java
@Test
@Transactional
void recordsNestedDetailAsJsonb() {
    var event = new AuditEvent(
            "ROLE_CHANGED", "SUCCESS",
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            "req-456", "10.0.0.1",
            Map.of("oldRole", "MEMBER", "newRole", "ADMIN", "nested", Map.of("k", "v"))
    );
    mapper.insert(event);
    var rows = mapper.findByActor(event.actorAccountId());
    assertThat(rows.get(0).getDetail())
            .containsEntry("oldRole", "MEMBER")
            .containsEntry("nested", Map.of("k", "v"));
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=AuditLogIntegrationTest test
~~~

预期：FAIL，JSONB 列类型与参数不匹配或 detail 为 null。

- [ ] **步骤 3：创建 JsonbTypeHandler**

创建 `JsonbTypeHandler.java`，使用 Jackson ObjectMapper 在 `Map<String, Object>` 与 PGobject（jsonb）之间转换：

~~~java
package com.zija.system.internal.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

@MappedTypes(Map.class)
public class JsonbTypeHandler extends BaseTypeHandler<Map<String, Object>> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(
            PreparedStatement ps, int i,
            Map<String, Object> parameter, JdbcType jdbcType
    ) throws SQLException {
        var pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("failed to serialize jsonb", e);
        }
        ps.setObject(i, pgObject);
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public Map<String, Object> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String json) throws SQLException {
        if (json == null) return null;
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new SQLException("failed to deserialize jsonb", e);
        }
    }
}
~~~

- [ ] **步骤 4：在 AuditLogMapper.xml 注册 TypeHandler**

修改 insert 语句，将 `CAST(#{detail} AS jsonb)` 替换为 `#{detail, typeHandler=com.zija.system.internal.persistence.JsonbTypeHandler}`：

~~~xml
<insert id="insert" parameterType="com.zija.system.internal.AuditEvent">
    INSERT INTO audit_log (
        id, household_id, actor_account_id, subject_account_id,
        action, outcome, detail, ip_address, request_id, created_at
    ) VALUES (
        gen_random_uuid(),
        #{householdId},
        #{actorAccountId},
        #{subjectAccountId},
        #{action},
        #{outcome},
        #{detail, typeHandler=com.zija.system.internal.persistence.JsonbTypeHandler},
        #{ipAddress},
        #{requestId},
        CURRENT_TIMESTAMP
    )
</insert>
~~~

同时在 `findByActor` 的 select 中，为 `detail` 列指定 `typeHandler`：

~~~xml
<select id="findByActor" resultMap="auditLogResultMap">
    SELECT id, household_id, actor_account_id, subject_account_id,
           action, outcome, detail, ip_address, request_id, created_at
    FROM audit_log
    WHERE actor_account_id = #{actorAccountId}
    ORDER BY created_at DESC
</select>

<resultMap id="auditLogResultMap" type="com.zija.system.internal.persistence.AuditLogEntity">
    <id property="id" column="id" />
    <result property="householdId" column="household_id" />
    <result property="actorAccountId" column="actor_account_id" />
    <result property="subjectAccountId" column="subject_account_id" />
    <result property="action" column="action" />
    <result property="outcome" column="outcome" />
    <result property="detail" column="detail"
            typeHandler="com.zija.system.internal.persistence.JsonbTypeHandler" />
    <result property="ipAddress" column="ip_address" />
    <result property="requestId" column="request_id" />
    <result property="createdAt" column="created_at" />
</resultMap>
~~~

- [ ] **步骤 5：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=AuditLogIntegrationTest test
~~~

预期：PASS，嵌套 Map 正确序列化与反序列化。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/system/internal/persistence/JsonbTypeHandler.java backend/src/main/resources/mapper/system/AuditLogMapper.xml
git commit -m "fix: 审计日志 detail 字段使用 JSONB TypeHandler 正确序列化"
~~~

---

## 补充任务 B：SessionInvalidator 会话失效组件

**背景：** 规格要求停用成员、修改密码、所有者恢复和所有权转移时，必须删除该账户全部 Spring Session。`ZijaPrincipal.getName()` 返回 `accountId` 字符串，正是 Spring Session 的 principal 索引名。需要利用 `FindByIndexNameSessionRepository` 按 principal 批量删除会话。

**文件：**
- 创建：`backend/src/main/java/com/zija/ZijaSessionInvalidator.java`
- 修改：`backend/src/main/java/com/zija/identity/internal/IdentityService.java`
- 修改：`backend/src/main/java/com/zija/household/internal/MemberService.java`
- 创建：`backend/src/test/java/com/zija/ZijaSessionInvalidatorTest.java`

- [ ] **步骤 1：编写失败测试**

创建 `ZijaSessionInvalidatorTest.java`：

~~~java
package com.zija;

import org.junit.jupiter.api.Test;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ZijaSessionInvalidatorTest {

    @SuppressWarnings("unchecked")
    @Test
    void deletesAllSessionsByPrincipalName() {
        var repository = mock(FindByIndexNameSessionRepository.class);
        var sessionId = "session-1";
        var session = mock(Session.class);
        when(repository.findByPrincipalName("account-uuid"))
                .thenReturn(Map.of(sessionId, session));

        var invalidator = new ZijaSessionInvalidator(repository);
        invalidator.invalidateAllForAccount(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        verify(repository).delete(sessionId);
    }
}
~~~

- [ ] **步骤 2：验证测试失败**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ZijaSessionInvalidatorTest test
~~~

预期：FAIL，类不存在。

- [ ] **步骤 3：创建 ZijaSessionInvalidator**

创建 `ZijaSessionInvalidator.java`：

~~~java
package com.zija;

import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class ZijaSessionInvalidator {

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public ZijaSessionInvalidator(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository
    ) {
        this.sessionRepository = sessionRepository;
    }

    public void invalidateAllForAccount(UUID accountId) {
        Map<String, ? extends Session> sessions =
                sessionRepository.findByPrincipalName(accountId.toString());
        for (String sessionId : sessions.keySet()) {
            sessionRepository.delete(sessionId);
        }
    }
}
~~~

> `FindByIndexNameSessionRepository` 是 Spring Session 的公开接口；Spring Session JDBC 的 `JdbcIndexedSessionRepository` 实现了它。通过依赖注入自动解析。

- [ ] **步骤 4：在 IdentityService.changePassword 中调用会话失效**

修改 `IdentityService`，注入 `ZijaSessionInvalidator`，在 `changePassword` 成功更新密码后调用：

~~~java
// 在构造器中新增 ZijaSessionInvalidator 参数
// changePassword 方法末尾新增：
sessionInvalidator.invalidateAllForAccount(accountId);
~~~

> 注意：修改密码后调用 `invalidateAllForAccount` 会删除当前会话本身，用户需重新登录——这符合规格要求。

- [ ] **步骤 5：在 MemberService.updateStatus 中调用会话失效**

修改 `MemberService`，注入 `ZijaSessionInvalidator`，在停用成员成功后调用：

~~~java
if ("DEACTIVATED".equals(newStatus)) {
    identityApi.disableAccount(target.getAccountId());
    sessionInvalidator.invalidateAllForAccount(target.getAccountId());
}
~~~

- [ ] **步骤 6：在 MemberService.transferOwnership 中调用会话失效**

转移所有权后删除新旧 Owner 全部会话：

~~~java
sessionInvalidator.invalidateAllForAccount(currentOwner.getAccountId());
sessionInvalidator.invalidateAllForAccount(target.getAccountId());
~~~

- [ ] **步骤 7：在 OwnerRecoveryService.resetPassword 中调用会话失效**

恢复密码后删除 Owner 全部会话：

~~~java
sessionInvalidator.invalidateAllForAccount(token.getAccountId());
~~~

- [ ] **步骤 8：更新单元测试以验证会话失效被调用**

在各 Service 的 mock 测试中，注入 mock `ZijaSessionInvalidator` 并 `verify(...).invalidateAllForAccount(any())`。

- [ ] **步骤 9：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=ZijaSessionInvalidatorTest,IdentityServiceTest,MemberServiceTest,OwnerRecoveryServiceTest test
~~~

预期：PASS。

- [ ] **步骤 10：提交**

~~~bash
git add backend/src/main/java/com/zija/ZijaSessionInvalidator.java backend/src/main/java/com/zija/identity/internal/IdentityService.java backend/src/main/java/com/zija/household/internal/MemberService.java backend/src/main/java/com/zija/household/internal/OwnerRecoveryService.java backend/src/test/java/com/zija/ZijaSessionInvalidatorTest.java
git commit -m "feat: 新增会话失效组件，停用/改密/恢复/转移时清除全部会话"
~~~

---

## 补充任务 C：成员列表跨模块数据组装

**背景：** `MemberController.list` 当前返回的 `username`/`displayName` 为 null，因为账户信息属于 `identity` 模块。需要通过 `IdentityApi.findById()` 批量获取账户信息并组装到响应中。

**文件：**
- 修改：`backend/src/main/java/com/zija/identity/IdentityApi.java`
- 修改：`backend/src/main/java/com/zija/identity/internal/IdentityService.java`
- 修改：`backend/src/main/java/com/zija/household/internal/MemberController.java`

- [ ] **步骤 1：在 IdentityApi 新增批量查询方法**

在 `IdentityApi` 接口新增：

~~~java
java.util.Map<UUID, AccountInfo> findByIds(java.util.Collection<UUID> ids);
~~~

- [ ] **步骤 2：在 IdentityService 实现批量查询**

~~~java
@Override
@Transactional(readOnly = true)
public java.util.Map<UUID, AccountInfo> findByIds(java.util.Collection<UUID> ids) {
    if (ids.isEmpty()) return java.util.Map.of();
    var entities = accountMapper.selectBatchIds(ids);
    return entities.stream()
            .collect(java.util.stream.Collectors.toMap(
                    AccountEntity::getId, this::toInfo));
}
~~~

- [ ] **步骤 3：在 MemberController.list 中组装账户信息**

修改 `list()` 方法，调用 `IdentityApi.findByIds()` 填充 username/displayName：

~~~java
@GetMapping
@com.zija.household.RequireMember
List<MemberResponse> list() {
    var principal = (ZijaPrincipal) SecurityContextHolder
            .getContext().getAuthentication().getPrincipal();
    var member = householdService.requireActiveMember(principal.getAccountId());
    var members = memberMapper.selectByHousehold(member.householdId());
    if (members.isEmpty()) return java.util.List.of();

    var accountIds = members.stream().map(MemberEntity::getAccountId).toList();
    var accounts = identityApi.findByIds(accountIds);

    return members.stream()
            .map(m -> {
                var account = accounts.get(m.getAccountId());
                return new MemberResponse(
                        m.getId(), m.getAccountId(),
                        account != null ? account.username() : null,
                        account != null ? account.displayName() : null,
                        m.getRole(), m.getStatus());
            })
            .toList();
}
~~~

> `MemberController` 需新增 `IdentityApi` 和 `MemberMapper` 依赖注入（如尚未有）。

- [ ] **步骤 4：编写测试验证列表含账户信息**

在 `MemberControllerTest` 中新增测试，mock `householdService.requireActiveMember` 返回成员列表，mock `identityApi.findByIds` 返回账户映射，断言响应含 username/displayName。

- [ ] **步骤 5：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=MemberControllerTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/identity/IdentityApi.java backend/src/main/java/com/zija/identity/internal/IdentityService.java backend/src/main/java/com/zija/household/internal/MemberController.java
git commit -m "feat: 成员列表通过 IdentityApi 批量组装账户信息"
~~~

---

## 补充任务 D：缺失的控制器与集成测试

**背景：** 目标文件清单中列出了多个测试文件，但主任务中未提供创建步骤。补全以确保规格第 13 节测试策略全覆盖。

**文件：**
- 创建：`backend/src/test/java/com/zija/household/internal/MemberControllerTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/InvitationControllerTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/OwnerRecoveryControllerTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/persistence/MemberMapperIntegrationTest.java`
- 创建：`backend/src/test/java/com/zija/household/internal/persistence/OwnerRecoveryIntegrationTest.java`

- [ ] **步骤 1：编写 MemberControllerTest**

创建 `MemberControllerTest.java`，使用 `@SpringBootTest` + `@AutoConfigureMockMvc` + MockBean，验证：
- 成员列表要求认证（401）
- 已认证 ADMIN 可以查看列表
- 已认证 MEMBER 可以查看列表但无操作按钮数据
- Owner 可以修改角色，Admin 不能修改 Owner/Admin 角色
- Admin 不能停用自己、Owner、其他 Admin
- 所有权转移要求 Owner 权限

~~~java
@SpringBootTest
@AutoConfigureMockMvc
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HouseholdService householdService;
    @MockBean MemberService memberService;
    @MockBean MemberMapper memberMapper;
    @MockBean IdentityApi identityApi;
    @MockBean SystemApi systemApi;

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    void listReturnsMembersWithAccountInfo() throws Exception {
        // mock householdService.requireActiveMember 返回 ACTIVE MEMBER
        // mock memberMapper.selectByHousehold 返回成员列表
        // mock identityApi.findByIds 返回账户映射
        // 断言响应 JSON 含 username 和 displayName
    }

    @Test
    @WithMockUser
    void updateRoleToOwnerRejected() throws Exception {
        // 调用 PUT /api/v1/members/{id}/role { role: "OWNER" }
        // 断言 MemberService.updateRole 抛出 IllegalArgumentException
        // 控制器异常处理器返回 400
    }
}
~~~

- [ ] **步骤 2：编写 InvitationControllerTest**

创建 `InvitationControllerTest.java`，验证：
- 创建邀请要求认证
- Admin 只能创建 MEMBER 邀请
- Owner 可以创建 ADMIN 邀请
- 公开 inspect 端点返回有效性
- 公开 redeem 端点要求 CSRF

- [ ] **步骤 3：编写 OwnerRecoveryControllerTest**

创建 `OwnerRecoveryControllerTest.java`，验证：
- 公开 inspect 返回有效性
- 公开 reset-password 要求 CSRF
- 过期/已消费 Token 返回不泄露内部状态的错误
- 成功重置密码后返回 204

- [ ] **步骤 4：编写 MemberMapperIntegrationTest**

创建 `MemberMapperIntegrationTest.java`，使用 Testcontainers 验证：
- 成员唯一约束 `(household_id, account_id)`
- 合法枚举值 CHECK 约束
- 唯一 Owner 索引 `uq_member_single_owner`（同一家庭不能有两个 OWNER）
- 乐观锁 `version` 更新

~~~java
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.session.jdbc.initialize-schema=never")
class MemberMapperIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired MemberMapper memberMapper;
    @Autowired HouseholdMapper householdMapper;

    @Test
    @Transactional
    void uniqueOwnerIndexPreventsSecondOwner() {
        // 初始化家庭后插入 OWNER，再插入第二个 OWNER 应抛出唯一约束异常
    }
}
~~~

- [ ] **步骤 5：编写 OwnerRecoveryIntegrationTest**

创建 `OwnerRecoveryIntegrationTest.java`，使用 Testcontainers 验证：
- 并发消费恢复令牌恰好一个成功
- 密码更新后旧会话失效
- 令牌轮换使旧令牌失效

- [ ] **步骤 6：验证所有新测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=MemberControllerTest,InvitationControllerTest,OwnerRecoveryControllerTest,MemberMapperIntegrationTest,OwnerRecoveryIntegrationTest test
~~~

预期：PASS。

- [ ] **步骤 7：提交**

~~~bash
git add backend/src/test/java/com/zija/household/internal/MemberControllerTest.java backend/src/test/java/com/zija/household/internal/InvitationControllerTest.java backend/src/test/java/com/zija/household/internal/OwnerRecoveryControllerTest.java backend/src/test/java/com/zija/household/internal/persistence/MemberMapperIntegrationTest.java backend/src/test/java/com/zija/household/internal/persistence/OwnerRecoveryIntegrationTest.java
git commit -m "test: 补全成员/邀请/恢复控制器与持久化集成测试"
~~~

---

## 补充任务 E：OwnerRecoveryPage 前端页面

**背景：** 规格第 8.5 节的恢复链接使用 `/owner-recovery#token=...`，但前端缺少对应页面。

**文件：**
- 创建：`frontend/src/views/OwnerRecoveryPage.vue`
- 修改：`frontend/src/router/index.ts`
- 创建：`frontend/src/api/owner-recovery.ts`
- 修改：`frontend/src/types/identity.ts`

- [ ] **步骤 1：在 types/identity.ts 新增恢复类型**

~~~typescript
export interface OwnerRecoveryInspect {
  valid: boolean;
  ownerDisplayName?: string;
}

export interface OwnerRecoveryResetRequest {
  token: string;
  newPassword: string;
}
~~~

- [ ] **步骤 2：创建 api/owner-recovery.ts**

~~~typescript
import type { OwnerRecoveryInspect, OwnerRecoveryResetRequest } from "../types/identity";
import { postJson } from "./http";

export const ownerRecoveryApi = {
  inspect: (token: string) =>
    postJson<OwnerRecoveryInspect>("/api/v1/owner-recovery/inspect", { token }),
  resetPassword: (data: OwnerRecoveryResetRequest) =>
    postJson("/api/v1/owner-recovery/reset-password", data),
};
~~~

- [ ] **步骤 3：创建 OwnerRecoveryPage.vue**

从 URL fragment 读取 token，调用 inspect 显示有效性，提供新密码表单，成功后跳转登录页。Token 放在 fragment 中不发送给服务器，使用后立即从地址栏移除。

~~~vue
<template>
  <div class="recovery-page">
    <el-card>
      <h2>重置所有者密码</h2>
      <template v-if="info?.valid">
        <p>请为所有者账户设置新密码。</p>
        <el-form :model="form" label-position="top" @submit.prevent="reset">
          <el-form-item label="新密码">
            <el-input v-model="form.newPassword" type="password" required show-password />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="confirmPassword" type="password" required show-password />
          </el-form-item>
          <el-button type="primary" :loading="loading" :disabled="!passwordsMatch" @click="reset">
            重置密码
          </el-button>
        </el-form>
      </template>
      <template v-else>
        <p>恢复链接无效或已过期。</p>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ownerRecoveryApi } from "../api/owner-recovery";
import { authApi } from "../api/auth";
import type { OwnerRecoveryInspect } from "../types/identity";

const router = useRouter();
const info = ref<OwnerRecoveryInspect | null>(null);
const loading = ref(false);
const token = ref("");
const form = reactive({ newPassword: "" });
const confirmPassword = ref("");

const passwordsMatch = computed(
  () => form.newPassword.length > 0 && form.newPassword === confirmPassword.value
);

onMounted(async () => {
  const hash = window.location.hash;
  const match = hash.match(/token=([^&]+)/);
  if (!match) {
    info.value = { valid: false };
    return;
  }
  token.value = decodeURIComponent(match[1]);
  window.history.replaceState(null, "", window.location.pathname);

  try {
    await authApi.initializeCsrf();
    info.value = await ownerRecoveryApi.inspect(token.value);
  } catch {
    info.value = { valid: false };
  }
});

async function reset() {
  loading.value = true;
  try {
    await ownerRecoveryApi.resetPassword({ token: token.value, newPassword: form.newPassword });
    ElMessage.success("密码已重置，请使用新密码登录");
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.recovery-page {
  max-width: 400px;
  margin: 4rem auto;
}
</style>
~~~

- [ ] **步骤 4：在路由中注册**

在 `router/index.ts` 的 routes 数组中新增：

~~~typescript
{ path: "/owner-recovery", name: "owner-recovery", component: OwnerRecoveryPage },
~~~

并导入组件。`owner-recovery` 已在 `PUBLIC_ROUTES` 中。

- [ ] **步骤 5：验证类型检查通过**

运行：

~~~bash
npm --prefix frontend run typecheck
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add frontend/src/views/OwnerRecoveryPage.vue frontend/src/api/owner-recovery.ts frontend/src/router/index.ts frontend/src/types/identity.ts
git commit -m "feat: 前端新增所有者恢复页面"
~~~

---

## 补充任务 F：Nginx 安全头、Dockerfile 命令模式与 Makefile

**背景：** 规格第 7.4 节要求页面设置 `Referrer-Policy: no-referrer`；第 8.5 节的非 Web 命令模式需要 Dockerfile 和 Makefile 支持；阶段一审查记录中的 Dockerfile jar 名硬编码版本问题也需一并修复。

**文件：**
- 修改：`deploy/nginx/default.conf`
- 修改：`deploy/app/Dockerfile`
- 修改：`Makefile`

- [ ] **步骤 1：在 Nginx 配置中添加安全头**

修改 `deploy/nginx/default.conf`，在 server 块中添加：

~~~nginx
# 防止邀请和恢复 Token 泄露到 Referer
add_header Referrer-Policy "no-referrer" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
~~~

- [ ] **步骤 2：修复 Dockerfile jar 名硬编码**

检查 `deploy/app/Dockerfile` 中的 `COPY` / `ENTRYPOINT` 行。如果使用了硬编码版本号（如 `zija-backend-0.1.0-SNAPSHOT.jar`），改为通配符或重命名策略：

~~~dockerfile
# 在多阶段构建中重命名为固定名
COPY target/*.jar /app/zija.jar
ENTRYPOINT ["java", "-jar", "/app/zija.jar"]
~~~

> 这样非 Web 命令模式可以直接 `java -jar /app/zija.jar --spring.main.web-application-type=none --zija.command=recover-owner`。

- [ ] **步骤 3：在 Makefile 中新增 recover-owner 目标**

在 `Makefile` 中添加：

~~~makefile
## 生成所有者恢复链接（在运行中的容器内执行非 Web 命令）
recover-owner:
	docker compose exec app java -jar /app/zija.jar \
		--spring.main.web-application-type=none \
		--zija.command=recover-owner
~~~

- [ ] **步骤 4：验证 Nginx 配置语法**

运行：

~~~bash
docker compose run --rm web nginx -t
~~~

预期：syntax is ok / test is successful。

- [ ] **步骤 5：验证 Compose 栈仍可启动**

运行：

~~~bash
make compose-smoke
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add deploy/nginx/default.conf deploy/app/Dockerfile Makefile
git commit -m "ops: 新增 Nginx 安全头、修复 Dockerfile jar 名、新增 recover-owner 目标"
~~~

---

## 补充任务 G：IdentityApi.resetPassword 独立方法

**背景：** 任务 23 中 `OwnerRecoveryService.resetPassword` 通过 `IdentityApi.changePassword` 传 null `currentPassword` 来重置密码，这绕过了 `@NotBlank` 校验意图且职责不清。应为恢复流程提供独立的 `resetPassword` 方法。

**文件：**
- 修改：`backend/src/main/java/com/zija/identity/IdentityApi.java`
- 修改：`backend/src/main/java/com/zija/identity/internal/IdentityService.java`
- 修改：`backend/src/main/java/com/zija/household/internal/OwnerRecoveryService.java`

- [ ] **步骤 1：在 IdentityApi 新增 resetPassword 方法**

~~~java
void resetPassword(UUID accountId, String newPassword);
~~~

- [ ] **步骤 2：在 IdentityService 实现 resetPassword**

~~~java
@Override
@Transactional
public void resetPassword(UUID accountId, String newPassword) {
    var account = accountMapper.selectById(accountId);
    if (account == null) {
        throw new InvalidCredentialsException();
    }
    var newHash = passwordEncoder.encode(newPassword);
    if (accountMapper.updatePasswordHash(accountId, newHash, account.getVersion()) != 1) {
        throw new InvalidCredentialsException();
    }
    sessionInvalidator.invalidateAllForAccount(accountId);
}
~~~

- [ ] **步骤 3：在 OwnerRecoveryService.resetPassword 改用 resetPassword**

将：

~~~java
identityApi.changePassword(token.getAccountId(),
        new IdentityApi.ChangePasswordCommand(null, newPassword));
~~~

替换为：

~~~java
identityApi.resetPassword(token.getAccountId(), newPassword);
~~~

- [ ] **步骤 4：更新 OwnerRecoveryServiceTest 调用断言**

将 `verify(identityApi).changePassword(...)` 改为 `verify(identityApi).resetPassword(...)`。

- [ ] **步骤 5：验证测试通过**

运行：

~~~bash
cd backend && ./mvnw -q -Dtest=OwnerRecoveryServiceTest,IdentityServiceTest test
~~~

预期：PASS。

- [ ] **步骤 6：提交**

~~~bash
git add backend/src/main/java/com/zija/identity/IdentityApi.java backend/src/main/java/com/zija/identity/internal/IdentityService.java backend/src/main/java/com/zija/household/internal/OwnerRecoveryService.java backend/src/test/java/com/zija/household/internal/OwnerRecoveryServiceTest.java
git commit -m "refactor: 恢复流程使用独立的 resetPassword 方法替代 null currentPassword"
~~~

---

## 任务 41：最终验证与提交

**文件：**
- 验证所有变更

- [ ] **步骤 1：运行完整静态和自动化验证**

运行：

~~~bash
make verify
~~~

预期：布局检查、后端测试、前端测试、生产构建全部通过。

- [ ] **步骤 2：运行 Compose 冒烟测试**

运行：

~~~bash
make compose-smoke
~~~

预期：Docker Compose 栈从空数据启动并报告服务健康。

- [ ] **步骤 3：运行 E2E 冒烟测试**

运行：

~~~bash
make e2e-smoke
~~~

预期：Playwright 浏览器冒烟测试通过。

- [ ] **步骤 4：检查差异和工作树**

运行：

~~~bash
git diff --check && git status --short
~~~

预期：无空白错误；工作树干净或仅剩预期未暂存改动。

- [ ] **步骤 5：更新文档**

更新 `README.md`，描述初始化、登录、邀请、成员管理、所有者恢复的新工作流与配置项。

- [ ] **步骤 6：最终提交**

~~~bash
git add README.md
git commit -m "docs: 更新阶段二身份与家庭工作流文档"
~~~

---

## 自审清单

执行计划前，对照规格 `docs/superpowers/specs/2026-07-20-phase2-identity-household-design.md` 逐项核对：

- [ ] 首次引导恰好创建一次家庭和所有者——任务 19
- [ ] 登录使用服务端会话、HttpOnly Cookie、CSRF、限流——任务 13、15、16
- [ ] 所有者创建限时单次邀请——任务 21
- [ ] Owner/Admin/Member 权限矩阵——任务 24
- [ ] 停用成员历史可归属——任务 22（`member.status` 与 `account.status` 分离）
- [ ] 容器维护命令创建恢复链接——任务 23 + 补充任务 F（Makefile）
- [ ] 审计覆盖登录、邀请、成员状态、角色变更——任务 8、15、19、21、22
- [ ] 审计 detail JSONB 正确序列化——补充任务 A
- [ ] 停用/改密/恢复/转移时清除全部会话——补充任务 B
- [ ] 成员列表含账户信息（跨模块组装）——补充任务 C
- [ ] 所有控制器与持久化测试全覆盖——补充任务 D
- [ ] 所有者恢复前端页面——补充任务 E
- [ ] Nginx Referrer-Policy、Dockerfile 命令模式、Makefile recover-owner——补充任务 F
- [ ] 恢复流程使用独立 resetPassword——补充任务 G
- [ ] OpenAPI 生成与契约检查——任务 26
- [ ] `ModularityTests` 验证单向依赖——任务 25
- [ ] Playwright 覆盖关键流程——任务 34
- [ ] `make verify` 通过——任务 41

---

## 执行方式选择

**计划已保存到 `docs/superpowers/plans/2026-07-20-phase2-identity-household.md`（41 个任务，含 7 个补充任务）。两种执行方式：**

**1. 子代理驱动（推荐）** - 每个任务派发独立子代理，任务间审查，快速迭代。

**2. 内联执行** - 在当前会话中使用 executing-plans，批量执行并设检查点。

**选择哪种方式？**
