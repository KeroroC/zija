<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">低库存</h1>
        <p class="page-subtitle">库存低于阈值的物品</p>
      </div>
      <el-button v-if="canExport" @click="doExport">导出 CSV</el-button>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-select
        v-model="filters.categoryId"
        placeholder="分类"
        clearable
        @change="onFilter"
      >
        <el-option
          v-for="cat in categories"
          :key="cat.id"
          :label="cat.name"
          :value="cat.id"
        />
      </el-select>
    </div>

    <!-- 表格 -->
    <el-table :data="rows" v-loading="loading" class="report-table" style="margin-top: 16px;">
      <el-table-column prop="item_name" label="物品" min-width="160" />
      <el-table-column prop="total_quantity" label="当前库存" width="120" align="right">
        <template #default="{ row }">
          <span :class="{ 'stock-low': row.total_quantity <= row.low_stock_threshold }">
            {{ row.total_quantity }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="low_stock_threshold" label="阈值" width="120" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ row.low_stock_threshold }}</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[20, 50, 100]"
      class="report-pagination"
      @current-change="loadData"
      @size-change="onPageSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { getReport, buildExportUrl } from '../../api/reporting'
import { fetchCategories } from '../../api/catalog'
import { useSessionStore } from '../../stores/session'
import type { LowStockRow } from '../../types/reporting'
import type { Category } from '../../types/catalog'

const sessionStore = useSessionStore()
const canExport = computed(() => {
  const role = sessionStore.role
  return role === 'OWNER' || role === 'ADMIN'
})

const loading = ref(false)
const rows = ref<LowStockRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = ref<Record<string, string | undefined>>({
  categoryId: undefined,
})

const categories = ref<Category[]>([])

async function loadNameMaps() {
  const cats = await fetchCategories()
  categories.value = cats
}

async function loadData() {
  loading.value = true
  try {
    const result = await getReport<LowStockRow>('low-stock', {
      page: page.value,
      pageSize: pageSize.value,
      ...filters.value,
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

function onPageSizeChange() {
  page.value = 1
  loadData()
}

function doExport() {
  const url = buildExportUrl('low-stock', {
    ...filters.value,
    scope: 'current-filter',
  })
  window.open(url, '_blank')
}

onMounted(async () => {
  await loadNameMaps()
  await loadData()
})
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
.report-table {
  margin-top: 16px;
}
.numeric-cell {
  font-variant-numeric: tabular-nums;
}
.stock-low {
  color: var(--zj-danger);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.report-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
