<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">库存变化</h1>
        <p class="page-subtitle">按时间范围查看库存变动记录</p>
      </div>
      <el-button v-if="canExport" @click="doExport">导出 CSV</el-button>
    </div>

    <!-- 筛选条 -->
    <div class="filter-bar">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        clearable
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
      <el-select
        v-model="filters.type"
        placeholder="变动类型"
        clearable
        @change="onFilter"
      >
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <!-- 表格 -->
    <el-table :data="rows" v-loading="loading" class="report-table">
      <el-table-column prop="business_time" label="时间" min-width="170">
        <template #default="{ row }">
          <span class="numeric-cell">{{ formatDateTime(row.business_time) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="item_name" label="物品" min-width="160" show-overflow-tooltip />
      <el-table-column prop="type" label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="movementTagType(row.type)" size="small">
            {{ movementTypeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="quantity_delta" label="数量" width="90" align="right">
        <template #default="{ row }">
          <span class="numeric-cell">{{ row.quantity_delta > 0 ? '+' : '' }}{{ row.quantity_delta }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="from_location_path" label="来源" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.from_location_path ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="to_location_path" label="目标" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.to_location_path ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="operator_display_name" label="操作人" width="110" show-overflow-tooltip />
      <el-table-column prop="reason" label="原因" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.reason ?? '-' }}
        </template>
      </el-table-column>
      <template #empty>
        <div class="report-empty">当前条件下暂无库存变动记录</div>
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
import { formatDateTime } from '../../utils/date'
import { movementTypeLabel, movementTagType } from '../../utils/movement'
import type { StockChangeRow } from '../../types/reporting'
import type { LocationNode } from '../../types/location'

const sessionStore = useSessionStore()
const canExport = computed(() => {
  const role = sessionStore.role
  return role === 'OWNER' || role === 'ADMIN'
})

const loading = ref(false)
const rows = ref<StockChangeRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const dateRange = ref<string[] | null>(null)

const filters = ref<Record<string, string | undefined>>({
  itemId: undefined,
  locationId: undefined,
  type: undefined,
})

const itemNameMap = ref<Map<string, string>>(new Map())
const locationNameMap = ref<Map<string, string>>(new Map())

const typeOptions: { value: string; label: string }[] = [
  { value: 'INBOUND', label: '入库' },
  { value: 'CONSUME', label: '领用' },
  { value: 'LOSS', label: '报损' },
  { value: 'ADJUSTMENT', label: '调整' },
  { value: 'TRANSFER', label: '移位' },
  { value: 'REVERSAL', label: '冲正' },
]

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
    const result = await getReport<StockChangeRow>('stock-changes', {
      page: page.value,
      pageSize: pageSize.value,
      from: dateRange.value?.[0] || undefined,
      to: dateRange.value?.[1] || undefined,
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
  const url = buildExportUrl('stock-changes', {
    from: dateRange.value?.[0] || undefined,
    to: dateRange.value?.[1] || undefined,
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
.filter-bar :deep(.el-date-editor) {
  width: 300px;
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
.report-empty {
  padding: 28px 0 32px;
  font-family: var(--zj-serif);
  font-size: 15px;
  color: var(--zj-ink-400);
}
.report-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}
</style>
