# ADR-012: 身份认证、会话与家庭安全模型

## 状态

已批准

## 背景

知家是单家庭私有部署系统，首期只有 Web 端，认证必须安全且不依赖外部服务。spec §10 要求密码自适应哈希、CSRF 防护、会话 Cookie 安全属性和登录限流。spec §4.1 要求首个账户自动成为所有者，后续成员通过限时单次邀请加入。如何在单体应用中实现完整的安全链路？

## 决策

### 1. 密码与认证

- 使用 `DelegatingPasswordEncoder`，当前编码采用 BCrypt，保存 `{id}hash` 格式，为后续提高强度或迁移算法保留升级能力。
- 用户名去除首尾空白并按 `Locale.ROOT` 转小写后写入 `username_normalized`，注册、登录、限流和唯一性检查统一使用该值。
- 登录失败对"账户不存在"和"密码错误"执行等价的密码哈希成本并返回相同错误，防止账户枚举。

### 2. 会话管理

- 使用 Spring Session JDBC，会话表由 Flyway 管理迁移，生产配置 `spring.session.jdbc.initialize-schema=never`，禁止应用启动时自动建表。
- Cookie 名固定为 `ZIJA_SESSION`，`HttpOnly=true`、`SameSite=Lax`、`Path=/`，生产环境（`prod` profile）`Secure=true`。
- 允许同一账户多会话（多设备登录）；停用成员、修改密码和所有权转移时清理该账户全部会话。
- 默认空闲超时 24 小时。
- Pinia 只保存当前页面生命周期的会话状态，不长期缓存服务端业务数据；不在浏览器中存储长期访问令牌。

### 3. CSRF 防护

- Spring Security SPA 配置：Cookie `XSRF-TOKEN` + Header `X-XSRF-TOKEN` 双提交 Cookie 流程。
- 不为公开写端点（登录、初始化、邀请兑换、恢复密码、登出）配置 `ignoringRequestMatchers`，所有不安全方法都必须携带 CSRF Token。

### 4. 登录限流

- 双桶设计：账户桶（同一规范化用户名 5 分钟内失败 5 次 → 锁 5 分钟）和 IP 桶（同一来源 IP 5 分钟内失败 20 次 → 锁 5 分钟），分别计算。
- 单实例内存限流，有界容量 + TTL 防止内存无限增长。当前 Compose 只有一个应用实例，无需 Redis。未来多实例时必须替换为共享限流存储。

### 5. 家庭单例与所有权

- `singleton_key = 1` 在数据库层保证一套部署最多只有一个家庭；初始化事务先尝试插入单例记录，并发请求由主键/检查约束串行化。
- 首次引导除 CSRF 外，还须携带部署时配置的初始化口令（`ZIJA_SETUP_TOKEN` / 请求头 `X-Zija-Setup-Token`）；`prod` profile 下口令未配置则应用拒绝启动。
- `uq_member_single_owner` 部分唯一索引保证一个家庭最多一个 `OWNER`。
- 所有者不允许被停用或直接降级，必须通过所有权转移在同一事务中完成新旧 Owner 角色切换，提交后删除两者全部会话。
- 所有者恢复使用容器内非 Web 命令模式（`zija.command=recover-owner`），不启动第二个 Web 服务；恢复令牌只显示一次且只存 SHA-256 摘要。

### 6. 邀请机制

- 邀请单次使用，`SecureRandom` 生成 32 字节令牌，只向创建者返回一次，数据库仅存 SHA-256 摘要。
- 兑换事务通过摘要查询并锁定邀请行，任一步失败则整体回滚。
- 邀请链接把 Token 放在 URL fragment 中（不发送给 Nginx），兑换页面读取后立即 `history.replaceState` 移除。
- Admin 只能创建 `MEMBER` 邀请；只有 Owner 可创建 `ADMIN` 邀请。后端校验目标角色，不依赖前端隐藏选项。

### 7. 权限模型

- 三个元注解：`@RequireMember`、`@RequireAdmin`、`@RequireOwner`，不使用 SpEL `#requiredRole` 表达式。
- URL 授权只区分公开与已认证端点；角色和目标对象规则在业务方法上再次校验。控制器参数中的 `householdId` 不能替代从当前认证和数据库读取的安全边界。
- 数据访问始终绑定当前家庭，不接受客户端任意指定家庭 ID。

## 考虑过的备选

- **JWT 无状态令牌**：首期 Web 同源部署不需要，且 JWT 撤销困难，与"停用即清理会话"的安全要求冲突。移动端令牌认证在 App 阶段新增。
- **`username+IP` 组合限流键**：无法分别应对撞库（单账户多 IP）和扫号（多账户单 IP），双桶设计覆盖两种场景。
- **为公开端点忽略 CSRF**：会引入登录 CSRF 风险，所有不安全方法一律要求 Token。

## 后果

- 不配置 SMTP 也能完整运行全部核心业务流程。
- 会话安全性由 Cookie 属性 + 服务端校验 + 限流三重保障。
- 密钥（数据库密码、SMTP 密码）全部通过环境变量注入，不落库。
- 审计日志不对账户和家庭设外键，以便停用或归档后仍保留历史。
