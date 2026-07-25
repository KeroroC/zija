<template>
  <div class="movements-tab">
    <!-- Filter bar -->
    <div class="filter-bar">
      <el-select
        v-model="filterType"
        placeholder="流水类型"
        clearable
        style="width: 140px"
        @change="onFilterChange"
      >
        <el-option
          v-for="opt in typeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-date-picker
        v-model="filterDateRange"
        type="daterange"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        value-format="YYYY-MM-DD"
        clearable
        style="width: 280px"
        @change="onFilterChange"
      />
      <el-select
        v-model="filterItemId"
        placeholder="筛选物品"
        clearable
        filterable
        style="width: 200px"
        @change="onFilterChange"
      >
        <el-option
          v-for="[id, name] in itemNameMap"
          :key="id"
          :label="name"
          :value="id"
        />
      </el-select>
      <el-select
        v-model="filterLocationId"
        placeholder="筛选位置"
        clearable
        filterable
        style="width: 200px"
        @change="onFilterChange"
      >
        <el-option
          v-for="[id, name] in locationNameMap"
          :key="id"
          :label="name"
          :value="id"
        />
      </el-select>
    </div>

    <!-- Data table -->
    <el-table
      :data="rows"
      v-loading="loading"
      @row-click="onRowClick"
      style="width: 100%"
    >
      <el-table-column label="时间" min-width="160">
        <template #default="{ row }">
          {{ formatTime(row.businessTime) }}
        </template>
      </el-table-column>
      <el-table-column label="类型" min-width="80">
        <template #default="{ row }">
          <el-tag :type="tagType(row.type)" size="small">
            {{ typeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="物品" min-width="120">
        <template #default="{ row }">
          {{ itemNameMap.get(row.itemId) ?? row.itemName ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="数量" min-width="100">
        <template #default="{ row }">
          {{ row.quantity }} {{ itemUnitNameMap.get(row.itemId) ?? row.unitName ?? '' }}
        </template>
      </el-table-column>
      <el-table-column label="来源 → 目标" min-width="160">
        <template #default="{ row }">
          {{ resolveFromTo(row as Movement) }}
        </template>
      </el-table-column>
      <el-table-column label="原因" min-width="120">
        <template #default="{ row }">
          {{ row.reason ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="操作人" min-width="100">
        <template #default="{ row }">
          {{ resolveOperator(row as Movement) }}
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

    <!-- Movement detail drawer -->
    <MovementDetailDrawer
      v-model="drawerVisible"
      :movement="selectedMovement"
      :item-name-map="itemNameMap"
      :location-name-map="locationNameMap"
      :item-unit-name-map="itemUnitNameMap"
      :operator-name-map="operatorNameMap"
      @reversed="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchMovements } from '../../api/inventory'
import { fetchItems, fetchUnits } from '../../api/catalog'
import { fetchLocationTree } from '../../api/location'
import { memberApi } from '../../api/member'
import type { Movement, MovementType } from '../../types/inventory'
import type { LocationNode } from '../../types/location'
import MovementDetailDrawer from './MovementDetailDrawer.vue'

const loading = ref(false)
const rows = ref<Movement[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

// Filters
const filterType = ref('')
const filterDateRange = ref<string[] | null>(null)
const filterItemId = ref('')
const filterLocationId = ref('')

// Name maps
const itemNameMap = ref<Map<string, string>>(new Map())
const locationNameMap = ref<Map<string, string>>(new Map())
const itemUnitNameMap = ref<Map<string, string>>(new Map())
const operatorNameMap = ref<Map<string, string>>(new Map())

// Drawer state
const drawerVisible = ref(false)
const selectedMovement = ref<Movement | null>(null)

const typeOptions: { value: string; label: string }[] = [
  { value: 'INBOUND', label: '入库' },
  { value: 'CONSUME', label: '领用' },
  { value: 'LOSS', label: '报损' },
  { value: 'ADJUSTMENT', label: '调整' },
  { value: 'TRANSFER', label: '移位' },
  { value: 'REVERSAL', label: '冲销' },
]

const TYPE_LABELS: Record<MovementType, string> = {
  INBOUND: '入库',
  CONSUME: '领用',
  LOSS: '报损',
  ADJUSTMENT: '调整',
  TRANSFER: '移位',
  REVERSAL: '冲销',
}

const TYPE_TAG_MAP: Record<MovementType, 'success' | 'warning' | 'danger' | 'info' | 'primary'> = {
  INBOUND: 'success',
  CONSUME: 'primary',
  LOSS: 'danger',
  ADJUSTMENT: 'warning',
  TRANSFER: 'info',
  REVERSAL: 'warning',
}

function typeLabel(type: MovementType): string {
  return TYPE_LABELS[type] ?? type
}

function tagType(type: MovementType): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  return TYPE_TAG_MAP[type] ?? 'info'
}

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').replace(/\.\d+Z$/, '')
}

function resolveFromTo(row: Movement): string {
  const from = row.fromLocationId
    ? (locationNameMap.value.get(row.fromLocationId) ?? row.fromLocationName ?? '-')
    : null
  const to = row.toLocationId
    ? (locationNameMap.value.get(row.toLocationId) ?? row.toLocationName ?? '-')
    : null
  if (from && to) return `${from} → ${to}`
  if (from) return from
  if (to) return to
  return '-'
}

function resolveOperator(row: Movement): string {
  // The API returns operatorAccountId; try operatorNameMap first, then operatorUsername
  const accountId = (row as unknown as Record<string, unknown>)['operatorAccountId'] as string | undefined
  if (accountId && operatorNameMap.value.has(accountId)) {
    return operatorNameMap.value.get(accountId)!
  }
  return row.operatorUsername ?? accountId ?? '-'
}

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
  const [itemsResp, locTree, units, members] = await Promise.all([
    fetchItems({ pageSize: 1000 }),
    fetchLocationTree(),
    fetchUnits(),
    memberApi.list(),
  ])

  const iMap = new Map<string, string>()
  const uMap = new Map<string, string>()
  const unitMap = new Map<string, string>()
  for (const item of itemsResp.items) {
    iMap.set(item.id, item.name)
    uMap.set(item.id, item.unitId)
  }
  for (const unit of units) {
    unitMap.set(unit.id, unit.name)
  }
  // Build combined itemId → unitName map
  const combinedMap = new Map<string, string>()
  for (const [itemId, unitId] of uMap) {
    const unitName = unitMap.get(unitId)
    if (unitName) combinedMap.set(itemId, unitName)
  }

  itemNameMap.value = iMap
  itemUnitNameMap.value = combinedMap
  locationNameMap.value = new Map(flattenLocations(locTree.roots))

  const oMap = new Map<string, string>()
  for (const member of members) {
    oMap.set(member.accountId, member.displayName)
  }
  operatorNameMap.value = oMap
}

async function loadData() {
  loading.value = true
  try {
    const resp = await fetchMovements({
      type: filterType.value || undefined,
      itemId: filterItemId.value || undefined,
      locationId: filterLocationId.value || undefined,
      from: filterDateRange.value?.[0] || undefined,
      to: filterDateRange.value?.[1] || undefined,
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

function onRowClick(row: unknown) {
  selectedMovement.value = row as Movement
  drawerVisible.value = true
}

defineExpose({ loadData })

onMounted(async () => {
  await loadNameMaps()
  await loadData()
})
</script>

<style scoped>
.movements-tab {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.filter-bar {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 8px;
}
</style>
