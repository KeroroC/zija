<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">物品资料</h2>
        <p class="page-subtitle">维护家庭物品的主数据</p>
      </div>
      <el-button type="primary" @click="openCreate">新建物品</el-button>
    </div>

    <div class="items-filters">
      <el-input v-model="filters.q" placeholder="搜索物品" clearable class="filter-search" @input="debouncedFetch" />
      <el-select v-model="filters.managementType" placeholder="管理类型" clearable @change="fetchItems">
        <el-option label="消耗品" value="CONSUMABLE" />
        <el-option label="耐用品" value="DURABLE" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable @change="fetchItems">
        <el-option label="活跃" value="ACTIVE" />
        <el-option label="归档" value="ARCHIVED" />
      </el-select>
      <el-tree-select
        v-model="filters.categoryId"
        :data="categoryTree"
        :props="({ label: 'name', value: 'id', children: 'children' } as any)"
        placeholder="分类"
        clearable
        check-strictly
        @change="fetchItems"
      />
      <el-select v-model="filters.brandId" placeholder="品牌" clearable @change="fetchItems">
        <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-select v-model="filters.tagId" placeholder="标签" clearable @change="fetchItems">
        <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-select v-model="filters.sort" placeholder="排序" clearable @change="fetchItems">
        <el-option label="名称↑" value="name,asc" />
        <el-option label="名称↓" value="name,desc" />
        <el-option label="创建时间↑" value="createdAt,asc" />
        <el-option label="创建时间↓" value="createdAt,desc" />
        <el-option label="更新时间↑" value="updatedAt,asc" />
        <el-option label="更新时间↓" value="updatedAt,desc" />
      </el-select>
    </div>

    <el-table :data="items" v-loading="loading" class="items-table table-clickable" @row-click="(row: any) => openDetail(row as CatalogItem)">
      <el-table-column label="封面" width="60" class-name="col-cover">
        <template #default="{ row }">
          <img v-if="row.coverUrl" :src="row.coverUrl" class="cover-thumb" alt="封面" />
          <span v-else class="cover-placeholder">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="managementType" label="类型" width="90">
        <template #default="{ row }">
          <span :class="row.managementType === 'CONSUMABLE' ? 'zj-dot zj-dot-warn' : 'zj-dot zj-dot-pine'"></span>{{ row.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}
        </template>
      </el-table-column>
      <el-table-column label="分类" width="110" class-name="col-secondary">
        <template #default="{ row }"><span class="cell-secondary">{{ categoryMap[(row.categoryId as string)] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="品牌" width="90" class-name="col-secondary">
        <template #default="{ row }"><span class="cell-secondary">{{ brandMap[(row.brandId as string)] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="单位" width="70" class-name="col-secondary">
        <template #default="{ row }"><span class="cell-secondary">{{ unitMap[row.unitId] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="标签" width="125" class-name="col-secondary">
        <template #default="{ row }">
          <template v-if="row.tagIds?.length">
            <el-tag v-for="tid in row.tagIds.slice(0, 2)" :key="tid" size="small" effect="plain" class="tag-item">
              {{ tagMap[tid] || tid }}
            </el-tag>
            <el-tag v-if="row.tagIds.length > 2" size="small" type="info" effect="plain">+{{ row.tagIds.length - 2 }}</el-tag>
          </template>
          <span v-else class="cell-secondary">—</span>
        </template>
      </el-table-column>
      <el-table-column label="低库存阈值" width="100" class-name="col-secondary">
        <template #default="{ row }">
          <span class="cell-secondary zj-num">{{ row.managementType === 'CONSUMABLE' && row.lowStockThreshold ? row.lowStockThreshold : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <span :class="row.status === 'ACTIVE' ? 'zj-dot zj-dot-pine' : 'zj-dot zj-dot-off'"></span>{{ row.status === 'ACTIVE' ? '活跃' : '归档' }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="165">
        <template #default="{ row }"><span class="cell-secondary zj-num">{{ formatDate(row.updatedAt) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="70" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click.stop="openEdit(row as CatalogItem)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="table-empty">
          <p class="table-empty-title">还没有物品</p>
          <p class="table-empty-hint">从新建第一件物品开始，让每一件物品都有迹可循。</p>
        </div>
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.pageSize"
      :total="pagination.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="fetchItems"
      @size-change="fetchItems"
    />

    <el-drawer v-model="detailVisible" title="物品详情" size="400px">
      <div v-if="selectedItem">
        <img v-if="selectedItem.coverUrl" :src="selectedItem.coverUrl" class="detail-cover" alt="封面" />
        <h3 class="detail-name">{{ selectedItem.name }}</h3>
        <p class="detail-row">类型：{{ selectedItem.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}</p>
        <p class="detail-row">状态：{{ selectedItem.status === 'ACTIVE' ? '活跃' : '归档' }}</p>
        <p class="detail-row">分类：{{ categoryMap[(selectedItem.categoryId as string)] || '—' }}</p>
        <p class="detail-row">品牌：{{ brandMap[(selectedItem.brandId as string)] || '—' }}</p>
        <p class="detail-row">单位：{{ unitMap[selectedItem.unitId] || '—' }}<span v-if="unitDetailMap[selectedItem.unitId]">（精度：{{ unitDetailMap[selectedItem.unitId] }} 位小数）</span></p>
        <p class="detail-row">标签：<template v-if="selectedItem.tagIds?.length">
          <el-tag v-for="tid in selectedItem.tagIds" :key="tid" size="small" effect="plain" class="tag-item">{{ tagMap[tid] || tid }}</el-tag>
        </template><template v-else>—</template></p>
        <p v-if="selectedItem.expiryReminderMode">临期提醒：{{ selectedItem.expiryReminderMode }}{{ selectedItem.expiryReminderDays?.length ? `（${selectedItem.expiryReminderDays.join(', ')} 天）` : '' }}</p>
        <p v-if="selectedItem.lowStockMode">低库存：{{ selectedItem.lowStockMode }}{{ selectedItem.lowStockThreshold ? `（阈值：${selectedItem.lowStockThreshold}）` : '' }}</p>
        <p v-if="selectedItem.memo">备注：{{ selectedItem.memo }}</p>
        <p>创建时间：{{ formatDate(selectedItem.createdAt) }}</p>
        <el-divider />
        <div class="inventory-summary">
          <p class="detail-row">库存总量：{{ inventoryTotal }}</p>
          <p class="detail-row">批次数：{{ lotCount }}</p>
          <el-button type="primary" size="small" @click="goToInbound">入库</el-button>
        </div>
        <div class="detail-actions">
          <el-button v-if="selectedItem.status === 'ACTIVE'" @click="archiveItem(selectedItem)">归档</el-button>
          <el-button v-if="selectedItem.status === 'ARCHIVED'" @click="restoreItem(selectedItem)">恢复</el-button>
        </div>
      </div>
    </el-drawer>

    <ItemFormDrawer
      v-model="formDrawerVisible"
      :item="editingItem"
      @saved="onFormSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchItems as apiFetchItems,
  archiveItem as apiArchiveItem,
  restoreItem as apiRestoreItem,
  fetchCategories, fetchBrands, fetchUnits, fetchTags
} from '../api/catalog'
import { fetchStockPositions, fetchLots } from '../api/inventory'
import type { CatalogItem, Category, Brand, Unit, Tag } from '../types/catalog'
import ItemFormDrawer from './ItemFormDrawer.vue'

const router = useRouter()

const items = ref<CatalogItem[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const selectedItem = ref<CatalogItem | null>(null)
const formDrawerVisible = ref(false)
const editingItem = ref<CatalogItem | null>(null)

// Inventory summary for detail drawer
const inventoryTotal = ref(0)
const lotCount = ref(0)

// Dictionary data
const categories = ref<Category[]>([])
const brands = ref<Brand[]>([])
const units = ref<Unit[]>([])
const tags = ref<Tag[]>([])

const filters = reactive({ q: '', managementType: '', status: 'ACTIVE', categoryId: '', brandId: '', tagId: '', sort: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

// Lookup maps
const categoryMap = computed(() => {
  const map: Record<string, string> = {}
  function walk(nodes: (Category & { children?: Category[] })[]) {
    for (const n of nodes) {
      map[n.id] = n.name
      if (n.children) walk(n.children)
    }
  }
  walk(categories.value)
  return map
})

const brandMap = computed(() => {
  const map: Record<string, string> = {}
  for (const b of brands.value) map[b.id] = b.name
  return map
})

const unitMap = computed(() => {
  const map: Record<string, string> = {}
  for (const u of units.value) map[u.id] = u.name
  return map
})

const unitDetailMap = computed(() => {
  const map: Record<string, number> = {}
  for (const u of units.value) map[u.id] = u.decimalScale
  return map
})

const tagMap = computed(() => {
  const map: Record<string, string> = {}
  for (const t of tags.value) map[t.id] = t.name
  return map
})

const categoryTree = computed(() => buildCategoryTree(categories.value))

function buildCategoryTree(list: Category[]): Category[] {
  const map = new Map<string, Category & { children: Category[] }>()
  for (const cat of list) {
    map.set(cat.id, { ...cat, children: [] })
  }
  const roots: (Category & { children: Category[] })[] = []
  for (const node of map.values()) {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

let debounceTimer: ReturnType<typeof setTimeout>

function debouncedFetch() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(fetchItems, 300)
}

async function fetchItems() {
  loading.value = true
  try {
    const res = await apiFetchItems({
      q: filters.q || undefined,
      managementType: filters.managementType || undefined,
      status: filters.status || undefined,
      categoryId: filters.categoryId || undefined,
      brandId: filters.brandId || undefined,
      tagId: filters.tagId || undefined,
      sort: filters.sort || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    items.value = res.items
    pagination.total = res.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDictionaries() {
  const [c, b, u, t] = await Promise.all([
    fetchCategories().catch(() => []),
    fetchBrands().catch(() => []),
    fetchUnits().catch(() => []),
    fetchTags().catch(() => []),
  ])
  categories.value = c
  brands.value = b
  units.value = u
  tags.value = t
}

function openCreate() {
  editingItem.value = null
  formDrawerVisible.value = true
}

function openEdit(item: CatalogItem) {
  editingItem.value = item
  formDrawerVisible.value = true
}

function onFormSaved() {
  formDrawerVisible.value = false
  fetchItems()
}

async function openDetail(item: CatalogItem) {
  selectedItem.value = item
  detailVisible.value = true
  inventoryTotal.value = 0
  lotCount.value = 0
  try {
    const [pos, lots] = await Promise.all([
      fetchStockPositions({ itemId: item.id, pageSize: 10000 }),
      fetchLots({ itemId: item.id, pageSize: 1 }),
    ])
    inventoryTotal.value = pos.items.reduce((sum, p) => sum + Number(p.quantity), 0)
    lotCount.value = lots.total
  } catch {
    // silently ignore — inventory module may not be available yet
  }
}

function goToInbound() {
  if (!selectedItem.value) return
  router.push({ name: 'inventory', query: { action: 'inbound', itemId: selectedItem.value.id } })
}

async function archiveItem(item: CatalogItem) {
  await ElMessageBox.confirm('确定归档此物品？', '确认')
  await apiArchiveItem(item.id, item.version)
  ElMessage.success('已归档')
  fetchItems()
}

async function restoreItem(item: CatalogItem) {
  await apiRestoreItem(item.id, item.version)
  ElMessage.success('已恢复')
  fetchItems()
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadDictionaries()
  fetchItems()
})
</script>

<style scoped>
.items-filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 12px;
  flex-wrap: wrap;
  background: var(--zj-surface-sunken);
  border-radius: var(--zj-radius-md);
}
.filter-search {
  width: 200px;
}
.items-filters .el-select {
  width: 130px;
}
.items-filters .el-tree-select {
  width: 160px;
}
.items-table {
  border-radius: var(--zj-radius-md);
  overflow: hidden;
  box-shadow: var(--zj-shadow-sm);
}
.cell-secondary {
  color: var(--zj-ink-600);
}
.table-empty {
  padding: 40px 0 44px;
}
.table-empty-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}
.table-empty-hint {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--zj-ink-400);
}
.detail-name {
  margin: 0 0 12px;
  font-family: var(--zj-serif);
  font-size: 20px;
  font-weight: 600;
}
.detail-row {
  margin: 0 0 10px;
  font-size: 14px;
  line-height: 1.7;
}
.inventory-summary {
  margin: 12px 0;
}
.detail-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
.cover-thumb {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: var(--zj-radius-sm);
}
.cover-placeholder {
  color: var(--zj-ink-300);
}
.detail-cover {
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  border-radius: var(--zj-radius-md);
  margin-bottom: 12px;
}
.tag-item {
  margin-right: 4px;
  margin-bottom: 2px;
}

@media (max-width: 1024px) {
  :deep(.col-cover),
  :deep(.col-secondary) {
    display: none;
  }
}
</style>
