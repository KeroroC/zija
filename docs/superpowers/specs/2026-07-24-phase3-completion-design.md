# 阶段三补全实现计划

## 概述

补全阶段三审计中发现的所有缺失功能，包括后端端点、前端页面、API 函数和测试。

## 现状说明（悬挂引用与已完成工作）

代码中已有部分"前端已实现但后端端点缺失"的悬挂引用，批次一落地后即点亮：

| 前端函数/按钮 | 状态 | 依赖 |
|--------------|------|------|
| `restoreBrand()` in `catalog.ts:101` | ✅ 已存在 | ❌ 后端 `POST /brands/{id}/restore` 缺失 |
| `restoreTag()` in `catalog.ts:135` | ✅ 已存在 | ❌ 后端 `POST /tags/{id}/restore` 缺失 |
| 品牌恢复按钮 in `CatalogSettingsPage.vue:59` | ✅ 已存在 | ❌ 同上 |
| 标签恢复按钮 in `CatalogSettingsPage.vue:115` | ✅ 已存在 | ❌ 同上 |
| `archiveBrand()` / `archiveTag()` | ✅ 已存在 | ✅ 后端已实现 |
| `renameCategory` / `renameUnit` / `renameTag` | ✅ 已存在 | ✅ 后端已实现 |
| 分类/单位/标签重命名对话框 | ✅ 已存在 | — |
| `renameBrand()` | ❌ 不存在 | 后端 `PUT /brands/{id}` 已存在 |
| `archiveUnit()` / `restoreUnit()` | ❌ 不存在 | ❌ 后端端点缺失 |

## 分批策略

按依赖关系分为 5 批，每批完成后验证：

1. **批次一：后端端点和修复** — 无前端依赖，可独立验证
2. **批次二：前端 API 函数** — 依赖批次一的后端端点
3. **批次三：前端页面补全** — 依赖批次二的 API 函数
4. **批次四：Vitest 单元测试** — 依赖批次三的页面代码
5. **批次五：Playwright E2E 测试** — 依赖所有前序批次

---

## 批次一：后端端点和修复

### 1.1 Catalog 字典端点补全

**文件：** `backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryController.java`

新增 4 个端点，完全遵循现有 archive/restore 模式：

| 端点 | 方法签名 | 委托调用 |
|------|----------|----------|
| `POST /brands/{id}/restore` | `restoreBrand(principal, id, VersionRequest)` | `dictionaryService.restoreBrand(...)` |
| `POST /units/{id}/archive` | `archiveUnit(principal, id, VersionRequest)` | `dictionaryService.archiveUnit(...)` |
| `POST /units/{id}/restore` | `restoreUnit(principal, id, VersionRequest)` | `dictionaryService.restoreUnit(...)` |
| `POST /tags/{id}/restore` | `restoreTag(principal, id, VersionRequest)` | `dictionaryService.restoreTag(...)` |

**文件：** `backend/src/main/java/com/zija/catalog/internal/CatalogDictionaryService.java`

新增 4 个方法，遵循现有模式：

```java
// restoreBrand: requireBrand → setStatus("ACTIVE") → updateById → audit("BRAND_RESTORED")
// archiveUnit: requireUnit → setStatus("ARCHIVED") → updateById → audit("UNIT_ARCHIVED")
// restoreUnit: requireUnit → setStatus("ACTIVE") → updateById → audit("UNIT_RESTORED")
// restoreTag: requireTag → setStatus("ACTIVE") → updateById → audit("TAG_RESTORED")
```

所有方法：`@Transactional`，使用 `require{Entity}(householdId, id)` 校验归属，乐观锁通过 `updateById` 返回值 == 0 检测冲突。

### 1.2 OwnerRecoveryController.inspect() 修复

**文件：** `backend/src/main/java/com/zija/household/internal/OwnerRecoveryController.java`

**当前问题：** `ownerDisplayName` 硬编码为 `null`。

**修复方案：** 在 Controller 中注入 `IdentityApi`，inspect 方法中当 token 存在时调用 `identityApi.findById(token.get().getAccountId())` 获取显示名。

```java
// 修改 inspect 方法：
// 1. 注入 IdentityApi (通过构造器)
// 2. token 存在时: identityApi.findById(token.get().getAccountId())
//    .map(AccountInfo::displayName)
//    .orElse(null)
```

### 1.3 MemberController.list() 补全 createdAt

**文件：** `backend/src/main/java/com/zija/household/internal/MemberController.java`

**修改：**
1. `MemberResponse` 记录添加 `OffsetDateTime createdAt` 字段
2. 映射 lambda 中添加 `m.getCreatedAt()`

**消费方：** 前端 MembersPage 可用于显示成员加入时间。

### 1.4 HouseholdController.me() 补全 householdName

**文件：** `backend/src/main/java/com/zija/household/internal/HouseholdController.java`

**修改：**
1. `CurrentMemberResponse` 记录添加 `String householdName` 字段
2. `me()` 方法中通过 `householdService.findHousehold()` 获取家庭名称并填入响应

**消费方：** AppShell 显示家庭名称。

---

## 批次二：前端 API 函数

**文件：** `frontend/src/api/catalog.ts`

新增 3 个函数：

```typescript
// renameBrand — PUT /api/v1/brands/{id}，body: { name, version }
export function renameBrand(id: string, name: string, version: number): Promise<void> {
  return putJson(`/api/v1/brands/${id}`, { name, version });
}

// archiveUnit — POST /api/v1/units/{id}/archive，body: { version }
export function archiveUnit(id: string, version: number): Promise<void> {
  return postJson(`/api/v1/units/${id}/archive`, { version });
}

// restoreUnit — POST /api/v1/units/{id}/restore，body: { version }
export function restoreUnit(id: string, version: number): Promise<void> {
  return postJson(`/api/v1/units/${id}/restore`, { version });
}
```

**文件：** `frontend/src/types/identity.ts`

`CurrentMember` 接口添加 `householdName: string` 字段（配合 1.4）。

---

## 批次三：前端页面补全

### 3.1 ItemsPage.vue

#### 3.1.0 字典名称显示方案

**问题：** 后端 `ItemController.toItemResponse` 只返回 ID（`categoryId`/`brandId`/`unitId`/`tagIds`），不返回名称。前端 `CatalogItem` 类型也只有 ID。

**方案 B（纯前端查找表）：** 在 ItemsPage 的 `onMounted` 中并行调用 `fetchCategories()`、`fetchBrands()`、`fetchUnits()`、`fetchTags()` 构建 `Map<id, name>` 查找表。表格列和详情抽屉通过查找表将 ID 解析为名称。

**优点：** 无需后端改动，与现有字典加载模式一致。
**缺点：** 需额外加载字典数据，但已在 ItemFormDrawer 中有先例。

#### 3.1.1 表格列补全

在现有列基础上新增：

| 列 | 数据来源 | 宽度 | 显示逻辑 |
|----|----------|------|----------|
| 封面 | `item.coverUrl` | 60px | 缩略图 `<img>`，无封面时显示占位符 |
| 分类 | 查找表 `categoryMap[item.categoryId]` | 120px | 文本，无分类时显示 "—" |
| 品牌 | 查找表 `brandMap[item.brandId]` | 100px | 文本，无品牌时显示 "—" |
| 单位 | 查找表 `unitMap[item.unitId]` | 80px | 文本 |
| 标签 | 查找表 `tagMap[item.tagIds]` | 150px | `el-tag` 列表，最多显示 2 个 + "+N" |
| 低库存阈值 | `item.lowStockThreshold` | 100px | 仅消耗品显示 |

#### 3.1.2 筛选控件补全

在现有筛选栏新增：

- **分类筛选：** `el-tree-select`，数据来自 `fetchCategories()`
- **品牌筛选：** `el-select`，数据来自 `fetchBrands()`
- **标签筛选：** `el-select` multiple，数据来自 `fetchTags()`
- **排序：** `el-select` with options: 名称↑↓、创建时间↑↓、更新时间↑↓

#### 3.1.3 详情抽屉补全

在现有字段基础上新增显示：

- 封面图片（大图）
- 分类名称（查找表解析）
- 品牌名称（查找表解析）
- 单位名称及小数精度（查找表解析）
- 标签列表（查找表解析）
- 临期提醒配置（模式 + 天数）
- 低库存配置（模式 + 阈值）
- 创建时间

#### 3.1.4 响应式列隐藏

添加 CSS 媒体查询 `@media (max-width: 1024px)` 隐藏次要列（品牌、单位、标签、低库存阈值）。

### 3.2 CatalogSettingsPage.vue

#### 3.2.1 分类移动 UI

- 在分类树节点操作区添加"移动"按钮（图标：`Rank` 或 `Switch`）
- 实现方式：**复制** LocationsPage 的移动对话框逻辑到 CatalogSettingsPage（LocationsPage 的移动对话框是内联实现，非共享组件），调整为目标分类树 + sortOrder 输入
- 调用已有的 `moveCategory` API

#### 3.2.2 品牌重命名

- 在品牌表格操作列添加"重命名"按钮
- 扩展现有的共享重命名对话框，使 `renameForm.type` 支持 `'brand'`
- 调用新增的 `renameBrand` API

#### 3.2.3 单位归档/恢复

- 在单位表格操作列添加"归档"/"恢复"按钮
- 归档前 `ElMessageBox.confirm` 确认
- 调用新增的 `archiveUnit`/`restoreUnit` API

### 3.3 ItemFormDrawer.vue

#### 3.3.1 临期提醒天数多值输入

- 将 `el-input-number` 替换为 `el-select` + `allow-create` + `multiple`（与 tagIds 输入方式一致）
- 修改 `form.expiryReminderDays` 类型为 `number[]`
- 修改 `fillForm` 直接赋值数组（不再取 `[0]`）
- 修改 `buildSubmitData` 直接传递数组（不再包装）
- **校验规则**（来自设计 §4.3）：必须是 1–3650 的互异正整数，按从大到小保存；`INHERIT` 和 `DISABLED` 模式不保存数组

#### 3.3.2 动态单位精度显示

- 当选择了单位后，查找对应单位的 `decimalScale`
- 在低库存阈值输入框旁动态显示精度提示："精度：{decimalScale} 位小数"
- 可选：将 `el-input` 改为 `el-input-number` 并设置 `precision` 属性

### 3.4 LocationsPage.vue

#### 3.4.1 详情面板补全

- 显示祖先路径：从根到当前节点的面包屑（利用 `LocationNode.parentId` 从树数据中向上遍历构建路径）
- 显示子节点列表：当前节点的直接子节点名称列表（利用 `LocationNode.children`）

#### 3.4.2 删除按钮子节点预检

- 在 `deleteNode` 中，调用 API 前先检查当前节点是否有子节点（`data.children.length > 0`）
- 如果有子节点，显示提示 "该位置下有 N 个子位置，请先删除子位置" 并阻止删除
- **注意：** 后端 `DELETE /locations/{id}` 已通过 `LocationHasChildrenException` 返回 `LOCATION_HAS_CHILDREN`（409）强制拦截，前端预检仅为 UX 优化
- 保留现有的 `everReferenced` 检查

### 3.5 AppShell.vue

**方案（合并为单一实现）：**
1. 后端 `CurrentMemberResponse` 添加 `householdName` 字段（批次一 1.4）
2. 前端 `CurrentMember` 类型添加 `householdName` 字段（批次二）
3. AppShell 的 `onMounted` 中调用 `householdApi.getCurrentMember()` 获取家庭名称
4. 将硬编码的 "我的家" 替换为 `currentMember.householdName`

**数据来源：** `HouseholdService.findHousehold()` → `HouseholdEntity.getName()`

---

## 批次四：Vitest 单元测试

遵循现有 `MembersPage.test.ts` 模式。

### 4.1 ItemsPage.test.ts

测试场景：
1. 加载时调用 fetchItems 并渲染表格
2. 筛选控件变化时重新请求（q、managementType、categoryId、brandId、tagId）
3. 分页切换时重新请求
4. 点击行打开详情抽屉，显示完整字段（含字典名称解析）
5. 归档确认对话框 → 调用 archiveItem
6. 恢复操作 → 调用 restoreItem
7. 角色可见性：**MEMBER 能看到新建按钮**（设计 §6.1 权限矩阵：新建/编辑/归档/恢复物品，Owner/Admin/Member 均为"是"）

Mock：`vi.mock('../api/catalog')`，mock 所有 API 函数。

### 4.2 CatalogSettingsPage.test.ts

测试场景：
1. 加载时渲染四个 tab
2. 管理员可见所有操作按钮
3. **路由级权限**：`/settings/catalog` 路由仅对 OWNER/ADMIN 显示（AppShell.vue:25），MEMBER 根本无法进入该页；测试应验证 MEMBER 视角下该菜单项不可见/禁用
4. 品牌重命名对话框提交调用 renameBrand
5. 单位归档确认调用 archiveUnit

### 4.3 LocationsPage.test.ts

测试场景：
1. 加载时渲染位置树
2. 新增子位置对话框提交调用 createLocation
3. 重命名对话框提交调用 renameLocation
4. 移动对话框提交调用 moveLocation
5. 删除确认调用 deleteLocation
6. API 错误时显示错误提示

---

## 批次五：Playwright E2E 测试

遵循现有 `members.spec.ts` 模式。

### 5.1 catalog.spec.ts

测试流程：
1. ensureBootstrapped → loginViaUi
2. 导航到目录设置页
3. 创建分类 → 创建品牌 → 创建单位 → 创建标签
4. 导航到物品页 → 创建物品（选择上述分类/品牌/单位/标签）
5. 上传封面图片
6. 归档物品 → 恢复物品
7. 归档分类 → 恢复分类
8. 归档品牌 → 恢复品牌
9. 归档单位 → 恢复单位
10. 归档标签 → 恢复标签

### 5.2 locations.spec.ts

测试流程：
1. ensureBootstrapped → loginViaUi
2. 导航到位置管理页
3. 创建根位置 → 创建子位置 → 创建孙位置
4. 重命名位置
5. 移动位置到新父节点
6. 验证循环引用被拒绝（尝试将父节点移到子节点下）
7. 删除空叶子节点
8. 验证有子节点的位置不能删除（后端返回 LOCATION_HAS_CHILDREN）

---

## 验证计划

每批次完成后：
1. `make backend-test` — 后端测试通过
2. `make frontend-test` — 前端测试通过
3. `make verify` — 全量验证（含类型检查、生产构建）
4. 手动检查关键路径（如有运行环境）

## 风险和注意事项

1. **ItemsPage 字典名称**：采用方案 B（前端查找表），需在页面加载时预取字典数据，与 ItemFormDrawer 模式一致
2. **分类移动对话框**：LocationsPage 的移动对话框是内联实现，需复制而非共享
3. **临期提醒校验**：需实现 1–3650 互异正整数、降序保存的校验逻辑
4. **LocationsPage 祖先路径**：需要从扁平树数据中递归构建路径，利用 `parentId` 和 `children` 字段
5. **E2E 测试**：需要确保测试环境有正确的种子数据和认证流程
