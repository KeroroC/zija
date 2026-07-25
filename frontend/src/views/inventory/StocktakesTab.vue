<template>
  <div class="stocktakes-tab">
    <!-- Action bar -->
    <div class="action-bar">
      <el-select
        v-model="filterStatus"
        placeholder="状态筛选"
        clearable
        style="width: 140px"
        @change="onFilterChange"
      >
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
      </el-select>
      <el-button type="primary" @click="openCreate">发起盘点</el-button>
    </div>

    <!-- Data table -->
    <el-table
      :data="rows"
      v-loading="loading"
      @row-click="onRowClick"
      style="width: 100%"
    >
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="160">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="完成时间" min-width="160">
        <template #default="{ row }">
          {{ row.completedAt ? formatTime(row.completedAt) : '-' }}
        </template>
      </el-table-column>
      <el-table-column label="创建人" min-width="100">
        <template #default="{ row }">
          {{ operatorNameMap.get(row.createdBy) ?? row.createdBy ?? '-' }}
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="loadData"
        @size-change="onPageSizeChange"
      />
    </div>

    <!-- Stocktake dialog -->
    <StocktakeDialog
      v-model="dialogVisible"
      :stocktake-id="selectedStocktakeId"
      :start-step="dialogStartStep"
      @saved="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchStocktakes } from '../../api/inventory'
import { memberApi } from '../../api/member'
import type { StocktakeSummary, StocktakeStatus } from '../../types/inventory'
import StocktakeDialog from './StocktakeDialog.vue'

const loading = ref(false)
const rows = ref<StocktakeSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterStatus = ref('')

const operatorNameMap = ref<Map<string, string>>(new Map())

// Dialog state
const dialogVisible = ref(false)
const selectedStocktakeId = ref<string | null>(null)
const dialogStartStep = ref(0)

const STATUS_LABELS: Record<StocktakeStatus, string> = {
  DRAFT: '草稿',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

const STATUS_TAG_MAP: Record<StocktakeStatus, 'info' | 'success' | 'danger'> = {
  DRAFT: 'info',
  COMPLETED: 'success',
  CANCELLED: 'danger',
}

function statusLabel(status: StocktakeStatus): string {
  return STATUS_LABELS[status] ?? status
}

function statusTagType(status: StocktakeStatus): 'info' | 'success' | 'danger' {
  return STATUS_TAG_MAP[status] ?? 'info'
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').replace(/\.\d+Z$/, '')
}

async function loadOperatorMap() {
  try {
    const members = await memberApi.list()
    const map = new Map<string, string>()
    for (const m of members) {
      map.set(m.accountId, m.displayName)
    }
    operatorNameMap.value = map
  } catch {
    // Ignore - operator names are optional
  }
}

async function loadData() {
  loading.value = true
  try {
    const resp = await fetchStocktakes({
      status: filterStatus.value || undefined,
      page: currentPage.value,
      pageSize: pageSize.value,
    })
    rows.value = resp.items
    total.value = resp.total
  } finally {
    loading.value = false
  }
}

function onFilterChange() {
  currentPage.value = 1
  loadData()
}

function onPageSizeChange() {
  currentPage.value = 1
  loadData()
}

function openCreate() {
  selectedStocktakeId.value = null
  dialogStartStep.value = 0
  dialogVisible.value = true
}

function onRowClick(row: StocktakeSummary) {
  if (row.status === 'DRAFT') {
    selectedStocktakeId.value = row.id
    dialogStartStep.value = 1
    dialogVisible.value = true
  }
}

defineExpose({ loadData })

onMounted(async () => {
  await loadOperatorMap()
  await loadData()
})
</script>

<style scoped>
.stocktakes-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.action-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}
</style>
