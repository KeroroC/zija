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
    </div>

    <el-table :data="items" v-loading="loading" @row-click="(row: any) => openDetail(row as CatalogItem)">
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="managementType" label="类型" width="100">
        <template #default="{ row }">
          <el-tag :type="row.managementType === 'CONSUMABLE' ? 'warning' : 'success'" size="small">
            {{ row.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}
          </el-tag>
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
        <h3>{{ selectedItem.name }}</h3>
        <p>类型：{{ selectedItem.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}</p>
        <p>状态：{{ selectedItem.status === 'ACTIVE' ? '活跃' : '归档' }}</p>
        <p v-if="selectedItem.memo">备注：{{ selectedItem.memo }}</p>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchItems as apiFetchItems, archiveItem as apiArchiveItem, restoreItem as apiRestoreItem } from '../api/catalog'
import type { CatalogItem } from '../types/catalog'
import ItemFormDrawer from './ItemFormDrawer.vue'

const items = ref<CatalogItem[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const selectedItem = ref<CatalogItem | null>(null)
const formDrawerVisible = ref(false)
const editingItem = ref<CatalogItem | null>(null)

const filters = reactive({ q: '', managementType: '', status: 'ACTIVE' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

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

onMounted(fetchItems)
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
}
.detail-actions {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}
</style>
