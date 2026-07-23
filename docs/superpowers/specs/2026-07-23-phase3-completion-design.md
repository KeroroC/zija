# 阶段三补齐：缺失功能与页面 设计方案

- **状态：** 已批准
- **日期：** 2026-07-23
- **基础规格：** `docs/superpowers/specs/2026-07-22-phase3-items-locations-design.md`
- **基础计划：** `docs/superpowers/plans/2026-07-22-phase3-items-locations.md`

## 1. 目标与范围

补齐阶段三实施计划中未完成的功能、页面和测试。共分三轮：

| 轮次 | 范围 | 产出 |
|---|---|---|
| 第一轮 | 后端 API 补全 + 后端测试 | ~15 个文件 |
| 第二轮 | 前端页面/组件 + API 模块 | ~6 个文件 |
| 第三轮 | 前端测试 + E2E 测试 | ~4 个文件 |

### 不在范围

- 物品分页列表端点 `GET /items`（已存在，有集成测试覆盖）
- 物品更新端点 `PUT /items/{id}`（已存在，有集成测试覆盖）
- 位置重命名/移动端点（已存在）

## 2. 第一轮：后端 API 补全

### 2.1 字典编辑端点

所有编辑端点需 `@RequireAdmin`，请求体含 `version` 用于乐观锁。

**PUT /api/v1/categories/{id}** — 重命名分类
- 请求体：`{ name: string, version: integer }`
- 名称归一化（NFKC + lowercase），检查同父级下重复
- 调用 `CatalogDictionaryService.updateCategory()`

**PUT /api/v1/categories/{id}/position** — 移动分类
- 请求体：`{ parentId: UUID|null, sortOrder: integer, version: integer }`
- 循环检查：不能移到自身或后代（使用 CategoryMapper.xml 的 `findDescendantIds`）
- 调用 `CatalogDictionaryService.moveCategory()`

**PUT /api/v1/brands/{id}** — 重命名品牌
- 请求体：`{ name: string, version: integer }`
- 调用 `CatalogDictionaryService.updateBrand()`

**PUT /api/v1/units/{id}** — 更新单位（仅名称）
- 请求体：`{ name: string, version: integer }`
- decimalScale 不可修改（已有物品引用时锁定）
- 调用 `CatalogDictionaryService.updateUnit()`

**PUT /api/v1/tags/{id}** — 重命名标签
- 请求体：`{ name: string, version: integer }`
- 调用 `CatalogDictionaryService.updateTag()`

### 2.2 物品封面端点

**POST /api/v1/items/{id}/cover** — 上传/替换封面
- multipart/form-data，字段 `file`
- 流程：FileApi.store() → 若已有封面则 FileApi.release() 旧文件 → 更新 cover_file_id → FileApi.retain() 新文件
- 返回文件信息（id、url、detectedMediaType 等）
- 需 `@RequireMember`

**DELETE /api/v1/items/{id}/cover** — 移除封面
- 调用 FileApi.release() 释放引用
- 将 cover_file_id 置 null
- 请求体含 `{ version: integer }`
- 需 `@RequireMember`

### 2.3 CategoryMapper.xml

创建递归 CTE 查询：

```xml
<!-- findTree: 获取完整分类树 -->
<select id="findTree">
  WITH RECURSIVE tree AS (
    SELECT ... FROM catalog_category WHERE household_id = #{householdId} AND parent_id IS NULL
    UNION ALL
    SELECT c.* FROM catalog_category c INNER JOIN tree t ON c.parent_id = t.id
  )
  SELECT * FROM tree ORDER BY sort_order, id
</select>

<!-- findDescendantIds: 获取所有后代 ID（用于循环检查） -->
<select id="findDescendantIds">
  WITH RECURSIVE descendants AS (
    SELECT id FROM catalog_category WHERE id = #{categoryId} AND household_id = #{householdId}
    UNION ALL
    SELECT c.id FROM catalog_category c INNER JOIN descendants d ON c.parent_id = d.id
  )
  SELECT id FROM descendants
</select>
```

### 2.4 后端单元测试

| 测试文件 | 覆盖场景 |
|---|---|
| FileServiceTest | 上传有效文件、retain 增引用、release 减引用归零删除、release 有引用不删除、拒绝超大文件、拒绝不支持类型 |
| FileControllerTest | 上传返回文件信息、下载需认证、下载 404、删除 |
| CatalogDictionaryServiceTest | 名称归一化、重复拒绝、分类有子不能归档、单位精度越界拒绝、品牌/标签创建归一化 |
| ItemServiceTest | 创建物品关联归档字典拒绝、阈值精度超限拒绝、封面绑定/移除、归档/恢复 |
| CatalogDictionaryControllerTest | 需认证、Admin 可创建/归档、Member 可新增品牌标签、Member 不能归档 |
| LocationServiceTest | 创建/重命名/移动/删除、循环拒绝、有子不能删、已引用不能删、版本冲突 |
| LocationControllerTest | 树查询、创建、重命名、移动、删除、未认证 |

### 2.5 后端集成测试

| 测试文件 | 覆盖场景 |
|---|---|
| FileServiceIntegrationTest | Testcontainers：真实文件存储读取、引用计数数据库一致性 |
| CatalogDictionaryIntegrationTest | Testcontainers：分类树递归查询、字典 CRUD + 乐观锁 |
| LocationIntegrationTest | Testcontainers：位置树构建、markReferenced 阻止删除 |

## 3. 第二轮：前端页面/组件

### 3.1 api/file.ts

```typescript
export async function uploadItemCover(itemId: string, file: File): Promise<UploadedFile>
export async function removeItemCover(itemId: string, version: number): Promise<void>
```

使用 FormData 上传，统一错误处理。

### 3.2 ItemFormDrawer.vue

抽屉组件，新建/编辑物品：
- 必填：名称、管理类型（CONSUMABLE/DURABLE）、单位
- 可选：分类（树形选择器）、品牌（可新建）、标签（多选，可新建）、备注、封面
- 临期提醒：INHERIT/DISABLED/CUSTOM 模式，CUSTOM 显示天数
- 低库存：INHERIT/DISABLED/CUSTOM 模式，CUSTOM 显示阈值（精度提示）
- 调用 POST /items 或 PUT /items/{id}

### 3.3 ItemCoverUpload.vue

封面管理组件：
- 预览区域（缩略图/占位符）
- 选择文件 + 前端类型/大小预检
- 上传进度、替换、移除（需确认）
- 服务端错误码呈现

### 3.4 CatalogSettingsPage.vue

Owner/Admin 专用，四个 Tab：
- 分类：树形展示，新增子分类、重命名、移动、归档/恢复
- 品牌：列表，新增、重命名、归档
- 单位：列表，新增（含小数精度）、重命名
- 标签：列表，新增、重命名、归档

### 3.5 LocationMoveDialog.vue

选择目标父节点和排序位置的对话框：
- 显示位置树供选择
- 循环/并发失败提示
- 确认后调用 PUT /locations/{id}/position

### 3.6 路由与导航

- 新增路由 `/settings/catalog` → CatalogSettingsPage
- AppShell.vue "家庭设置"菜单项取消 disabled
- 仅 Owner/Admin 可见

## 4. 第三轮：前端测试 + E2E

### 4.1 Vitest 测试

| 测试文件 | 覆盖场景 |
|---|---|
| ItemsPage.test.ts | 筛选、分页、详情抽屉、归档确认、角色可见性 |
| CatalogSettingsPage.test.ts | 管理员控制可见、普通成员无入口 |
| LocationsPage.test.ts | 树加载、新增、重命名、移动确认、API 错误提示 |

### 4.2 E2E Playwright 测试

| 测试文件 | 覆盖场景 |
|---|---|
| catalog.spec.ts | 创建分类/单位/品牌/标签 → 创建物品 → 上传封面 → 归档/恢复 |
| locations.spec.ts | 创建位置层级 → 重命名 → 移动 → 循环拒绝 → 删除空叶 |

## 5. 错误码

复用阶段三已定义的错误码，不新增：

| 错误码 | 触发场景 |
|---|---|
| CATALOG_VERSION_CONFLICT | 字典/物品更新版本不匹配 |
| CATALOG_DICTIONARY_NAME_EXISTS | 重命名后与已有字典同名 |
| CATALOG_CATEGORY_CYCLE | 分类移到自身或后代 |
| CATALOG_CATEGORY_HAS_CHILDREN | 归档含活动子分类 |
| CATALOG_UNIT_PRECISION_LOCKED | 已引用单位修改精度 |
| FILE_TOO_LARGE | 文件超 5 MiB |
| FILE_MEDIA_TYPE_UNSUPPORTED | 不支持的文件类型 |
| FILE_SIGNATURE_MISMATCH | 签名与声明类型不匹配 |
| LOCATION_CYCLE | 位置移到自身或后代 |
| LOCATION_HAS_CHILDREN | 删除含子节点位置 |
| LOCATION_REFERENCED | 删除已引用位置 |

## 6. 约束

- 所有字典编辑使用 CatalogDictionaryService.normalizeName()（NFKC + lowercase）
- 封面操作通过 FileApi retain/release，不直接操作 StoredFileMapper
- 前端不缓存文件内容到 Pinia，仅保存文件 ID 和受保护 URL
- 遵循现有 Problem Details 错误契约
- 乐观锁版本不匹配返回 409
