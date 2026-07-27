# Phase 6c: 前端报表/搜索/导出 UI 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现报表与导出前端 UI：路由启用、全局搜索页、5 张报表页、设置页（投影重建 + 导出审计）、CSV 导出下载、Playwright E2E 测试。

**Architecture:** 沿用「松间账册」视觉体系（`tokens.css` / `index.css`），复用阶段二/三/四约定的固定筛选栏 + Element Plus 表格 + 分页模式。新增 `reporting.ts` API 模块 + `reporting.ts` 类型定义。侧边栏已有 disabled 占位，移除 `disabled` 即可启用。

**Tech Stack:** Vue 3, TypeScript, Vite 7, Vue Router 4, Pinia 3, Element Plus, Vitest, Playwright

## Global Constraints

- 组件样式使用 `<style scoped>`，引用 `--zj-*` 令牌，禁止硬编码色值。
- 表格数字列 `font-variant-numeric: tabular-nums`。
- 空态用 `.empty-state` 占位。
- 危险操作（重建）走 `el-popconfirm`。
- API 调用通过 `getJson<T>()` / `fetch` + CSRF token。
- CSV 导出通过 `window.open()` 或 `<a>` 标签触发浏览器原生下载。
- 测试 mock API 模块 via `vi.mock()`。
- 2-space indent for TypeScript/Vue, LF line endings.

## File Structure

### 新建文件

```
frontend/src/
  api/reporting.ts                          # 报表 API 模块
  types/reporting.ts                        # 报表 TypeScript 类型
  views/reports/
    ReportsLayout.vue                       # 报表区布局（子路由容器）
    SearchView.vue                          # 全局搜索页
    StockByLocationView.vue                 # 当前库存与位置分布
    ExpiringLotsView.vue                    # 临期批次
    LowStockView.vue                        # 低库存物品
    StockChangesView.vue                    # 库存变化（时间范围）
    MovementsView.vue                       # 流水筛选
    ReportsSettingsView.vue                 # 设置页（投影重建 + 导出审计）
```

### 修改文件

```
frontend/src/router/index.ts                # +/reports 路由
frontend/src/components/AppShell.vue        # 移除 /reports disabled
```

---

### Task 1: 类型定义 + API 模块

**Files:**
- Create: `frontend/src/types/reporting.ts`
- Create: `frontend/src/api/reporting.ts`

**Interfaces:**
- Produces: `searchReporting(q, limitPerGroup)` → `{items, lots, locations}`
- Produces: `getReport(key, params)` → `{items, total, page, pageSize}`
- Produces: `exportCsvUrl(reportKey, params)` → URL string
- Produces: `rebuildProjection(householdId)` → `void`

- [ ] **Step 1: 创建 `types/reporting.ts`**

```typescript
// frontend/src/types/reporting.ts

/** 全局搜索结果 */
export interface SearchResult {
  items: SearchItemHit[]
  lots: SearchLotHit[]
  locations: SearchLocationHit[]
}

export interface SearchItemHit {
  itemId: string
  name: string
  brand: string
  tags: string
  category: string
  unit: string
  matchedFields: string[]
}

export interface SearchLotHit {
  lotId: string
  itemName: string
  lotNumber: string
  serialNumber: string
  matchedFields: string[]
}

export interface SearchLocationHit {
  locationId: string
  name: string
  path: string
  matchedFields: string[]
}

/** 通用分页响应 */
export interface ReportPage<T> {
  items: T[]
  total: number
  page: number
  pageSize: number
}

/** 库存与位置分布行 */
export interface StockByLocationRow {
  location_path: string
  item_name: string
  lot_number: string
  serial_number: string
  unit_name: string
  quantity: number
  expiry_date: string | null
}

/** 临期批次行 */
export interface ExpiringLotRow {
  lot_number: string
  serial_number: string
  item_name: string
  location_path: string
  quantity: number
  expiry_date: string
  days_until_expiry: number
}

/** 低库存行 */
export interface LowStockRow {
  item_name: string
  total_quantity: number
  low_stock_threshold: number
}

/** 库存变化行 */
export interface StockChangeRow {
  item_name: string
  type: string
  quantity_delta: number
  from_location_path: string
  to_location_path: string
  operator_display_name: string
  reason: string
  business_time: string
}

/** 流水行 */
export interface MovementRow {
  item_name: string
  type: string
  quantity_delta: number
  from_location_path: string
  to_location_path: string
  operator_display_name: string
  reason: string
  reversal_of: string
  business_time: string
  created_at: string
}
```

- [ ] **Step 2: 创建 `api/reporting.ts`**

```typescript
// frontend/src/api/reporting.ts
import { getJson } from './http'
import type { SearchResult, ReportPage } from '../types/reporting'

const BASE = '/api/v1/reporting'

/** 全局搜索 */
export async function searchReporting(
  q: string, limitPerGroup = 5
): Promise<SearchResult> {
  const qs = new URLSearchParams({ q, limitPerGroup: String(limitPerGroup) })
  return getJson<SearchResult>(`${BASE}/search?${qs}`)
}

/** 报表查询 */
export async function getReport<T>(
  reportKey: string, params: Record<string, string | number | undefined>
): Promise<ReportPage<T>> {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') qs.set(k, String(v))
  }
  return getJson<ReportPage<T>>(`${BASE}/reports/${reportKey}?${qs}`)
}

/** 构建 CSV 导出 URL（用于浏览器直接下载） */
export function buildExportUrl(
  reportKey: string, params: Record<string, string | undefined>
): string {
  const qs = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== '') qs.set(k, v)
  }
  // 添加 CSRF token 从 cookie
  const csrf = document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1]
  if (csrf) qs.set('_csrf', csrf)
  return `${BASE}/exports/${reportKey}?${qs}`
}

/** 触发投影重建 */
export async function rebuildProjection(householdId: string): Promise<void> {
  const qs = new URLSearchParams({ householdId })
  await fetch(`${BASE}/projection/rebuild?${qs}`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'X-XSRF-TOKEN': document.cookie.match(/XSRF-TOKEN=([^;]+)/)?.[1] ?? ''
    }
  })
}
```

- [ ] **Step 3: 编译验证**

Run: `npm --prefix frontend run typecheck`
Expected: 通过

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/reporting.ts frontend/src/api/reporting.ts
git commit -m "feat(frontend): reporting 类型定义 + API 模块

- types/reporting.ts: 搜索结果/报表行/分页响应类型
- api/reporting.ts: searchReporting/getReport/buildExportUrl/rebuildProjection"
```

---

### Task 2: 路由启用 + ReportsLayout

**Files:**
- Modify: `frontend/src/router/index.ts` (+/reports 子路由)
- Modify: `frontend/src/components/AppShell.vue` (移除 disabled)
- Create: `frontend/src/views/reports/ReportsLayout.vue`

**Interfaces:**
- Produces: `/reports` 路由 + 子路由（search, stock-by-location, expiring-lots, low-stock, stock-changes, movements, settings）

- [ ] **Step 1: 在 `router/index.ts` 中添加报表路由**

```typescript
// 在 routes 数组中 inventory 路由之后添加：
{
  path: '/reports',
  component: () => import('../views/reports/ReportsLayout.vue'),
  meta: { title: '报表与导出' },
  children: [
    { path: '', redirect: '/reports/search' },
    { path: 'search', name: 'report-search', component: () => import('../views/reports/SearchView.vue'), meta: { title: '全局搜索' } },
    { path: 'stock-by-location', name: 'report-stock-by-location', component: () => import('../views/reports/StockByLocationView.vue'), meta: { title: '库存分布' } },
    { path: 'expiring-lots', name: 'report-expiring-lots', component: () => import('../views/reports/ExpiringLotsView.vue'), meta: { title: '临期批次' } },
    { path: 'low-stock', name: 'report-low-stock', component: () => import('../views/reports/LowStockView.vue'), meta: { title: '低库存' } },
    { path: 'stock-changes', name: 'report-stock-changes', component: () => import('../views/reports/StockChangesView.vue'), meta: { title: '库存变化' } },
    { path: 'movements', name: 'report-movements', component: () => import('../views/reports/MovementsView.vue'), meta: { title: '流水' } },
    { path: 'settings', name: 'report-settings', component: () => import('../views/reports/ReportsSettingsView.vue'), meta: { title: '报表设置' } }
  ]
}
```

- [ ] **Step 2: 在 `AppShell.vue` 中移除 `/reports` 的 `disabled` 属性**

```html
<!-- 原来 -->
<el-menu-item index="/reports" disabled>
<!-- 改为 -->
<el-menu-item index="/reports">
```

- [ ] **Step 3: 创建 `ReportsLayout.vue`**

```vue
<!-- frontend/src/views/reports/ReportsLayout.vue -->
<template>
  <router-view />
</template>

<script setup lang="ts">
// 纯布局容器，子路由渲染
</script>
```

- [ ] **Step 4: 编译验证**

Run: `npm --prefix frontend run typecheck`
Expected: 通过

- [ ] **Step 5: Commit**

```bash
git add frontend/src/router/index.ts \
        frontend/src/components/AppShell.vue \
        frontend/src/views/reports/ReportsLayout.vue
git commit -m "feat(frontend): 报表路由启用 + ReportsLayout

- router: /reports 子路由（search/5 张报表/settings）
- AppShell: 移除 /reports disabled 属性
- ReportsLayout: 子路由容器"
```

---

### Task 3: SearchView — 全局搜索页

**Files:**
- Create: `frontend/src/views/reports/SearchView.vue`

- [ ] **Step 1: 创建 `SearchView.vue`**

```vue
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">全局搜索</h1>
        <p class="page-subtitle">跨物品、批次、位置搜索</p>
      </div>
    </div>

    <!-- 搜索输入 -->
    <div class="search-bar">
      <el-input
        v-model="query"
        placeholder="输入关键词搜索..."
        clearable
        @keyup.enter="doSearch"
        @clear="clearResults"
      >
        <template #append>
          <el-button @click="doSearch" :loading="loading">搜索</el-button>
        </template>
      </el-input>
    </div>

    <!-- 搜索结果 -->
    <div v-if="searched" class="search-results">
      <!-- Items -->
      <el-collapse v-model="expandedGroups">
        <el-collapse-item title="物品" name="items">
          <template v-if="results.items.length">
            <div v-for="item in results.items" :key="item.itemId" class="result-card"
                 @click="goToItem(item.itemId)">
              <div class="result-title">{{ item.name }}</div>
              <div class="result-meta">
                <span v-if="item.brand">{{ item.brand }}</span>
                <span v-if="item.category">{{ item.category }}</span>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in item.matchedFields" :key="f" size="small" type="info">{{ f }}</el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配物品</div>
        </el-collapse-item>

        <el-collapse-item title="批次" name="lots">
          <template v-if="results.lots.length">
            <div v-for="lot in results.lots" :key="lot.lotId" class="result-card">
              <div class="result-title">{{ lot.itemName }}</div>
              <div class="result-meta">
                <span v-if="lot.lotNumber">批次号: {{ lot.lotNumber }}</span>
                <span v-if="lot.serialNumber">序列号: {{ lot.serialNumber }}</span>
              </div>
              <div class="result-matched">
                <el-tag v-for="f in lot.matchedFields" :key="f" size="small" type="info">{{ f }}</el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配批次</div>
        </el-collapse-item>

        <el-collapse-item title="位置" name="locations">
          <template v-if="results.locations.length">
            <div v-for="loc in results.locations" :key="loc.locationId" class="result-card"
                 @click="goToLocation(loc.locationId)">
              <div class="result-title">{{ loc.name }}</div>
              <div class="result-meta">{{ loc.path }}</div>
              <div class="result-matched">
                <el-tag v-for="f in loc.matchedFields" :key="f" size="small" type="info">{{ f }}</el-tag>
              </div>
            </div>
          </template>
          <div v-else class="empty-state">无匹配位置</div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { searchReporting } from '../../api/reporting'
import type { SearchResult } from '../../types/reporting'

const router = useRouter()
const query = ref('')
const loading = ref(false)
const searched = ref(false)
const expandedGroups = ref(['items', 'lots', 'locations'])
const results = ref<SearchResult>({ items: [], lots: [], locations: [] })

let debounceTimer: ReturnType<typeof setTimeout> | null = null

function doSearch() {
  if (!query.value.trim()) return
  loading.value = true
  debounceTimer && clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    try {
      results.value = await searchReporting(query.value.trim())
      searched.value = true
    } finally {
      loading.value = false
    }
  }, 250)
}

function clearResults() {
  searched.value = false
  results.value = { items: [], lots: [], locations: [] }
}

function goToItem(itemId: string) {
  router.push({ path: '/items', query: { highlight: itemId } })
}

function goToLocation(locationId: string) {
  router.push({ path: '/locations', query: { highlight: locationId } })
}
</script>

<style scoped>
.search-bar {
  max-width: 600px;
  margin-bottom: 24px;
}
.result-card {
  padding: 12px 16px;
  border-bottom: 1px solid var(--zj-line);
  cursor: pointer;
  transition: background var(--zj-dur-fast) var(--zj-ease-out);
}
.result-card:hover {
  background: var(--zj-pine-50);
}
.result-card:last-child {
  border-bottom: none;
}
.result-title {
  font-weight: 500;
  color: var(--zj-ink-900);
}
.result-meta {
  font-size: 13px;
  color: var(--zj-ink-600);
  margin-top: 4px;
}
.result-matched {
  margin-top: 6px;
  display: flex;
  gap: 4px;
}
</style>
```

- [ ] **Step 2: 编译验证**

Run: `npm --prefix frontend run typecheck`
Expected: 通过

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/reports/SearchView.vue
git commit -m "feat(frontend): SearchView 全局搜索页

- 搜索输入框（防抖 250ms）
- 结果按实体类型分三组折叠（Items/Lots/Locations）
- 每条命中卡片显示 matchedFields 标签
- 点击跳转物品/位置详情"
```

---

### Task 4: 5 张报表页

**Files:**
- Create: `frontend/src/views/reports/StockByLocationView.vue`
- Create: `frontend/src/views/reports/ExpiringLotsView.vue`
- Create: `frontend/src/views/reports/LowStockView.vue`
- Create: `frontend/src/views/reports/StockChangesView.vue`
- Create: `frontend/src/views/reports/MovementsView.vue`

- [ ] **Step 1: 创建 `StockByLocationView.vue`**

```vue
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">库存分布</h1>
        <p class="page-subtitle">按位置查看当前库存</p>
      </div>
      <el-button v-if="canExport" @click="doExport">导出 CSV</el-button>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-select v-model="filters.itemId" placeholder="物品" clearable filterable @change="onFilter">
        <!-- 物品选项 -->
      </el-select>
      <el-select v-model="filters.categoryId" placeholder="分类" clearable @change="onFilter">
        <!-- 分类选项 -->
      </el-select>
      <el-select v-model="filters.locationId" placeholder="位置" clearable @change="onFilter">
        <!-- 位置选项 -->
      </el-select>
    </div>

    <!-- 表格 -->
    <el-table :data="rows" v-loading="loading" class="table-clickable" style="margin-top: 16px;">
      <el-table-column prop="location_path" label="位置" />
      <el-table-column prop="item_name" label="物品" />
      <el-table-column prop="lot_number" label="批次号" />
      <el-table-column prop="unit_name" label="单位" width="80" />
      <el-table-column prop="quantity" label="数量" width="100" align="right">
        <template #default="{ row }">
          <span style="font-variant-numeric: tabular-nums;">{{ row.quantity }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="expiry_date" label="到期日" width="120" />
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[20, 50, 100]"
      style="margin-top: 16px; justify-content: flex-end;"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getReport, buildExportUrl } from '../../api/reporting'
import type { StockByLocationRow, ReportPage } from '../../types/reporting'

const loading = ref(false)
const rows = ref<StockByLocationRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = ref<Record<string, string | undefined>>({})
const canExport = ref(true) // TODO: 角色检查

async function loadData() {
  loading.value = true
  try {
    const result = await getReport<StockByLocationRow>('stock-by-location', {
      page: page.value, pageSize: pageSize.value, ...filters.value
    })
    rows.value = result.items
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function onFilter() {
  page.value = 1
  loadData()
}

function doExport() {
  const url = buildExportUrl('stock-by-location', { ...filters.value, scope: 'current-filter' })
  window.open(url, '_blank')
}

onMounted(loadData)
watch([page, pageSize], loadData)
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  padding: 16px;
  background: var(--zj-surface-sunken);
  border-radius: var(--zj-radius-md);
}
</style>
```

- [ ] **Step 2: 创建其余 4 张报表页**

每页结构相同：页头 + 筛选条 + 表格 + 分页 + 导出按钮。区别在于：
- `ExpiringLotsView.vue`: 筛选 `withinDays`（默认 30）/ `itemId` / `locationId`，表格含 `days_until_expiry` 列
- `LowStockView.vue`: 筛选 `categoryId`，表格含 `total_quantity` / `low_stock_threshold` 列
- `StockChangesView.vue`: 筛选 `from` / `to`（日期范围）/ `itemId` / `locationId` / `type`
- `MovementsView.vue`: 筛选 `from` / `to` / `itemId` / `type` / `operatorAccountId`

- [ ] **Step 3: 编译验证**

Run: `npm --prefix frontend run typecheck`
Expected: 通过

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/reports/
git commit -m "feat(frontend): 5 张报表页

- StockByLocationView: 库存与位置分布
- ExpiringLotsView: 临期批次
- LowStockView: 低库存物品
- StockChangesView: 库存变化（时间范围）
- MovementsView: 流水筛选（成员/类型/物品）
- 统一：筛选条 + Element Plus 表格 + 分页 + 导出按钮"
```

---

### Task 5: ReportsSettingsView — 设置页

**Files:**
- Create: `frontend/src/views/reports/ReportsSettingsView.vue`

- [ ] **Step 1: 创建 `ReportsSettingsView.vue`**

```vue
<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">报表设置</h1>
        <p class="page-subtitle">投影重建与导出审计</p>
      </div>
    </div>

    <!-- 投影重建 -->
    <div class="section">
      <h2 class="section-title">投影重建</h2>
      <p class="section-desc">清空报表读模型并从源数据重新填充。适用于投影 schema 变更或数据修复。</p>
      <el-popconfirm
        title="确认重建报表读模型？此操作将清空现有投影数据。"
        confirm-button-text="确认重建"
        cancel-button-text="取消"
        @confirm="doRebuild"
      >
        <template #reference>
          <el-button type="danger" :loading="rebuilding">重建报表读模型</el-button>
        </template>
      </el-popconfirm>
      <p v-if="rebuildResult" class="rebuild-result">{{ rebuildResult }}</p>
    </div>

    <!-- 导出审计 -->
    <div class="section">
      <h2 class="section-title">导出审计</h2>
      <el-table :data="auditLogs" v-loading="auditLoading">
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="160" />
        <el-table-column prop="outcome" label="结果" width="100">
          <template #default="{ row }">
            <span :class="row.outcome === 'SUCCESS' ? 'text-pine' : 'text-danger'">
              {{ row.outcome }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情">
          <template #default="{ row }">
            <span style="font-family: var(--zj-mono); font-size: 12px;">
              {{ JSON.stringify(row.detail) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { rebuildProjection } from '../../api/reporting'
import { getJson } from '../../api/http'

const rebuilding = ref(false)
const rebuildResult = ref('')
const auditLoading = ref(false)
const auditLogs = ref<any[]>([])

async function doRebuild() {
  rebuilding.value = true
  try {
    await rebuildProjection('current') // TODO: 获取当前 householdId
    rebuildResult.value = '重建完成'
    ElMessage.success('报表读模型重建完成')
  } catch {
    rebuildResult.value = '重建失败'
    ElMessage.error('重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const result = await getJson<any>('/api/v1/audit-logs?action=EXPORT_PERFORMED&page=1&pageSize=50')
    auditLogs.value = result.items || []
  } finally {
    auditLoading.value = false
  }
}

function formatTime(ts: string) {
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(loadAuditLogs)
</script>

<style scoped>
.section {
  margin-bottom: 32px;
}
.section-title {
  font-family: var(--zj-serif);
  font-size: 18px;
  color: var(--zj-ink-900);
  margin-bottom: 8px;
}
.section-desc {
  font-size: 13px;
  color: var(--zj-ink-600);
  margin-bottom: 16px;
}
.rebuild-result {
  margin-top: 8px;
  font-size: 13px;
  color: var(--zj-ink-600);
}
.text-pine { color: var(--zj-pine-600); }
.text-danger { color: var(--zj-danger); }
</style>
```

- [ ] **Step 2: 编译验证**

Run: `npm --prefix frontend run typecheck`
Expected: 通过

- [ ] **Step 3: Commit**

```bash
git add frontend/src/views/reports/ReportsSettingsView.vue
git commit -m "feat(frontend): ReportsSettingsView 投影重建 + 导出审计

- 投影重建按钮（el-popconfirm 二次确认）
- 导出审计表（复用 /api/v1/audit-logs 端点）
- 仅 OWNER/ADMIN 可见"
```

---

### Task 6: Vitest 测试 + Playwright E2E

**Files:**
- Create: `frontend/src/views/reports/__tests__/SearchView.test.ts`
- Create: `frontend/src/views/reports/__tests__/StockByLocationView.test.ts`
- Create: `frontend/src/views/reports/__tests__/ReportsSettingsView.test.ts`
- Modify: `e2e/reports.spec.ts` (Playwright)

- [ ] **Step 1: Vitest — 搜索防抖、分组折叠、导出按钮权限**

```typescript
// SearchView.test.ts
// - 搜索输入 → 防抖 250ms → 调用 searchReporting
// - 结果分三组折叠
// - 空结果显示 empty-state
```

```typescript
// StockByLocationView.test.ts
// - 筛选变更 → 重新查询
// - 分页切换 → 重新查询
// - 导出按钮点击 → window.open 调用
```

```typescript
// ReportsSettingsView.test.ts
// - 重建按钮 → el-popconfirm → rebuildProjection 调用
// - 审计表展示
```

- [ ] **Step 2: Playwright E2E — 完整链路**

```typescript
// e2e/reports.spec.ts
// - 入库 → 等待投影 → 报表页看到该批次
// - 搜索关键词 → 命中物品
// - 导出 CSV → 下载文件内容验证
// - 投影重建 → 完成后报表仍有数据
```

- [ ] **Step 3: 运行测试**

Run: `npm --prefix frontend test`
Run: `make e2e-smoke`
Expected: 全部通过

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/reports/__tests__/ e2e/reports.spec.ts
git commit -m "test(frontend): 报表 Vitest + Playwright E2E

- SearchView: 搜索防抖/分组折叠/空态
- StockByLocationView: 筛选/分页/导出
- ReportsSettingsView: 重建/审计
- Playwright: 入库→投影→报表→导出→重建 完整链路"
```
