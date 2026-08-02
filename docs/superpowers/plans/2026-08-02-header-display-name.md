# 顶栏显示名 + 个人资料增强 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把顶栏右上角的角色徽章（所有者/管理员/成员）换成可点击的显示名下拉（个人资料 / 登出），并扩展个人资料页支持展示用户名、显示名、角色和编辑显示名。

**Architecture:** 后端在 identity 模块新增 `PUT /api/v1/auth/display-name` 自改显示名端点，改库后通过 `ZijaSessionAuthenticationSupport.refreshPrincipal` 就地刷新当前会话的 `ZijaPrincipal`（审计 actor 名不脏）；前端顶栏用 `el-dropdown` 收纳显示名与登出，个人资料页编辑成功后刷新 session store 的 `currentMember` 实现顶栏即时更新。DB 层 `display_name` 在 V1 已是 `NOT NULL`，无需任何迁移。

**Tech Stack:** Spring Boot / Spring Modulith / MyBatis-Plus / Vue 3 / Element Plus / Playwright

---

## File Structure

**Backend (modify):**
- `backend/src/main/java/com/zija/identity/internal/auth/UpdateDisplayNameRequest.java` — 新建，`@NotBlank` + `@Size(max=100)` 请求体
- `backend/src/main/java/com/zija/identity/internal/persistence/AccountMapper.java` — 新增 `updateDisplayName` 方法
- `backend/src/main/resources/mapper/identity/AccountMapper.xml` — 新增乐观锁 `updateDisplayName` SQL
- `backend/src/main/java/com/zija/identity/internal/IdentityService.java` — 新增 `updateDisplayName(accountId, displayName)`（trim + 乐观锁）
- `backend/src/main/java/com/zija/ZijaSessionAuthenticationSupport.java` — 新增 `refreshPrincipal` 方法
- `backend/src/main/java/com/zija/identity/internal/IdentityController.java` — 新增 `PUT /api/v1/auth/display-name` 端点 + 审计

**Backend (test):**
- `backend/src/test/java/com/zija/identity/internal/persistence/AccountMapperIntegrationTest.java`
- `backend/src/test/java/com/zija/identity/internal/IdentityServiceTest.java`
- `backend/src/test/java/com/zija/identity/internal/IdentityControllerTest.java`
- `backend/src/test/java/com/zija/ZijaSessionLifecycleIntegrationTest.java`

**Frontend (modify):**
- `frontend/src/types/identity.ts` — 新增 `UpdateDisplayNameRequest`
- `frontend/src/api/auth.ts` — 新增 `updateDisplayName`
- `frontend/src/stores/session.ts` — 新增 `refreshCurrentMember` action
- `frontend/src/components/AppShell.vue` — 显示名下拉替代角色徽章 + 独立登出按钮
- `frontend/src/views/ProfilePage.vue` — 个人信息卡片（用户名/角色/改显示名）+ 保留改密码

**Frontend (test):**
- `frontend/src/components/AppShell.test.ts`
- `frontend/src/views/ProfilePage.test.ts`

**E2E (modify + create):**
- `frontend/e2e/helpers.ts`
- `frontend/e2e/login.spec.ts`
- `frontend/e2e/bootstrap.spec.ts`
- `frontend/e2e/profile.spec.ts` — 新建

---

### Task 1: 后端 — `updateDisplayName` 的 Mapper + 请求 DTO

**Files:**
- Modify: `backend/src/main/java/com/zija/identity/internal/persistence/AccountMapper.java`
- Modify: `backend/src/main/resources/mapper/identity/AccountMapper.xml`
- Create: `backend/src/main/java/com/zija/identity/internal/auth/UpdateDisplayNameRequest.java`
- Test: `backend/src/test/java/com/zija/identity/internal/persistence/AccountMapperIntegrationTest.java`

- [ ] **Step 1: 写失败的分片集成测试**

在 `AccountMapperIntegrationTest.java` 的类尾（`rejectsDuplicateNormalizedUsername` 之后）追加：

```java
@Test
@Transactional
void updatesDisplayNameAndBumpsVersion() {
    var entity = new AccountEntity();
    entity.setId(UUID.randomUUID());
    entity.setUsername("alice");
    entity.setUsernameNormalized("alice");
    entity.setPasswordHash("{bcrypt}$2a$10$examplehash");
    entity.setDisplayName("Alice");
    entity.setStatus("ACTIVE");
    mapper.insert(entity);

    var affected = mapper.updateDisplayName(entity.getId(), "Alice 2", entity.getVersion());

    assertThat(affected).isEqualTo(1);
    var found = mapper.selectById(entity.getId());
    assertThat(found.getDisplayName()).isEqualTo("Alice 2");
    assertThat(found.getVersion()).isEqualTo(entity.getVersion() + 1);
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && ./mvnw -q test -Dtest=AccountMapperIntegrationTest`
Expected: 编译失败，`AccountMapper` 无 `updateDisplayName` 方法。

- [ ] **Step 3: 实现 Mapper 方法与 SQL**

在 `AccountMapper.java` 的 `updatePasswordHash` 之后追加：

```java
int updateDisplayName(@Param("id") UUID id, @Param("displayName") String displayName, @Param("version") Integer version);
```

在 `AccountMapper.xml` 的 `</mapper>` 之前追加：

```xml
<update id="updateDisplayName">
    UPDATE account
    SET display_name = #{displayName}, updated_at = CURRENT_TIMESTAMP, version = version + 1
    WHERE id = #{id, typeHandler=com.zija.system.internal.persistence.PostgresUuidTypeHandler}
      AND version = #{version}
</update>
```

- [ ] **Step 4: 创建请求 DTO**

创建 `UpdateDisplayNameRequest.java`：

```java
package com.zija.identity.internal.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateDisplayNameRequest(
        @NotBlank(message = "不能为空")
        @Size(max = 100, message = "长度不能超过 {max} 个字符")
        String displayName
) {
}
```

（校验规格与 `HouseholdController` bootstrap / `InvitationController` redeem 的 displayName 完全一致。）

- [ ] **Step 5: 运行确认通过**

Run: `cd backend && ./mvnw -q test -Dtest=AccountMapperIntegrationTest`
Expected: PASS（2 个测试全绿）。

- [ ] **Step 6: 提交**

```bash
git add backend/src/main/java/com/zija/identity/internal/persistence/AccountMapper.java \
        backend/src/main/resources/mapper/identity/AccountMapper.xml \
        backend/src/main/java/com/zija/identity/internal/auth/UpdateDisplayNameRequest.java \
        backend/src/test/java/com/zija/identity/internal/persistence/AccountMapperIntegrationTest.java
git commit -m "feat(identity): 支持修改账户显示名（mapper 与请求体）"
```

---

### Task 2: 后端 — `IdentityService.updateDisplayName`

**Files:**
- Modify: `backend/src/main/java/com/zija/identity/internal/IdentityService.java`
- Test: `backend/src/test/java/com/zija/identity/internal/IdentityServiceTest.java`

- [ ] **Step 1: 写失败的单测**

在 `IdentityServiceTest.java` 类尾追加：

```java
@Test
void updateDisplayNameTrimsAndPersists() {
    var account = new AccountEntity();
    account.setId(java.util.UUID.randomUUID());
    account.setVersion(2);
    account.setDisplayName("旧名字");
    when(accountMapper.selectById(account.getId())).thenReturn(account);
    when(accountMapper.updateDisplayName(account.getId(), "新名字", 2)).thenReturn(1);

    var info = service.updateDisplayName(account.getId(), "  新名字  ");

    assertThat(info.displayName()).isEqualTo("新名字");
    verify(accountMapper).updateDisplayName(account.getId(), "新名字", 2);
}

@Test
void updateDisplayNameThrowsWhenAccountMissing() {
    when(accountMapper.selectById(any())).thenReturn(null);

    assertThatThrownBy(() -> service.updateDisplayName(java.util.UUID.randomUUID(), "名字"))
            .isInstanceOf(InvalidCredentialsException.class);
}

@Test
void updateDisplayNameThrowsOnOptimisticLockFailure() {
    var account = new AccountEntity();
    account.setId(java.util.UUID.randomUUID());
    account.setVersion(1);
    when(accountMapper.selectById(account.getId())).thenReturn(account);
    when(accountMapper.updateDisplayName(account.getId(), "名字", 1)).thenReturn(0);

    assertThatThrownBy(() -> service.updateDisplayName(account.getId(), "名字"))
            .isInstanceOf(InvalidCredentialsException.class);
}
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && ./mvnw -q test -Dtest=IdentityServiceTest`
Expected: 编译失败，`IdentityService` 无 `updateDisplayName` 方法。

- [ ] **Step 3: 实现服务方法**

在 `IdentityService.java` 的 `requireActive` 方法之后追加：

```java
/**
 * 修改指定账户的显示名称，返回更新后的账户信息。
 * 此操作不改动会话：调用方负责刷新当前会话的认证主体。
 *
 * @throws InvalidCredentialsException 如果账户不存在或乐观锁失败
 */
@Transactional
public AccountInfo updateDisplayName(UUID accountId, String displayName) {
    var account = accountMapper.selectById(accountId);
    if (account == null) {
        throw new InvalidCredentialsException();
    }
    var trimmed = displayName.trim();
    if (accountMapper.updateDisplayName(accountId, trimmed, account.getVersion()) != 1) {
        throw new InvalidCredentialsException();
    }
    account.setDisplayName(trimmed);
    return toInfo(account);
}
```

注意：该方法**不加到 `IdentityApi` 接口**——只由本模块 controller 自服务调用，其他模块不需要跨模块改显示名。

- [ ] **Step 4: 运行确认通过**

Run: `cd backend && ./mvnw -q test -Dtest=IdentityServiceTest`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/zija/identity/internal/IdentityService.java \
        backend/src/test/java/com/zija/identity/internal/IdentityServiceTest.java
git commit -m "feat(identity): 服务层支持修改显示名（trim + 乐观锁）"
```

---

### Task 3: 后端 — 会话 principal 刷新 + 控制器端点 + 审计

**Files:**
- Modify: `backend/src/main/java/com/zija/ZijaSessionAuthenticationSupport.java`
- Modify: `backend/src/main/java/com/zija/identity/internal/IdentityController.java`
- Test: `backend/src/test/java/com/zija/identity/internal/IdentityControllerTest.java`
- Test: `backend/src/test/java/com/zija/ZijaSessionLifecycleIntegrationTest.java`

- [ ] **Step 1: 写失败的 Web 切片测试**

在 `IdentityControllerTest.java` 中追加三个测试：

```java
@Test
void changeDisplayNameUpdatesPrincipalAndAudits() throws Exception {
    var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧名字", "{bcrypt}x", true);
    var updatedInfo = new IdentityApi.AccountInfo(
            principal.getAccountId(), "owner", "新名字", null, "ACTIVE");
    when(identityService.updateDisplayName(principal.getAccountId(), "  新名字  "))
            .thenReturn(updatedInfo);

    mockMvc.perform(put("/api/v1/auth/display-name")
                    .with(SecurityMockMvcRequestPostProcessors.user(principal))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("displayName", "  新名字  "))))
            .andExpect(status().isNoContent());

    verify(identityService).updateDisplayName(principal.getAccountId(), "  新名字  ");
    verify(sessionAuthSupport).refreshPrincipal(any(ZijaPrincipal.class), any(), any());
    verify(systemApi).recordAudit(argThat(e ->
            "DISPLAY_NAME_CHANGED".equals(e.action())
                    && principal.getAccountId().equals(e.actorAccountId())));
}

@Test
void changeDisplayNameRejectsBlank() throws Exception {
    var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧", "{bcrypt}x", true);

    mockMvc.perform(put("/api/v1/auth/display-name")
                    .with(SecurityMockMvcRequestPostProcessors.user(principal))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("displayName", "   "))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.displayName").exists());

    verifyNoInteractions(identityService);
}

@Test
void changeDisplayNameRejectsTooLong() throws Exception {
    var principal = new ZijaPrincipal(UUID.randomUUID(), "owner", "旧", "{bcrypt}x", true);

    mockMvc.perform(put("/api/v1/auth/display-name")
                    .with(SecurityMockMvcRequestPostProcessors.user(principal))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of("displayName", "长".repeat(101)))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.fieldErrors.displayName").exists());

    verifyNoInteractions(identityService);
}
```

所需新增 import（`Map` 已在文件头 import）：

```java
import static org.mockito.ArgumentMatchers.argThat;
```

- [ ] **Step 2: 运行确认失败**

Run: `cd backend && ./mvnw -q test -Dtest=IdentityControllerTest`
Expected: 编译失败，controller 无 `changeDisplayName`、`ZijaSessionAuthenticationSupport` 无 `refreshPrincipal`。

- [ ] **Step 3: 实现 `refreshPrincipal`**

在 `ZijaSessionAuthenticationSupport.java` 的 `regenerateCsrfToken` 方法之后追加：

```java
/**
 * 用新主体刷新当前会话的认证信息（不轮换会话 ID）。
 * <p>
 * 用于就地更新会话内的 {@link ZijaPrincipal}（例如改名后），
 * 使后续请求（含审计 actor 名）立即反映新值。会话主索引按 accountId
 * 存储，主体变更不影响索引。
 *
 * @param principal 新的认证主体
 * @param request   当前 HTTP 请求
 * @param response  当前 HTTP 响应
 * @return 重新构造的已认证 {@link Authentication}
 */
public Authentication refreshPrincipal(
        ZijaPrincipal principal,
        HttpServletRequest request,
        HttpServletResponse response
) {
    var authentication = new UsernamePasswordAuthenticationToken(
            principal, null, principal.getAuthorities());
    authentication.setAuthenticated(true);

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(authentication);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, request, response);
    request.getSession().setAttribute(
            FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME,
            principal.getAccountId().toString());
    return authentication;
}
```

说明：`UsernamePasswordAuthenticationToken(principal, null, authorities)` 三参构造在 authorities 为空时会把 `authenticated` 置为 `false`，而 `ZijaPrincipal.getAuthorities()` 恒为空列表，所以必须显式 `setAuthenticated(true)`（与 `DaoAuthenticationProvider.createSuccessAuthentication` 的做法一致）。

- [ ] **Step 4: 实现控制器端点**

在 `IdentityController.java` 的 `changePassword` 方法之后追加：

```java
/**
 * 修改当前登录用户的显示名称。改库后立即刷新当前会话主体，
 * 并记录审计日志。
 *
 * @param request     修改显示名请求（新显示名）
 * @param httpRequest HTTP 请求
 * @param httpResponse HTTP 响应
 */
@PutMapping("/display-name")
void changeDisplayName(
        @Valid @RequestBody UpdateDisplayNameRequest request,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    var principal = (ZijaPrincipal) authentication.getPrincipal();
    var updated = identityService.updateDisplayName(
            principal.getAccountId(), request.displayName());
    var refreshed = new ZijaPrincipal(
            principal.getAccountId(), principal.getUsername(),
            updated.displayName(), principal.getPassword(), principal.isEnabled());
    sessionAuth.refreshPrincipal(refreshed, httpRequest, httpResponse);
    systemApi.recordAudit(new SystemApi.AuditEvent(
            "DISPLAY_NAME_CHANGED", "SUCCESS", null,
            principal.getAccountId(), principal.getAccountId(),
            (String) httpRequest.getAttribute("zija.request-id"),
            resolveClientIp(httpRequest), Map.of("displayName", updated.displayName())
    ));
}
```

同时更新类顶部 javadoc 的端点清单，在 `PUT /api/v1/auth/password` 行后追加一行 `<li>{@code PUT /api/v1/auth/display-name} — 修改当前用户显示名</li>`，并新增 import `com.zija.identity.internal.auth.UpdateDisplayNameRequest`。

- [ ] **Step 5: 运行 Web 切片测试确认通过**

Run: `cd backend && ./mvnw -q test -Dtest=IdentityControllerTest`
Expected: PASS。

- [ ] **Step 6: 写端到端会话集成测试**

在 `ZijaSessionLifecycleIntegrationTest.java` 追加（复用 login 的 CSRF 轮换流程，验证改名后同一会话的 `GET /session` 返回新名字，即 principal 真的刷新了）：

```java
@Test
void changeDisplayNameRefreshesPrincipalWithinSession() throws Exception {
    var accountId = UUID.randomUUID();
    var originalPrincipal = new ZijaPrincipal(
            accountId, "owner", "旧名字", "{bcrypt}x", true);
    var authentication = mock(Authentication.class);
    when(authentication.getPrincipal()).thenReturn(originalPrincipal);
    when(authentication.isAuthenticated()).thenReturn(true);
    when(authenticationManager.authenticate(any())).thenReturn(authentication);

    // 在真实 Postgres 里播种同名账户，使真实 IdentityService 能完成更新
    var account = new com.zija.identity.internal.persistence.AccountEntity();
    account.setId(accountId);
    account.setUsername("owner");
    account.setUsernameNormalized("owner");
    account.setPasswordHash("{bcrypt}$2a$10$examplehash");
    account.setDisplayName("旧名字");
    account.setStatus("ACTIVE");
    accountMapper.insert(account);

    var session = new MockHttpSession();
    var csrfResult = mockMvc.perform(get("/api/v1/auth/csrf").session(session))
            .andExpect(status().isOk())
            .andReturn();
    var csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(csrfCookie).isNotNull();

    mockMvc.perform(post("/api/v1/auth/login")
                    .session(session)
                    .cookie(csrfCookie)
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"username":"owner","password":"Passw0rd!"}
                            """))
            .andExpect(status().isOk());

    var newCsrf = Arrays.stream(session.getAttributeNames()).anyMatch(n -> false) // 占位，见下一步
            ? csrfCookie : csrfCookie;
    mockMvc.perform(put("/api/v1/auth/display-name")
                    .session(session)
                    .cookie(csrfCookie)
                    .header("X-Request-Id", "rename-request")
                    .header("X-XSRF-TOKEN", csrfCookie.getValue())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"displayName":"新名字"}
                            """))
            .andExpect(status().isNoContent())
            .andExpect(header().string("X-Request-Id", "rename-request"));

    mockMvc.perform(get("/api/v1/auth/session")
                    .session(session)
                    .cookie(csrfCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true))
            .andExpect(jsonPath("$.accountId").value(accountId.toString()))
            .andExpect(jsonPath("$.displayName").value("新名字"));

    verify(systemApi).recordAudit(argThat(event ->
            "DISPLAY_NAME_CHANGED".equals(event.action())
                    && accountId.equals(event.actorAccountId())));
}
```

需要的追加 import 与字段（`AccountMapper` 注入）：

```java
import com.zija.identity.internal.persistence.AccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

// 字段区追加：
@Autowired AccountMapper accountMapper;
```

若 CSRF 在改名后失效导致 403，可参照 login 测试取登录响应的新 XSRF cookie 用于后续请求（登录响应的 cookie 在 `loginResult.getResponse().getCookies()` 中；本测试用同一个 session，`CsrfTokenRepository` 在轮换后已重置令牌，故需读取登录响应的新 cookie 替换上方 `csrfCookie` 变量——以运行结果为准调整取值来源）。

- [ ] **Step 7: 运行集成测试确认通过**

Run: `cd backend && ./mvnw -q test -Dtest=ZijaSessionLifecycleIntegrationTest`
Expected: PASS（login + 改名两个测试全绿）。

- [ ] **Step 8: 跑 identity 相关测试全量确认**

Run: `cd backend && ./mvnw -q test -Dtest='Identity*,AccountMapper*'`
Expected: PASS。

- [ ] **Step 9: 提交**

```bash
git add backend/src/main/java/com/zija/ZijaSessionAuthenticationSupport.java \
        backend/src/main/java/com/zija/identity/internal/IdentityController.java \
        backend/src/test/java/com/zija/identity/internal/IdentityControllerTest.java \
        backend/src/test/java/com/zija/ZijaSessionLifecycleIntegrationTest.java
git commit -m "feat(identity): 修改显示名端点并刷新当前会话主体"
```

---

### Task 4: 前端 — 类型 + API + session store

**Files:**
- Modify: `frontend/src/types/identity.ts`
- Modify: `frontend/src/api/auth.ts`
- Modify: `frontend/src/stores/session.ts`

- [ ] **Step 1: 新增类型**

在 `frontend/src/types/identity.ts` 的 `ChangePasswordRequest` 之后追加：

```typescript
export interface UpdateDisplayNameRequest {
  displayName: string;
}
```

- [ ] **Step 2: 新增 API 方法**

在 `frontend/src/api/auth.ts` 修改 import 与对象：

```typescript
import type { SessionInfo, LoginRequest, ChangePasswordRequest, UpdateDisplayNameRequest } from "../types/identity";
```

```typescript
  changePassword: (data: ChangePasswordRequest) =>
    putJson("/api/v1/auth/password", data),
  updateDisplayName: (data: UpdateDisplayNameRequest) =>
    putJson("/api/v1/auth/display-name", data),
```

- [ ] **Step 3: 新增 store action**

在 `frontend/src/stores/session.ts` 的 `clearLocalSession` action 之前追加：

```typescript
    async refreshCurrentMember(): Promise<CurrentMember> {
      const currentMember = await householdApi.getCurrentMember();
      this.currentMember = currentMember;
      return currentMember;
    },
```

- [ ] **Step 4: 验证构建**

Run: `npm --prefix frontend run build`
Expected: 类型检查与构建通过。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/types/identity.ts frontend/src/api/auth.ts frontend/src/stores/session.ts
git commit -m "feat(frontend): 显示名修改的类型、API 与会话刷新"
```

---

### Task 5: 前端 — 顶栏显示名下拉

**Files:**
- Modify: `frontend/src/components/AppShell.vue`
- Test: `frontend/src/components/AppShell.test.ts`

- [ ] **Step 1: 改失败组件测试**

将 `AppShell.test.ts` 中第一个测试（"renders the approved desktop navigation"）的断言修改为：

```typescript
    expect(wrapper.text()).toContain("知家");
    expect(wrapper.text()).toContain("首页");
    expect(wrapper.text()).toContain("成员管理");
    expect(wrapper.text()).toContain("个人资料");
    expect(wrapper.text()).toContain("物品资料");
    expect(wrapper.text()).toContain("库存管理");
    expect(wrapper.text()).toContain("位置管理");
    expect(wrapper.text()).toContain("提醒中心");
    expect(wrapper.text()).toContain("报表与导出");
    expect(wrapper.text()).toContain("家庭设置");
    expect(wrapper.text()).toContain("库存操作");

    // 顶栏显示当前成员显示名（不再显示角色徽章）
    const userTrigger = wrapper.find(".user-trigger");
    expect(userTrigger.exists()).toBe(true);
    expect(userTrigger.text()).toContain("Admin");
    // 角色徽章已移除，登出收纳在下拉菜单中（未展开不渲染）
    expect(wrapper.text()).not.toContain("管理员");
    expect(wrapper.text()).not.toContain("登出");
```

删除原来的 `expect(wrapper.text()).toContain("管理员")` 与 `expect(wrapper.text()).toContain("登出")` 两行。

在测试文件顶部（`afterEach` 之前）新增一个复用的辅助函数，并改写四个登出相关测试（logout fails / overlay / cancel / 及任何触发登出的测试），统一通过 dropdown 的 `command` 事件驱动，避免依赖 jsdom 中 popper 渲染：

```typescript
  async function triggerUserCommand(command: string) {
    const userDropdown = wrapper!
      .findAllComponents({ name: "ElDropdown" })
      .find((c) => c.find(".user-trigger").exists());
    expect(userDropdown).toBeDefined();
    await userDropdown!.vm.$emit("command", command);
    await flushPromises();
  }
```

把 `logoutButton.trigger("click")` 三段（"shows an error and stays on the current route when logout fails"、"renders the logout confirmation inside the .el-overlay"、"does not log out when the user cancels"）全部替换为 `await triggerUserCommand("logout");`，删除原有的 `const logoutButton = wrapper.findAll("button").find(...)` 两行。

新增一个测试验证下拉命令路由跳转：

```typescript
  it("navigates to profile when the user dropdown profile command fires", async () => {
    const session = useSessionStore();
    session.session = { authenticated: true, username: "admin" };
    session.currentMember = {
      householdId: "h1", memberId: "m1", accountId: "a1",
      username: "admin", displayName: "Admin", role: "ADMIN",
      status: "ACTIVE", householdName: "测试家庭"
    };
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: "/", name: "home", component: { render: () => h("div", "系统状态") } },
        { path: "/profile", name: "profile", component: { render: () => h("div", "个人资料页") } }
      ]
    });
    await router.push("/");
    await router.isReady();
    wrapper = mount(AppShell, {
      global: { plugins: [router, ElementPlus] }
    });

    await triggerUserCommand("profile");
    expect(router.currentRoute.value.name).toBe("profile");
  });
```

- [ ] **Step 2: 运行确认失败**

Run: `npm --prefix frontend test -- src/components/AppShell.test.ts`
Expected: FAIL（顶栏仍是旧徽章，`.user-trigger` 不存在，登出按钮仍独立渲染）。

- [ ] **Step 3: 改模板**

将 `AppShell.vue` 模板中 `<div class="header-right">` 内的角色徽章与登出按钮替换为显示名下拉：

```vue
          <el-dropdown trigger="click" @command="onUserCommand">
            <button class="user-trigger" type="button">
              {{ session.currentMember?.displayName || "-" }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>登出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
```

- [ ] **Step 4: 改 script**

- 删除 `roleLabel` computed（整块）。
- 删除 `SwitchButton` 的 import（登出按钮已移除），保留 `ArrowDown`。
- 新增 `onUserCommand`：

```typescript
function onUserCommand(command: string) {
  if (command === "profile") {
    router.push({ name: "profile" });
  } else if (command === "logout") {
    onLogout();
  }
}
```

- [ ] **Step 5: 新增 scoped 样式**

在 `AppShell.vue` 文件末尾 `</script>` 之后追加：

```vue
<style scoped>
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-sm);
  background: var(--zj-surface);
  color: var(--zj-ink-600);
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--zj-dur-fast) var(--zj-ease-out),
              color var(--zj-dur-fast) var(--zj-ease-out);
}
.user-trigger:hover {
  border-color: var(--zj-pine-600);
  color: var(--zj-pine-600);
}
</style>
```

- [ ] **Step 6: 运行确认通过**

Run: `npm --prefix frontend test -- src/components/AppShell.test.ts`
Expected: PASS。

- [ ] **Step 7: 提交**

```bash
git add frontend/src/components/AppShell.vue frontend/src/components/AppShell.test.ts
git commit -m "feat(frontend): 顶栏显示名下拉收纳个人资料与登出"
```

---

### Task 6: 前端 — 个人资料页增强

**Files:**
- Modify: `frontend/src/views/ProfilePage.vue`
- Test: `frontend/src/views/ProfilePage.test.ts`

- [ ] **Step 1: 改失败组件测试**

整体重写 `ProfilePage.test.ts`（替换为以下内容）：

```typescript
import ElementPlus from "element-plus";
import { flushPromises, mount, type VueWrapper } from "@vue/test-utils";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import ProfilePage from "./ProfilePage.vue";

const pushMock = vi.fn();
const clearLocalSessionMock = vi.fn();
const refreshCurrentMemberMock = vi.fn();

const currentMember = {
  householdId: "h1",
  memberId: "m1",
  accountId: "a1",
  username: "owner",
  displayName: "所有者",
  role: "OWNER" as const,
  status: "ACTIVE" as const,
  householdName: "我的家"
};

vi.mock("vue-router", () => ({
  useRouter: () => ({ push: pushMock })
}));

vi.mock("../api/auth", () => ({
  authApi: { changePassword: vi.fn(), updateDisplayName: vi.fn() }
}));

vi.mock("../api/household", () => ({
  householdApi: { getCurrentMember: vi.fn() }
}));

vi.mock("../stores/session", () => ({
  useSessionStore: () => ({
    currentMember,
    role: "OWNER",
    clearLocalSession: clearLocalSessionMock,
    refreshCurrentMember: refreshCurrentMemberMock
  })
}));

const changePasswordMock = vi.mocked(authApi.changePassword);
const updateDisplayNameMock = vi.mocked(authApi.updateDisplayName);
const getCurrentMemberMock = vi.mocked(householdApi.getCurrentMember);

describe("ProfilePage", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    pushMock.mockReset();
    clearLocalSessionMock.mockReset();
    refreshCurrentMemberMock.mockReset().mockResolvedValue(currentMember);
    changePasswordMock.mockReset().mockResolvedValue(undefined);
    updateDisplayNameMock.mockReset().mockResolvedValue(undefined);
    getCurrentMemberMock.mockReset().mockResolvedValue(currentMember);
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
  });

  it("shows username, role and editable display name", () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    expect(wrapper.text()).toContain("owner");
    expect(wrapper.text()).toContain("所有者");
    const nameInput = wrapper.find(".name-edit input");
    expect((nameInput.element as HTMLInputElement).value).toBe("所有者");
  });

  it("updates display name and refreshes current member", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    await wrapper.find(".name-edit input").setValue("新名字");
    await wrapper.find(".name-edit .el-button").trigger("click");
    await flushPromises();

    expect(updateDisplayNameMock).toHaveBeenCalledWith({ displayName: "新名字" });
    expect(refreshCurrentMemberMock).toHaveBeenCalledOnce();
  });

  it("rejects blank display name without calling the api", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    await wrapper.find(".name-edit input").setValue("   ");
    await wrapper.find(".name-edit .el-button").trigger("click");
    await flushPromises();

    expect(updateDisplayNameMock).not.toHaveBeenCalled();
  });

  it("clears only the local session and navigates to login after changing password", async () => {
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[1].setValue("old-secret");
    await inputs[2].setValue("new-secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(changePasswordMock).toHaveBeenCalledWith({
      currentPassword: "old-secret",
      newPassword: "new-secret"
    });
    expect(clearLocalSessionMock).toHaveBeenCalledOnce();
    expect(pushMock).toHaveBeenCalledWith({ name: "login" });
  });

  it("keeps the local session and current route when changing password fails", async () => {
    changePasswordMock.mockRejectedValue(new Error("password change failed"));
    wrapper = mount(ProfilePage, { global: { plugins: [ElementPlus] } });
    const inputs = wrapper.findAll("input");
    await inputs[1].setValue("old-secret");
    await inputs[2].setValue("new-secret");
    await wrapper.find("form").trigger("submit");
    await flushPromises();

    expect(clearLocalSessionMock).not.toHaveBeenCalled();
    expect(pushMock).not.toHaveBeenCalled();
  });
});
```

注意：新增了显示名输入框后，页面里有两个输入框（显示名、当前密码），原密码测试的 `inputs[0]/[1]` 索引改为 `inputs[1]/[2]`。若显示名输入框带 maxlength 仍属于 `input` 选择器（是），索引顺序以模板 DOM 为准。

- [ ] **Step 2: 运行确认失败**

Run: `npm --prefix frontend test -- src/views/ProfilePage.test.ts`
Expected: FAIL（页面还没有显示名输入框 `.name-edit`，角色/用户名未展示）。

- [ ] **Step 3: 改模板**

整体替换 `ProfilePage.vue` 的 `<template>`：

```vue
<template>
  <div class="page-container-narrow">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">管理你的个人信息与账户安全。</p>
      </div>
    </div>

    <el-card>
      <template #header>个人信息</template>
      <div class="info-row">
        <span class="info-label">用户名</span>
        <span class="info-value">{{ username }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">角色</span>
        <span class="zj-badge zj-badge-pine">{{ roleLabel }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">显示名</span>
        <div class="name-edit">
          <el-input v-model="profile.displayName" maxlength="100" @keyup.enter="saveDisplayName" />
          <el-button type="primary" :loading="savingName" @click="saveDisplayName">保存</el-button>
        </div>
      </div>
    </el-card>

    <el-card>
      <h2 class="auth-title">修改密码</h2>
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
```

- [ ] **Step 4: 改 script**

整体替换 `ProfilePage.vue` 的 `<script setup>`：

```vue
<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { authApi } from "../api/auth";
import { householdApi } from "../api/household";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const savingName = ref(false);
const form = reactive({ currentPassword: "", newPassword: "" });
const profile = reactive({ displayName: "" });

const username = computed(() => session.currentMember?.username ?? "-");
const roleLabel = computed(() => {
  switch (session.role) {
    case "OWNER":
      return "所有者";
    case "ADMIN":
      return "管理员";
    case "MEMBER":
      return "成员";
    default:
      return "访客";
  }
});

function syncDisplayName() {
  profile.displayName = session.currentMember?.displayName ?? "";
}
syncDisplayName();

async function saveDisplayName() {
  const name = profile.displayName.trim();
  if (!name) {
    ElMessage.error("显示名不能为空");
    return;
  }
  savingName.value = true;
  try {
    await authApi.updateDisplayName({ displayName: name });
    await session.refreshCurrentMember();
    syncDisplayName();
    ElMessage.success("显示名已更新");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    savingName.value = false;
  }
}

async function submit() {
  loading.value = true;
  try {
    await authApi.changePassword(form);
    ElMessage.success("密码已修改，请重新登录");
    session.clearLocalSession();
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>
```

- [ ] **Step 5: 新增 scoped 样式**

替换 `ProfilePage.vue` 末尾的空 `<style scoped>`：

```vue
<style scoped>
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  gap: 16px;
}
.info-label {
  color: var(--zj-ink-400);
  font-size: 13px;
  flex-shrink: 0;
}
.info-value {
  color: var(--zj-ink-600);
  font-size: 13px;
}
.name-edit {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 280px;
}
</style>
```

- [ ] **Step 6: 运行确认通过**

Run: `npm --prefix frontend test -- src/views/ProfilePage.test.ts`
Expected: PASS。

- [ ] **Step 7: 全量前端单测 + 构建**

Run: `npm --prefix frontend test` 和 `npm --prefix frontend run build`
Expected: PASS（含 `AppShell.test.ts`、`ProfilePage.test.ts` 及既有全部组件测试）。

- [ ] **Step 8: 提交**

```bash
git add frontend/src/views/ProfilePage.vue frontend/src/views/ProfilePage.test.ts
git commit -m "feat(frontend): 个人资料页展示并支持编辑显示名"
```

---

### Task 7: e2e — 适配登出下拉流程

**Files:**
- Modify: `frontend/e2e/helpers.ts`
- Modify: `frontend/e2e/login.spec.ts`
- Modify: `frontend/e2e/bootstrap.spec.ts`

- [ ] **Step 1: 改 helpers**

在 `frontend/e2e/helpers.ts`：

1. `waitForAppReady` 的 `Promise.race` 中，把 `page.getByRole("button", { name: "登出" }).waitFor(...)` 替换为：

```typescript
    page.locator(".user-trigger").waitFor({ state: "visible", timeout: 15_000 })
```

2. `bootstrapViaUi` 末行 `await expect(page.getByText("所有者")).toBeVisible();` 改为：

```typescript
  await expect(page.getByText("E2E所有者")).toBeVisible();
```

3. `loginViaUi` 末行 `await expect(page.getByRole("button", { name: "登出" })).toBeVisible();` 改为：

```typescript
  await expect(page.locator(".user-trigger")).toBeVisible();
```

4. `ensureBootstrapped` 中的登录态判断 `await page.getByRole("button", { name: "登出" }).isVisible()` 改为：

```typescript
  if (await page.locator(".user-trigger").isVisible().catch(() => false)) {
    return;
  }
```

- [ ] **Step 2: 改 login.spec.ts**

将 `login.spec.ts` 中登出流程（原第 10-18 行）替换为：

```typescript
  await page.locator(".user-trigger").click();
  await page.locator(".el-dropdown-menu__item").filter({ hasText: "登出" }).click();
  // The logout flow keeps an ElMessageBox.confirm() step; the confirm button
  // reuses the "登出" text, so we scope to the dialog. The dropdown menu item
  // has already closed by now, so the dialog button is unambiguous.
  const logoutDialog = page.locator(".el-message-box");
  await expect(logoutDialog).toBeVisible();
  await logoutDialog.getByRole("button", { name: "登出" }).click();
  await expect(page).toHaveURL(/login/);
```

并将第 40 行 `await expect(page.getByText("所有者")).toBeVisible();` 改为 `await expect(page.getByText("E2E所有者")).toBeVisible();`。

- [ ] **Step 3: 改 bootstrap.spec.ts**

将第 16 行 `await expect(page.getByText("所有者")).toBeVisible();` 改为 `await expect(page.getByText("E2E所有者")).toBeVisible();`。

- [ ] **Step 4: 跑 e2e 验证**

Run: `make e2e-smoke`
Expected: 全部 Playwright 用例 PASS（含 bootstrap、login、members、zz-owner-recovery）。

- [ ] **Step 5: 提交**

```bash
git add frontend/e2e/helpers.ts frontend/e2e/login.spec.ts frontend/e2e/bootstrap.spec.ts
git commit -m "test(e2e): 登出流程改走顶栏显示名下拉"
```

---

### Task 8: e2e — 个人资料改名冒烟

**Files:**
- Create: `frontend/e2e/profile.spec.ts`

- [ ] **Step 1: 新建 e2e 用例**

创建 `frontend/e2e/profile.spec.ts`：

```typescript
import { expect, test } from "@playwright/test";
import { ensureBootstrapped, owner } from "./helpers";

test("owner edits display name from profile and header reflects it", async ({ page }) => {
  await ensureBootstrapped(page);

  await page.locator(".user-trigger").click();
  await page.locator(".el-dropdown-menu__item").filter({ hasText: "个人资料" }).click();
  await expect(page.getByRole("heading", { name: "个人资料" })).toBeVisible();

  const nameInput = page.locator(".name-edit input");
  await expect(nameInput).toHaveValue(owner.displayName);

  await nameInput.fill("E2E所有者2");
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.locator(".user-trigger")).toContainText("E2E所有者2");

  // Revert so later specs see the canonical owner display name.
  await nameInput.fill(owner.displayName);
  await page.getByRole("button", { name: "保存" }).click();
  await expect(page.locator(".user-trigger")).toContainText(owner.displayName);
});
```

- [ ] **Step 2: 跑 e2e 验证**

Run: `make e2e-smoke`
Expected: 全部 Playwright 用例 PASS（`profile.spec.ts` 改名后已还原，不影响其他用例）。

- [ ] **Step 3: 提交**

```bash
git add frontend/e2e/profile.spec.ts
git commit -m "test(e2e): 个人资料改名冒烟用例"
```

---

### Task 9: 全量验证

**Files:** 无

- [ ] **Step 1: 跑 make verify**

Run: `make verify`
Expected: layout check、backend 全量测试、frontend 全量测试与构建、生产构建、`git diff --check` 全部 PASS。

- [ ] **Step 2: 处理任何失败**

若 `make verify` 失败，按失败类型修复：布局/模块边界问题（ModularityTests）、前后端测试、构建错误或 whitespace/editorconfig 违规。修复后重跑 `make verify` 直至全绿。

---

## Self-Review

**1. Spec coverage:**
- 顶栏显示名（空则 `-`）→ Task 5（AppShell 模板 `.user-trigger`）
- 角色徽章移除、角色信息移到个人资料 → Task 5 删除 roleLabel / Task 6 展示角色
- 下拉含 个人资料 + 登出、独立登出按钮删除 → Task 5
- 个人资料展示 用户名/显示名/角色、支持改显示名 → Task 6
- 改完即时刷新顶栏 → Task 4 store `refreshCurrentMember` + Task 6 调用
- 空显示名 `-` 防御 + 必填（前端/后端校验）→ Task 1 `@NotBlank`、Task 6 客户端 trim 校验；DB 层 V1 已 `NOT NULL`，无需迁移
- identity 模块端点 + 会话 principal 刷新（审计不脏）→ Task 3
- e2e 适配登出下拉 + 新用例 → Task 7、Task 8

**2. Placeholder scan:** 所有步骤含完整代码、路径、命令与预期输出。Task 3 Step 6 中集成测试的 CSRF cookie 取值来源标注了"以运行结果为准"，属明确的运行期调整点而非占位。

**3. Type consistency:**
- `updateDisplayName(UUID, String)` 在 Task 1 mapper、Task 2 service、Task 3 controller 签名一致
- `UpdateDisplayNameRequest`（`displayName` 字段）在 Task 1 定义、Task 3 controller 使用、Task 4 前端类型/API 呼应
- 前端 `refreshCurrentMember(): Promise<CurrentMember>` 在 Task 4 定义、Task 5/6 使用
- AppShell 测试与 e2e 统一用 `.user-trigger`、`.el-dropdown-menu__item` 选择器
- ProfilePage 测试与 e2e 统一用 `.name-edit input` 与"保存"按钮
