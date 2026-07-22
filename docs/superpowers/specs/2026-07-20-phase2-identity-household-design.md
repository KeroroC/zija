# 阶段二：身份与家庭 设计方案

- **状态：** 待审批
- **日期：** 2026-07-20
- **覆盖规格：** 第 3.1、4、6.1、9 和 10 节
- **交付路线：** 阶段 2（身份与家庭）

## 1. 目标与范围

### 1.1 必须达成的结果

- 首次引导恰好创建一次家庭和所有者
- 用户名/密码登录使用服务端会话、HttpOnly Cookie、CSRF 防护和登录限流
- 所有者创建限时、单次邀请链接，无需 SMTP
- 所有者、管理员和成员权限符合规格中的角色矩阵
- 所有者可以管理 Admin 和普通成员，Admin 只能管理普通成员；历史记录仍可归属于已停用账户
- 容器维护命令创建一次性的所有者恢复链接
- 审计记录覆盖登录、邀请、成员状态和角色变更
- OpenAPI 生成和契约检查覆盖公开的系统、会话、引导、邀请和成员 API

### 1.2 不在范围内

- 物品、分类、品牌、单位、标签（阶段 3）
- 位置层级（阶段 3）
- 批次、库存位、流水（阶段 4）
- 提醒规则和任务（阶段 5）
- 报表和 CSV（阶段 6）
- 移动端令牌认证（v1 之后）

## 2. 架构决策

| 决策 | 选择 | 理由 |
|---|---|---|
| 模块结构 | `identity` + `household`，保持 `household → identity` 单向依赖 | 符合总体设计和 Spring Modulith 边界，避免认证与家庭模块循环依赖 |
| 跨模块编排 | 初始化、邀请兑换由 `household` 调用 `IdentityApi`；登录与会话只返回账户身份，家庭角色通过 `HouseholdApi` 查询 | 不让 `identity` 反向依赖 `household`，也不跨模块访问 `internal` 包 |
| 会话存储 | PostgreSQL（Spring Session JDBC），会话表由 Flyway 管理 | 无需额外基础设施，重启不丢失会话，生产环境不依赖运行时自动建表 |
| 活跃会话 | 允许同一账户存在多个会话；停用成员、修改密码和所有者恢复时清理该账户全部会话 | 支持同一成员在不同设备登录，同时保证安全状态变更立即生效 |
| 密码哈希 | `DelegatingPasswordEncoder`，当前编码使用 BCrypt | 保存 `{id}hash` 格式，为后续提高强度或迁移算法保留升级能力 |
| CSRF | Spring Security SPA 配置，Cookie `XSRF-TOKEN` + Header `X-XSRF-TOKEN` | 与 Vue 同源 SPA 的标准双提交 Cookie 流程一致 |
| OpenAPI 生成 | `springdoc-openapi-starter-webmvc-ui` 的 Spring Boot 4 兼容版本线 | 自动从控制器生成，并通过契约差异检查保护公开 API |
| 所有者恢复 | 容器内非 Web 命令模式 | 不启动第二个 8080 Web 服务，恢复令牌只显示一次且只存摘要 |
| 登录限流 | 单实例、有界、带过期的内存限流 | 当前 Compose 只有一个应用实例，无需 Redis；限制容量和 TTL 防止内存无限增长 |
| 反向代理 | Spring Boot 使用 `server.forward-headers-strategy: native` | 正确识别 Nginx 转发的 HTTPS scheme、客户端来源和 Secure Cookie 条件 |

## 3. 模块结构

### 3.1 identity 模块

```
com.zija.identity/
  IdentityApi.java              # 公开接口：账户注册、查询、密码修改
  package-info.java             # @ApplicationModule
  internal/
    IdentityController.java     # 登录、登出、会话查询、密码修改
    IdentityService.java        # 账户与密码业务逻辑
    ZijaUserDetailsService.java # Spring Security 账户加载
    LoginRateLimiter.java       # 登录限流
    persistence/
      AccountEntity.java        # 账户表
      AccountMapper.java
      AccountMapper.xml
```

**IdentityApi 公开接口：**

- `AccountInfo registerAccount(RegisterAccountCommand command)`
- `Optional<AccountInfo> findById(UUID id)`
- `Optional<AccountInfo> findByNormalizedUsername(String normalizedUsername)`
- `void changePassword(UUID accountId, ChangePasswordCommand command)`
- `void disableAccount(UUID accountId)`
- `void activateAccount(UUID accountId)`
- `void requireActive(UUID accountId)`

登录、登出和会话查询由 `identity` 控制器负责，但响应只包含账户身份，不拼装家庭角色。`SecurityContext` 中的 `ZijaPrincipal` 保存不可变 `accountId`、用户名和显示名，其 `getName()` 返回 `accountId` 字符串，使 Spring Session principal 索引可以稳定地按账户删除全部会话；角色与成员状态由 `household` 在授权时实时查询。

### 3.2 household 模块

```
com.zija.household/
  HouseholdApi.java             # 公开接口：家庭信息、成员列表、角色查询
  package-info.java             # @ApplicationModule
  internal/
    HouseholdController.java    # 初始化、邀请、成员管理
    HouseholdService.java       # 家庭与初始化编排
    InvitationService.java      # 邀请生成与兑换
    MemberService.java          # 成员角色管理
    OwnerRecoveryService.java   # 恢复令牌生成与兑换
    OwnerRecoveryCommand.java   # 仅在非 Web 命令模式运行
    persistence/
      HouseholdEntity.java
      MemberEntity.java
      InvitationEntity.java
      OwnerRecoveryTokenEntity.java
      HouseholdMapper.java
      MemberMapper.java
      InvitationMapper.java
      OwnerRecoveryTokenMapper.java
```

**HouseholdApi 公开接口：**

- `boolean isInitialized()`
- `Optional<HouseholdInfo> findHousehold()`
- `List<MemberInfo> findMembers(UUID householdId)`
- `Optional<MemberInfo> findMember(UUID householdId, UUID accountId)`
- `MemberInfo requireActiveMember(UUID accountId)`
- `boolean hasAtLeastRole(UUID accountId, MemberRole requiredRole)`

应用根包提供共享的 `ZijaSessionAuthenticationSupport` 技术组件，封装 `AuthenticationManager`、session fixation 防护、`SecurityContextRepository` 保存以及重新生成 CSRF Token。`identity` 登录、`household` 初始化和邀请兑换共同使用它；该组件不是新的业务模块，也不包含账户或家庭规则。

### 3.3 模块依赖

```
household → identity（仅通过 IdentityApi，用于初始化和邀请兑换创建账户）
household → system（通过 SystemApi，用于审计）
identity → system（通过 SystemApi，用于审计）
```

禁止 `identity → household`。登录和 `GET /api/v1/auth/session` 不返回角色或家庭信息；前端通过 `GET /api/v1/household/me` 获取当前成员、家庭和角色。`ModularityTests` 必须验证不存在循环依赖和任何跨模块 `internal` 包引用。

## 4. 数据库设计

### 4.1 账户表（identity 模块）

```sql
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
```

用户名在去除首尾空白并按 `Locale.ROOT` 转为小写后写入 `username_normalized`；注册、登录、限流键和唯一性检查统一使用该值。登录失败对“账户不存在”和“密码错误”执行等价的密码哈希成本并返回相同错误，避免账户枚举。

`account.status` 是认证层开关，决定账户能否登录；`member.status` 是家庭领域状态，用于保留成员历史。成员停用或启用事务必须通过 `IdentityApi` 同步更新账户状态，避免停用成员仍能建立新会话。

### 4.2 家庭表（household 模块）

```sql
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
```

`singleton_key = 1` 在数据库层保证一套部署最多只有一个家庭。初始化事务首先尝试插入该单例记录；并发请求由主键/检查约束串行化，失败事务映射为明确的已初始化冲突，不需要访问 `system` 模块的内部表。

### 4.3 成员表（household 模块）

```sql
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
```

数据库保证一个家庭最多一个 `OWNER`；服务层保证初始化完成后始终至少有一个 Owner。Owner 不允许被停用或直接降级，必须通过所有权转移在同一事务中完成新旧 Owner 的角色切换。

### 4.4 邀请表（household 模块）

```sql
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
```

邀请只支持单次使用，因此不引入 `max_uses/used_count`。服务端使用 `SecureRandom` 生成 32 字节原始令牌，只向创建者返回一次；数据库仅保存原始令牌的 SHA-256 十六进制摘要。兑换事务通过摘要查询并锁定邀请行，完成有效性检查、账户创建、成员创建和 `consumed_at/consumed_by` 更新，任一步失败则整体回滚。

### 4.5 所有者恢复令牌表（household 模块）

```sql
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
```

恢复命令只为当前 Owner 生成密码重置令牌，不通过公开流程创建第二个 Owner。创建新令牌时撤销该 Owner 之前尚未消费的令牌；数据库只保存摘要。

### 4.6 审计日志表（system 模块扩展）

```sql
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
```

审计记录不对账户和家庭设置外键，以便停用、恢复或未来归档后仍保留历史。`detail` 使用按事件定义的白名单字段，不得包含密码、密码哈希、会话 ID、CSRF Token、完整邀请令牌或完整恢复令牌。

### 4.7 Spring Session 表

Spring Session JDBC 表作为 Flyway 迁移纳入版本控制，基于所用 Spring Session 版本的 PostgreSQL 官方 schema 创建，并增加按 principal 查询会话所需的索引。生产配置使用 `spring.session.jdbc.initialize-schema=never`，禁止应用启动时自动创建或修改表。

## 5. 认证与会话

### 5.1 登录流程

```
POST /api/v1/auth/login
  ├─ 规范化 username，取得可信客户端 IP
  ├─ 分别检查账户桶和来源 IP 桶
  ├─ AuthenticationManager 验证用户名密码和账户状态
  ├─ 成功时更换 Session ID，保存 SecurityContext
  ├─ 清除账户失败计数并重新生成 CSRF Token
  └─ 审计 LOGIN_SUCCESS / LOGIN_FAILURE
```

**请求体：**
```json
{
  "username": "string",
  "password": "string"
}
```

**成功响应（200）：**
```json
{
  "authenticated": true,
  "accountId": "uuid",
  "username": "string",
  "displayName": "string"
}
```

家庭、成员和角色信息由 `GET /api/v1/household/me` 返回，避免 `identity → household` 反向依赖。

**失败响应（401）：**
```json
{
  "type": "about:blank",
  "title": "用户名或密码错误",
  "status": 401,
  "detail": "用户名或密码错误",
  "errorCode": "AUTH_LOGIN_FAILED",
  "requestId": "uuid"
}
```

不存在的用户名和错误密码返回完全相同的状态、标题和详情，不返回剩余次数。超过限流阈值返回 `429 Too Many Requests`、`AUTH_LOGIN_RATE_LIMITED` 和 `Retry-After`，但仍不透露账户是否存在。

### 5.2 会话管理

- Spring Session JDBC 存储在 PostgreSQL，Cookie 名固定为 `ZIJA_SESSION`
- Cookie 配置：`HttpOnly=true`、`SameSite=Lax`、`Path=/`，生产环境 `Secure=true`
- 默认空闲超时 24 小时；允许同一账户存在多个活跃会话
- 登录成功必须调用 session fixation 防护并保存 `SecurityContext`
- `GET /api/v1/auth/session` 返回账户会话状态，不返回家庭角色
- `POST /api/v1/auth/logout` 只注销当前会话并清除 `ZIJA_SESSION`
- 停用成员、修改密码和所有者恢复成功时，通过 Spring Session 的 principal 索引删除该账户全部会话
- 角色变化在每次受保护业务调用时从 `household` 查询，因此无需等待旧会话过期

手工 REST 登录不能只向 `SecurityContextHolder` 写入认证结果。`ZijaSessionAuthenticationSupport` 必须依次执行认证、调用配置为 `changeSessionId` 的 `SessionAuthenticationStrategy`、创建 `SecurityContext`、调用 `SecurityContextRepository.saveContext(...)`，否则下一请求可能丢失认证或保留登录前的 Session ID。

### 5.3 CSRF 防护

- 使用 Spring Security SPA CSRF 配置：`.csrf(csrf -> csrf.spa())`
- Cookie 名使用 `XSRF-TOKEN`，Header 名使用 `X-XSRF-TOKEN`
- `GET /api/v1/auth/csrf` 必须实际解析 `CsrfToken`，确保延迟 Token 被创建并写入 Cookie
- 所有不安全方法，包括公开的登录、初始化、邀请兑换、恢复密码和登出，都必须携带 CSRF Token
- 登录和登出会清除旧 Token；前端在成功后立即重新调用 CSRF 端点取得新 Token
- 不为公开写端点配置 `ignoringRequestMatchers`，避免登录 CSRF 和初始化 CSRF

### 5.4 登录限流

- 账户桶：同一规范化用户名在滚动 5 分钟内失败 5 次，锁定 5 分钟
- IP 桶：同一可信来源 IP 在滚动 5 分钟内失败 20 次，锁定 5 分钟
- 两个桶分别计算，不能只使用 `username+IP` 组合键，以免攻击者轮换任一维度绕过
- 成功登录清除账户桶；IP 桶按窗口自然过期
- 实现必须设置最大条目数和 TTL 清理，未知用户名同样进入限流，防止内存耗尽
- 当前方案只承诺单应用实例；未来扩展多实例时必须替换为数据库或共享限流存储
- 客户端 IP 只能来自经过信任边界处理后的请求，不直接信任任意 `X-Forwarded-For`

### 5.5 安全配置

```java
@EnableMethodSecurity
@Configuration(proxyBeanMethods = false)
public class ZijaSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                .anyRequest().authenticated()
            )
            .csrf(csrf -> csrf.spa())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId())
            )
            .requestCache(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(zijaAuthenticationEntryPoint)
                .accessDeniedHandler(zijaAccessDeniedHandler)
            )
            .logout(logout -> logout
                .logoutUrl("/api/v1/auth/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("ZIJA_SESSION", "XSRF-TOKEN")
                .logoutSuccessHandler((request, response, auth) -> {
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                })
            )
            .build();
    }
}
```

REST 登录控制器仍须显式调用共享的 session authentication support；上面的 session fixation DSL 主要覆盖由 Spring Security 过滤器触发的认证流程。实现时应把等价的 `SessionAuthenticationStrategy` 注册为可注入 Bean，供手工登录、初始化和邀请兑换共同调用。

`zijaAuthenticationEntryPoint` 和 `zijaAccessDeniedHandler` 返回统一 Problem Details：未认证为 `401 AUTHENTICATION_REQUIRED`，权限不足为 `403 ACCESS_DENIED`，CSRF 失败为稳定的 `403 CSRF_TOKEN_INVALID`；响应包含 `requestId`，不执行 HTML 跳转。

### 5.6 会话与代理配置

基础配置：

```yaml
server:
  forward-headers-strategy: native
  servlet:
    session:
      timeout: 24h
      cookie:
        name: ZIJA_SESSION
        http-only: true
        same-site: lax

spring:
  session:
    jdbc:
      initialize-schema: never
```

生产配置额外设置：

```yaml
server:
  servlet:
    session:
      cookie:
        secure: true
```

Nginx 或外层 TLS 终止代理必须规范化并传递 `Host`、`X-Forwarded-For`、`X-Forwarded-Proto`，需要外部端口时同时传递 `X-Forwarded-Port`。若 TLS 在外层 Ingress/LB 终止，内层 Nginx 不得用自身收到的 HTTP `$scheme` 覆盖可信的外部 HTTPS 协议。后端应用端口不对宿主机公开，转发头只信任受控代理网络。

## 6. 初始化引导

### 6.1 API

```
GET /api/v1/household/status
  → { initialized: boolean }

GET /api/v1/household/me
  → 当前已认证账户的家庭、成员、角色和状态

POST /api/v1/household/bootstrap
  请求体: {
    householdName: "string",
    username: "string",
    password: "string",
    displayName: "string",
    email?: "string"
  }
  → 201 Created + 自动登录会话
```

### 6.2 流程

1. 在同一数据库事务中首先插入 `household(singleton_key = 1)`；唯一约束冲突统一返回 `409 HOUSEHOLD_ALREADY_INITIALIZED`
2. 通过 `IdentityApi.registerAccount(...)` 创建账户，密码使用 `DelegatingPasswordEncoder`
3. 创建 `member` 记录（角色 `OWNER`、状态 `ACTIVE`）
4. 在同一事务中写入 `HOUSEHOLD_INITIALIZED` 审计记录
5. 事务提交后使用共享 session authentication support 自动登录并返回账户会话

`system_installation` 已由阶段一 Flyway V1 迁移创建，本阶段不得重复初始化，也不得由 `household` 直接访问其内部 Mapper。家庭单例约束和事务回滚保证两个并发初始化请求至多一个成功；失败请求不得留下孤立账户、家庭或成员记录。

### 6.3 前端流程

- 应用首次加载时调用 `GET /api/v1/household/status`，不在每次路由跳转时重复请求
- 如果 `initialized=false` 且不在初始化页面，重定向到 `/bootstrap`
- 初始化页面加载后先取得 CSRF Token，再提交初始化请求
- 初始化成功后刷新 CSRF Token 和当前成员信息，再跳转到首页

## 7. 邀请系统

### 7.1 创建邀请（Owner/Admin）

```
POST /api/v1/invitations
  请求体: {
    role: "MEMBER"|"ADMIN",
    expiresInHours: 24
  }
  → {
    id: "uuid",
    token: "string",
    role: "MEMBER",
    expiresAt: "2026-07-21T12:00:00Z",
    path: "/invitation/redeem#token=xxx"
  }
```

API 返回相对路径，不根据未经校验的 Host Header 拼接绝对 URL。前端使用当前同源 origin 生成可复制链接。

Admin 只能创建 `MEMBER` 邀请；只有 Owner 可以创建 `ADMIN` 邀请。后端根据当前角色校验请求中的目标角色，不能只依赖前端隐藏选项。

### 7.2 查询邀请信息（公开）

```
POST /api/v1/invitations/inspect
  请求体: { token: "string" }
  → {
    householdName: "string",
    role: "MEMBER",
    expiresAt: "2026-07-21T12:00:00Z",
    valid: true
  }
```

### 7.3 兑换邀请（公开）

```
POST /api/v1/invitations/redeem
  请求体: {
    token: "string",
    username: "string",
    password: "string",
    displayName: "string",
    email?: "string"
  }
  → 201 Created + 自动登录会话
```

**流程：**
1. 对请求中的原始 Token 计算 SHA-256 摘要并按摘要锁定邀请行
2. 验证未过期且 `consumed_at IS NULL`；无效、已过期和已消费统一返回不泄露内部状态的错误
3. 通过 `IdentityApi.registerAccount(...)` 创建新账户，不允许公开端点绑定已有账户
4. 创建成员记录，角色取邀请记录中的不可变角色
5. 写入 `consumed_at` 和 `consumed_by`
6. 在同一事务中写入 `MEMBER_JOINED` 审计记录；任一步失败整体回滚
7. 提交后自动登录，刷新 CSRF Token 并返回账户会话

唯一摘要约束、行锁和单事务消费保证并发兑换时恰好一个请求成功。

### 7.4 邀请码格式

- 使用 `SecureRandom` 生成 32 字节
- 使用 Base64 URL-safe、无 padding 编码
- 原始 Token 只在创建响应中返回一次，数据库只保存 SHA-256 摘要
- 应用日志、访问日志、审计详情和错误响应不得输出完整 Token
- 邀请链接把 Token 放在 URL fragment 中，fragment 不会发送给 Nginx；兑换页面读取后立即使用 `history.replaceState` 从地址栏移除
- 查询和兑换 API 把 Token 放在 JSON 请求体中，不放入 URL、查询参数或 Header 日志
- 页面设置 `Referrer-Policy: no-referrer`
- 公开查询和兑换端点同时受来源 IP 限流；兑换 POST 仍要求 CSRF Token

## 8. 成员管理

### 8.1 成员列表（已认证）

```
GET /api/v1/members
  → [
    {
      id: "uuid",
      accountId: "uuid",
      username: "string",
      displayName: "string",
      role: "OWNER",
      status: "ACTIVE",
      createdAt: "2026-07-20T12:00:00Z"
    }
  ]
```

### 8.2 修改角色（Owner）

```
PUT /api/v1/members/{id}/role
  请求体: { role: "ADMIN"|"MEMBER" }
  → 200 OK
  审计记录：ROLE_CHANGED
```

只有 Owner 可以任命或撤销 Admin。该接口不能把任何成员直接修改为 `OWNER`，也不能修改当前 Owner；所有权只能通过专用转移接口变更。

### 8.3 停用/启用成员（Admin+）

```
PUT /api/v1/members/{id}/status
  请求体: { status: "ACTIVE"|"DEACTIVATED" }
  → 200 OK
  审计记录：MEMBER_DEACTIVATED / MEMBER_REACTIVATED
```

Admin 只能停用或启用普通成员，不能操作 Owner、其他 Admin 或自己；Owner 可以操作 Admin 和普通成员，但不能停用自己。状态变更事务同时更新 `member.status` 和 `account.status`，停用成功后立即删除目标账户的全部 Spring Session，并在后端授权路径中再次校验成员状态，保证漏删会话时仍无法继续操作。

### 8.4 转移所有权（Owner）

```
POST /api/v1/household/transfer-ownership
  请求体: { targetMemberId: "uuid" }
  → 200 OK
  审计记录：OWNERSHIP_TRANSFERRED
```

目标必须是同一家庭中的活跃 Admin 或普通成员。转移事务锁定家庭的成员记录，先将原 Owner 调整为 `ADMIN`，再将目标调整为 `OWNER`，并依靠唯一 Owner 索引防止并发转移。事务中记录旧、新 Owner，提交后删除两者全部会话，要求重新登录以刷新安全上下文。

### 8.5 所有者恢复命令

```bash
docker compose exec app java -jar /app/zija.jar \
  --spring.main.web-application-type=none \
  --zija.command=recover-owner
```

- 命令使用现有容器的数据库环境变量，但以非 Web 模式启动，不绑定 8080 端口
- `OwnerRecoveryCommand` 只在 `zija.command=recover-owner` 时启用，完成后返回明确退出码；普通 Web 启动绝不执行恢复逻辑
- Servlet Security、MVC 控制器等 Web-only 配置必须以 WebApplication 条件保护，保证非 Web 命令上下文可以独立启动
- 命令只为当前唯一 Owner 生成一次性密码恢复链接，有效期 15 分钟
- 原始 Token 只输出到当前终端一次，数据库仅保存 SHA-256 摘要
- 生成新 Token 时使该 Owner 之前未使用的 Token 失效
- 恢复链接使用 `/owner-recovery#token=...`，避免 Token 进入 Nginx URL 访问日志
- `POST /api/v1/owner-recovery/inspect` 在 JSON 请求体中接收 Token，只返回有效性和 Owner 显示名
- `POST /api/v1/owner-recovery/reset-password` 在 JSON 请求体中接收 Token 和新密码，要求 CSRF Token，并在单事务中锁定、校验、消费 Token 和更新密码
- 恢复成功后删除 Owner 全部会话并记录 `OWNER_RECOVERY` 审计事件
- 公开恢复流程不允许创建第二个 Owner；若数据库已不存在唯一 Owner，命令失败并要求管理员从备份恢复或执行受控修复

## 9. 权限控制

### 9.1 角色定义

| 角色 | 说明 |
|---|---|
| `OWNER` | 家庭所有者，拥有所有权限 |
| `ADMIN` | 管理员，可以管理成员和设置 |
| `MEMBER` | 普通成员，可以操作库存 |

### 9.2 权限矩阵实现

阶段二使用三个明确的元注解，不使用无法解析注解属性的 `#requiredRole` 表达式：

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'MEMBER')")
public @interface RequireMember {}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'ADMIN')")
public @interface RequireAdmin {}

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@householdAuthorization.hasAtLeast(authentication, 'OWNER')")
public @interface RequireOwner {}
```

| 能力 | 注解 |
|---|---|
| 查看物品、位置、库存 | `@RequireMember` |
| 新建和编辑物品 | `@RequireMember` |
| 入库、领用、报损、移位和盘点 | `@RequireMember` |
| 冲正库存流水 | `@RequireAdmin` |
| 管理提醒规则 | `@RequireAdmin` |
| 邀请、停用和调整普通成员 | `@RequireAdmin`，服务层进一步限制目标角色 |
| 任命或撤销管理员 | `@RequireOwner` |
| 导入、导出和查看完整审计日志 | `@RequireAdmin` |
| 修改系统与邮件设置 | `@RequireAdmin` |
| 转移所有权或删除家庭 | `@RequireOwner` |

### 9.3 权限评估器

```java
@Component
public class HouseholdAuthorization {

    private final HouseholdApi householdApi;

    public boolean hasAtLeast(Authentication auth, String requiredRole) {
        // 从经过认证的 principal 读取不可变 accountId
        // 查询当前 ACTIVE 成员，停用成员直接拒绝
        // 按 OWNER > ADMIN > MEMBER 比较角色
    }
}
```

`@EnableMethodSecurity` 必须开启。URL 授权只负责区分公开与已认证端点；角色和目标对象规则必须在业务方法上再次校验。控制器参数中的 `householdId`、`accountId` 或角色值不能替代从当前认证和数据库读取的安全边界。

## 10. 审计日志

### 10.1 审计事件类型

| 事件 | 说明 |
|---|---|
| `LOGIN_SUCCESS` | 登录成功 |
| `LOGIN_FAILURE` | 登录失败 |
| `LOGOUT` | 登出 |
| `HOUSEHOLD_INITIALIZED` | 家庭初始化 |
| `MEMBER_JOINED` | 成员加入（邀请兑换） |
| `ROLE_CHANGED` | 角色变更 |
| `MEMBER_DEACTIVATED` | 成员停用 |
| `MEMBER_REACTIVATED` | 成员启用 |
| `OWNERSHIP_TRANSFERRED` | 所有权转移 |
| `PASSWORD_CHANGED` | 密码修改 |
| `INVITATION_CREATED` | 邀请创建 |
| `INVITATION_REDEEMED` | 邀请成功兑换 |
| `OWNER_RECOVERY` | 所有者恢复 |

### 10.2 审计服务

`system` 模块通过 `SystemApi.recordAudit(AuditEvent event)` 暴露类型化审计接口，业务模块不得直接访问 `audit_log` Mapper，也不使用任意字符串加任意 Map 作为跨模块契约。`AuditEvent` 至少包含：

- 事件类型和成功/失败结果
- householdId、actorAccountId、subjectAccountId
- requestId、经过可信代理处理的客户端 IP
- 按事件定义的白名单详情，例如旧角色、新角色和失败原因分类
- UTC 发生时间，由 `system` 模块时钟提供

初始化、邀请兑换、角色调整、成员状态变更和所有权转移的成功审计与业务写入处于同一事务。登录失败等没有成功业务事务的安全事件使用独立事务记录；审计写入失败必须记录服务端错误，但不得把密码或 Token 写入普通日志。

## 11. 前端设计

### 11.1 页面结构

```
views/
  BootstrapPage.vue          # 首次初始化页面
  LoginPage.vue              # 登录页面
  InvitationRedeemPage.vue   # 邀请兑换页面
  MembersPage.vue            # 成员管理页面（Admin+）
  ProfilePage.vue            # 个人资料、修改密码
```

### 11.2 路由守卫

```typescript
router.beforeEach(async (to) => {
  const session = useSessionStore()
  await session.ensureInitialized()

  if (!session.householdInitialized && to.name !== 'bootstrap') {
    return { name: 'bootstrap' }
  }

  if (session.isPublicRoute(to)) {
    return true
  }

  if (!session.authenticated) {
    return { name: 'login' }
  }
})
```

Pinia 只保存当前页面生命周期需要的会话、当前成员和 UI 状态，不长期缓存成员列表等服务器数据。应用首次加载或收到 `401` 时刷新会话；不在每次路由切换中重复请求家庭状态和会话。前端路由守卫只改善体验，后端始终执行完整授权。

### 11.3 API 客户端扩展

```typescript
// api/auth.ts
export const authApi = {
  login: (data: LoginRequest) => postJson<SessionInfo>('/api/v1/auth/login', data),
  logout: () => postJson('/api/v1/auth/logout'),
  getSession: () => getJson<SessionInfo>('/api/v1/auth/session'),
  initializeCsrf: () => getJson<CsrfToken>('/api/v1/auth/csrf'),
  changePassword: (data: ChangePasswordRequest) => putJson('/api/v1/auth/password', data),
}

// api/household.ts
export const householdApi = {
  getStatus: () => getJson<HouseholdStatus>('/api/v1/household/status'),
  getCurrentMember: () => getJson<CurrentMember>('/api/v1/household/me'),
  bootstrap: (data: BootstrapRequest) => postJson<SessionInfo>('/api/v1/household/bootstrap', data),
  transferOwnership: (data: TransferRequest) => postJson('/api/v1/household/transfer-ownership', data),
}

// api/invitation.ts
export const invitationApi = {
  inspect: (token: string) => postJson<InvitationInfo>('/api/v1/invitations/inspect', { token }),
  create: (data: CreateInvitationRequest) => postJson<InvitationInfo>('/api/v1/invitations', data),
  redeem: (token: string, data: RedeemRequest) =>
    postJson<SessionInfo>('/api/v1/invitations/redeem', { token, ...data }),
}

// api/member.ts
export const memberApi = {
  list: () => getJson<MemberInfo[]>('/api/v1/members'),
  updateRole: (id: string, data: UpdateRoleRequest) => putJson(`/api/v1/members/${id}/role`, data),
  updateStatus: (id: string, data: UpdateStatusRequest) => putJson(`/api/v1/members/${id}/status`, data),
}
```

集中式 HTTP 客户端必须：

- 所有请求使用 `credentials: "same-origin"`
- 在 POST、PUT、PATCH、DELETE 前确保 `XSRF-TOKEN` Cookie 存在，并将其值放入 `X-XSRF-TOKEN`
- 登录、初始化、邀请兑换、登出和恢复密码成功后重新初始化 CSRF Token
- 将 `401` 解释为会话缺失或失效并刷新 session store，将 `403` 区分为 CSRF 错误和权限不足
- 继续解析 Problem Details 的 `errorCode`、字段错误和 `requestId`
- 不把密码、会话 ID、邀请 Token 或恢复 Token 写入 Pinia 持久化、LocalStorage 或日志

## 12. OpenAPI 与契约

- 使用 `org.springdoc:springdoc-openapi-starter-webmvc-ui` 的 Spring Boot 4 兼容版本；实施计划第一项验证依赖可启动、`/v3/api-docs` 可生成且 Spring Security 注解不破坏文档
- OpenAPI 覆盖系统信息、CSRF、登录、登出、会话、初始化、当前成员、邀请、成员管理、所有权转移和恢复密码端点
- 所有请求、成功响应和 Problem Details 错误模型具有明确 Schema；枚举、格式、长度、必填字段和响应状态不能只写在自然语言中
- 生成的 OpenAPI JSON 作为构建产物进行规范校验，并与仓库中的批准基线做破坏性差异检查
- `/v3/api-docs` 和 Swagger UI 默认只在开发、测试环境开放；生产环境是否开放由配置显式决定

## 13. 测试策略

### 13.1 后端测试

**单元测试：**
- `IdentityServiceTest`：用户名规范化、大小写冲突、密码编码升级、账户状态
- `InvitationServiceTest`：Token 摘要、过期、消费和错误不泄露
- `MemberServiceTest`：角色层级、目标角色限制、唯一 Owner、不允许自我停用
- `OwnerRecoveryServiceTest`：令牌轮换、过期、单次消费和密码重置
- `LoginRateLimiterTest`：账户/IP 独立桶、阈值、TTL、容量上限和成功清理
- `HouseholdAuthorizationTest`：ACTIVE 状态与 `OWNER > ADMIN > MEMBER` 比较

**集成测试（Testcontainers）：**
- `AccountMapperIntegrationTest`：账户 CRUD、规范化用户名唯一约束和状态 CHECK
- `HouseholdBootstrapIntegrationTest`：两个并发初始化请求恰好一个成功且无孤立数据
- `MemberMapperIntegrationTest`：成员唯一约束、合法枚举和唯一 Owner
- `InvitationMapperIntegrationTest`：两个并发兑换请求恰好一个成功，失败事务完整回滚
- `OwnerRecoveryIntegrationTest`：并发消费、密码更新和旧会话清理
- `SpringSessionIntegrationTest`：principal 索引、跨重启读取和按账户删除全部会话
- `AuditLogIntegrationTest`：业务写入与成功审计原子提交，敏感字段不落库

**API 测试（MockMvc）：**
- `AuthControllerTest`：登录、登出、会话、错误一致性、session fixation 和 CSRF Token 刷新
- `HouseholdControllerTest`：初始化、状态、当前成员和重复初始化
- `InvitationControllerTest`：创建、公开查询、兑换、CSRF 和限流
- `MemberControllerTest`：列表、角色变更、目标角色约束、状态变更和所有权转移
- `OwnerRecoveryControllerTest`：公开查询、密码恢复、过期/已消费错误和 CSRF
- `ForwardedHeadersSecurityTest`：可信 `X-Forwarded-Proto: https` 下请求被识别为 Secure，生产会话 Cookie 包含 `Secure`
- `OpenApiContractTest`：规范可生成、可校验并通过破坏性差异门禁

**权限测试：**
- 验证角色矩阵中每个能力的访问控制
- 验证停用成员即使持有未清理的旧 Cookie 也无法执行操作
- 验证前端隐藏的端点仍需后端权限检查
- 验证 Admin 不能修改 Owner/Admin，Owner 不能停用自己
- 验证所有请求中的 householdId、accountId 和 role 参数不能绕过当前家庭边界
- 验证登录、初始化、邀请兑换、恢复密码和登出缺少 CSRF Token 时返回 403

### 13.2 前端测试

- `LoginPage.spec.ts`：表单验证、登录成功/失败、限流提示
- `BootstrapPage.spec.ts`：初始化流程、CSRF 初始化、重复初始化冲突
- `InvitationRedeemPage.spec.ts`：邀请信息、地址栏 Token 清理、兑换与无效 Token
- `MembersPage.spec.ts`：成员列表、按钮权限、目标角色限制和停用
- `ProfilePage.spec.ts`：修改密码、成功后会话失效和重新登录
- `sessionStore.spec.ts`：首次加载、401 清理、登录/登出后 CSRF 刷新
- `http.spec.ts`：不安全方法携带 `X-XSRF-TOKEN`，Problem Details 和 requestId 处理

### 13.3 Playwright 端到端测试

1. **初始化流程**：取得 CSRF → 创建家庭和 Owner → 自动登录 → 刷新 CSRF
2. **登录流程**：成功、统一失败提示、账户/IP 限流和 Session ID 变化
3. **邀请流程**：创建 → 复制链接 → 清理地址栏 Token → 单次兑换 → 自动登录
4. **权限验证**：Member、Admin、Owner 的允许和拒绝路径
5. **会话管理**：登出、空闲过期、修改密码和停用成员导致全部会话失效
6. **成员管理**：角色变更、目标角色限制、停用和所有权转移
7. **所有者恢复**：维护命令生成链接 → 重置密码 → Token 失效 → 旧会话失效

## 14. 验收标准

- [ ] 首次引导创建家庭和所有者，自动登录
- [ ] 两个并发初始化请求恰好一个成功，数据库始终只有一个家庭和一个 Owner
- [ ] 用户名大小写不敏感且唯一，密码使用带算法标识的自适应哈希
- [ ] 登录成功更换 Session ID；会话 Cookie 为 `ZIJA_SESSION`、`HttpOnly`、`SameSite=Lax`，生产 HTTPS 下包含 `Secure`
- [ ] 登录失败账户桶 5 次/5 分钟、IP 桶 20 次/5 分钟，错误不泄露账户是否存在
- [ ] CSRF 使用 `XSRF-TOKEN`/`X-XSRF-TOKEN`，所有公开和受保护写请求缺少 Token 时返回 403
- [ ] 登录和登出后前端取得新的 CSRF Token
- [ ] 所有者创建邀请链接，链接可访问
- [ ] 邀请和恢复 Token 只存摘要，日志、审计和浏览器持久化中没有完整 Token
- [ ] 两个并发邀请兑换请求恰好一个成功，成功后创建成员并自动登录
- [ ] Owner 可以任命/撤销 Admin
- [ ] Admin 只能管理普通成员；Owner 不能被停用或通过通用角色接口降级
- [ ] 所有权转移保持唯一 Owner，旧、新 Owner 的会话全部失效
- [ ] 停用成员和修改密码使该账户全部会话立即失效
- [ ] 容器非 Web 恢复命令生成一次性链接，消费后重置密码并使旧会话失效
- [ ] 审计日志记录全部关键事件、requestId、actor/subject 和结果，且不含敏感值
- [ ] OpenAPI 规范通过生成、校验和破坏性差异检查；生产开放策略由配置控制
- [ ] `ModularityTests` 证明 `household → identity` 单向依赖且无跨模块 internal 引用
- [ ] Playwright 测试覆盖所有关键流程
- [ ] `make verify` 全部通过
