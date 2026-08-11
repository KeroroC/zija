<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">临期批次</h1>
        <p class="page-subtitle">即将过期的批次清单</p>
      </div>
      <el-button v-if="canExport" @click="doExport">导出 CSV</el-button>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-input-number
        v-model="withinDays"
        :min="1"
        :max="365"
        placeholder="天数"
        @change="onFilter"
      />
      <el-select
        v-model="filters.itemId"
        placeholder="物品"
        clearable
        filterable
        @change="onFilter"
      >
        <el-option
          v-for="[id, name] in itemNameMap"
          :key="id"
          :label="name"
          :value="id"
        />
      </el-select>
      <el-select
        v-model="filters.locationId"
        placeholder="位置"
        clearable
        filterable
        @change="onFilter"
      >
        <el-option
          v-for="[id, name] in locationNameMap"
          :key="id"
          :label="name"
          :value="id"
        />
      </el-select>
    </div>

    <!-- 表格 -->
    <el-table :data="rows" v-loading="loading" class="report-table">
      <el-table-column prop="item_name" label="物品" min-width="160" show-overflow-tooltip />
      <el-table-column prop="lot_number" label="批次号" min-width="150" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.lot_number ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="location_path" label="位置" min-width="200" show-overflow-tooltip />
      <el-table-column prop="quantity" label="数量" width="90" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ row.quantity }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="expiry_date" label="到期日" min-width="130">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatDateOnly(row.expiry_date) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="days_until_expiry" label="剩余天数" width="90" align="right">
        <template #default="{ row }">
          <span :class="daysClass(row.days_until_expiry)">
            {{ row.days_until_expiry }}
          </span>
        </template>
      </el-table-column>
      <template #empty>
        <div class="report-empty">所选范围内暂无临期批次</div>
      </template>
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
import { fetchItems } from '../../api/catalog'
import { fetchLocationTree } from '../../api/location'
import { useSessionStore } from '../../stores/session'
import { formatDateOnly } from '../../utils/date'
import type { ExpiringLotRow } from '../../types/reporting'
import type { LocationNode } from '../../types/location'

const sessionStore = useSessionStore()
const canExport = computed(() => {
  const role = sessionStore.role
  return role === 'OWNER' || role === 'ADMIN'
})

const loading = ref(false)
const rows = ref<ExpiringLotRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const withinDays = ref(30)

const filters = ref<Record<string, string | undefined>>({
  itemId: undefined,
  locationId: undefined,
})

const itemNameMap = ref<Map<string, string>>(new Map())
const locationNameMap = ref<Map<string, string>>(new Map())

function flattenLocations(nodes: LocationNode[]): [string, string][] {
  const result: [string, string][] = []
  for (const node of nodes) {
    result.push([node.id, node.name])
    if (node.children?.length) {
      result.push(...flattenLocations(node.children))
    }
  }
  return result
}

async function loadNameMaps() {
  const [itemsResp, locTree] = await Promise.all([
    fetchItems({ pageSize: 1000 }),
    fetchLocationTree(),
  ])
  const iMap = new Map<string, string>()
  for (const item of itemsResp.items) {
    iMap.set(item.id, item.name)
  }
  itemNameMap.value = iMap
  locationNameMap.value = new Map(flattenLocations(locTree.roots))
}

async function loadData() {
  loading.value = true
  try {
    const result = await getReport<ExpiringLotRow>('expiring-lots', {
      page: page.value,
      pageSize: pageSize.value,
      withinDays: withinDays.value,
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

function daysClass(days: number): string {
  if (days <= 7) return 'days-urgent'
  if (days <= 14) return 'days-warning'
  return 'days-ok'
}

function doExport() {
  const url = buildExportUrl('expiring-lots', {
    withinDays: String(withinDays.value),
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
.filter-bar :deep(.el-input-number) {
  width: 140px;
}
.filter-bar :deep(.el-select) {
  width: 200px;
}
.report-table {
  margin-top: 16px;
}
.numeric-cell {
  font-variant-numeric: tabular-nums;
}
.report-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
.days-urgent {
  color: var(--zj-danger);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.days-warning {
  color: var(--zj-warning);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.days-ok {
  color: var(--zj-ink-600);
  font-variant-numeric: tabular-nums;
}
.report-empty {
  padding: 28px 0 32px;
  font-family: var(--zj-serif);
  font-size: 15px;
  color: var(--zj-ink-400);
}
</style>
