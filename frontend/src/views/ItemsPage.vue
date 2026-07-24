<template>
  <div class="items-page">
    <div class="items-header">
      <h2>物品资料</h2>
      <el-button type="primary" @click="openCreate">新建物品</el-button>
    </div>

    <div class="items-filters">
      <el-input v-model="filters.q" placeholder="搜索物品" clearable @input="debouncedFetch" />
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
        :props="{ label: 'name', value: 'id', children: 'children' }"
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

    <el-table :data="items" v-loading="loading" @row-click="(row: any) => openDetail(row as CatalogItem)">
      <el-table-column label="封面" width="60" class-name="col-cover">
        <template #default="{ row }">
          <img v-if="row.coverUrl" :src="row.coverUrl" class="cover-thumb" alt="封面" />
          <span v-else class="cover-placeholder">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="managementType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.managementType === 'CONSUMABLE' ? 'warning' : 'success'" size="small">
            {{ row.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分类" width="120" class-name="col-secondary">
        <template #default="{ row }">{{ categoryMap[row.categoryId] || '—' }}</template>
      </el-table-column>
      <el-table-column label="品牌" width="100" class-name="col-secondary">
        <template #default="{ row }">{{ brandMap[row.brandId] || '—' }}</template>
      </el-table-column>
      <el-table-column label="单位" width="80" class-name="col-secondary">
        <template #default="{ row }">{{ unitMap[row.unitId] || '—' }}</template>
      </el-table-column>
      <el-table-column label="标签" width="150" class-name="col-secondary">
        <template #default="{ row }">
          <template v-if="row.tagIds?.length">
            <el-tag v-for="tid in row.tagIds.slice(0, 2)" :key="tid" size="small" class="tag-item">
              {{ tagMap[tid] || tid }}
            </el-tag>
            <el-tag v-if="row.tagIds.length > 2" size="small" type="info">+{{ row.tagIds.length - 2 }}</el-tag>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column label="低库存阈值" width="110" class-name="col-secondary">
        <template #default="{ row }">
          {{ row.managementType === 'CONSUMABLE' && row.lowStockThreshold ? row.lowStockThreshold : '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status === 'ACTIVE' ? '活跃' : '归档' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="180">
        <template #default="{ row }">{{ formatDate(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click.stop="openEdit(row as CatalogItem)">编辑</el-button>
        </template>
      </el-table-column>
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
        <h3>{{ selectedItem.name }}</h3>
        <p>类型：{{ selectedItem.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}</p>
        <p>状态：{{ selectedItem.status === 'ACTIVE' ? '活跃' : '归档' }}</p>
        <p>分类：{{ categoryMap[selectedItem.categoryId] || '—' }}</p>
        <p>品牌：{{ brandMap[selectedItem.brandId] || '—' }}</p>
        <p>单位：{{ unitMap[selectedItem.unitId] || '—' }}<span v-if="unitDetailMap[selectedItem.unitId]">（精度：{{ unitDetailMap[selectedItem.unitId] }} 位小数）</span></p>
        <p>标签：<template v-if="selectedItem.tagIds?.length">
          <el-tag v-for="tid in selectedItem.tagIds" :key="tid" size="small" class="tag-item">{{ tagMap[tid] || tid }}</el-tag>
        </template><template v-else>—</template></p>
        <p v-if="selectedItem.expiryReminderMode">临期提醒：{{ selectedItem.expiryReminderMode }}{{ selectedItem.expiryReminderDays?.length ? `（${selectedItem.expiryReminderDays.join(', ')} 天）` : '' }}</p>
        <p v-if="selectedItem.lowStockMode">低库存：{{ selectedItem.lowStockMode }}{{ selectedItem.lowStockThreshold ? `（阈值：${selectedItem.lowStockThreshold}）` : '' }}</p>
        <p v-if="selectedItem.memo">备注：{{ selectedItem.memo }}</p>
        <p>创建时间：{{ formatDate(selectedItem.createdAt) }}</p>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchItems as apiFetchItems,
  archiveItem as apiArchiveItem,
  restoreItem as apiRestoreItem,
  fetchCategories, fetchBrands, fetchUnits, fetchTags
} from '../api/catalog'
import type { CatalogItem, Category, Brand, Unit, Tag } from '../types/catalog'
import ItemFormDrawer from './ItemFormDrawer.vue'

const items = ref<CatalogItem[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const selectedItem = ref<CatalogItem | null>(null)
const formDrawerVisible = ref(false)
const editingItem = ref<CatalogItem | null>(null)

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
  function walk(nodes: Category[]) {
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
    ElMessage.error(e.title || '加载失败')
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

function openDetail(item: CatalogItem) {
  selectedItem.value = item
  detailVisible.value = true
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
  return new Date(iso).toLocaleString('zh-CN')
}

onMounted(() => {
  loadDictionaries()
  fetchItems()
})
</script>

<style scoped>
.items-page {
  padding: 20px;
}
.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.items-filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
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
  border-radius: 4px;
}
.cover-placeholder {
  color: #c0c4cc;
}
.detail-cover {
  width: 100%;
  max-height: 200px;
  object-fit: contain;
  border-radius: 8px;
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
