<template>
  <div class="current-stock-tab">
    <!-- Filter bar -->
    <div class="filter-bar">
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
    <el-table :data="rows" v-loading="loading" @row-click="onRowClick" style="width: 100%">
      <el-table-column prop="itemName" label="物品名称" min-width="120" />
      <el-table-column prop="lotNumber" label="批次号" min-width="100">
        <template #default="{ row }">
          {{ row.lotNumber ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="位置" min-width="120">
        <template #default="{ row }">
          {{ locationNameMap.get(row.locationId) ?? row.locationId }}
        </template>
      </el-table-column>
      <el-table-column label="数量" min-width="100">
        <template #default="{ row }">
          {{ row.quantity }} {{ row.unitName }}
        </template>
      </el-table-column>
      <el-table-column label="有效期" min-width="110">
        <template #default="{ row }">
          {{ row.expiryDate ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="160">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
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
    <el-drawer
      v-model="drawerVisible"
      title="近期流水"
      direction="rtl"
      size="500px"
    >
      <template v-if="selectedRow">
        <p class="drawer-info">
          {{ selectedRow.itemName }} · {{ selectedRow.lotNumber ?? '-' }} · {{ locationNameMap.get(selectedRow.locationId) ?? selectedRow.locationId }}
        </p>
        <el-table :data="movements" v-loading="movementsLoading" style="width: 100%">
          <el-table-column label="类型" min-width="80">
            <template #default="{ row }">
              {{ movementTypeLabel(row.type) }}
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="80">
            <template #default="{ row }">
              {{ row.quantity }} {{ row.unitName }}
            </template>
          </el-table-column>
          <el-table-column label="来源/目标" min-width="120">
            <template #default="{ row }">
              {{ row.fromLocationName ?? row.toLocationName ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="140">
            <template #default="{ row }">
              {{ formatTime(row.businessTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="memo" label="备注" min-width="120">
            <template #default="{ row }">
              {{ row.memo ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchStockPositions, fetchMovements } from '../../api/inventory'
import { fetchItems } from '../../api/catalog'
import { fetchLocationTree } from '../../api/location'
import type { StockPosition, Movement, MovementType } from '../../types/inventory'
import type { LocationNode } from '../../types/location'

const loading = ref(false)
const rows = ref<StockPosition[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterItemId = ref('')
const filterLocationId = ref('')

const itemNameMap = ref<Map<string, string>>(new Map())
const locationNameMap = ref<Map<string, string>>(new Map())

// Drawer state
const drawerVisible = ref(false)
const selectedRow = ref<StockPosition | null>(null)
const movements = ref<Movement[]>([])
const movementsLoading = ref(false)

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
    const resp = await fetchStockPositions({
      itemId: filterItemId.value || undefined,
      locationId: filterLocationId.value || undefined,
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

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').replace(/\.\d+Z$/, '')
}

async function onRowClick(row: StockPosition) {
  selectedRow.value = row
  drawerVisible.value = true
  movementsLoading.value = true
  try {
    const resp = await fetchMovements({ lotId: row.lotId, page: 1, pageSize: 20 })
    movements.value = resp.items
  } finally {
    movementsLoading.value = false
  }
}

const MOVEMENT_TYPE_LABELS: Record<MovementType, string> = {
  INBOUND: '入库',
  CONSUME: '领用',
  LOSS: '报损',
  ADJUSTMENT: '调整',
  TRANSFER: '移位',
  REVERSAL: '冲销',
}

function movementTypeLabel(type: MovementType): string {
  return MOVEMENT_TYPE_LABELS[type] ?? type
}

onMounted(async () => {
  await loadNameMaps()
  await loadData()
})
</script>

<style scoped>
.current-stock-tab {
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
.drawer-info {
  margin-bottom: 12px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}
</style>
