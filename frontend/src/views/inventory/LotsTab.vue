<template>
  <div class="lots-tab">
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
    </div>

    <!-- Data table -->
    <el-table
      :data="rows"
      v-loading="loading"
      @row-click="onRowClick"
      style="width: 100%"
    >
      <el-table-column prop="itemName" label="物品名称" min-width="120" />
      <el-table-column prop="lotNumber" label="批次号" min-width="100">
        <template #default="{ row }">
          {{ row.lotNumber ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="serialNumber" label="序列号" min-width="100">
        <template #default="{ row }">
          {{ row.serialNumber ?? '-' }}
        </template>
      </el-table-column>
      <el-table-column label="总数量" min-width="100">
        <template #default="{ row }">
          {{ row.totalQuantity }} {{ row.unitName }}
        </template>
      </el-table-column>
      <el-table-column label="有效期" min-width="110">
        <template #default="{ row }">
          {{ row.expiryDate ?? '-' }}
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

    <!-- Lot detail drawer -->
    <LotDetailDrawer
      v-model="drawerVisible"
      :lot-id="selectedLotId"
      @updated="loadData"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchLots } from '../../api/inventory'
import { fetchItems } from '../../api/catalog'
import type { LotSummary } from '../../types/inventory'
import LotDetailDrawer from './LotDetailDrawer.vue'

const loading = ref(false)
const rows = ref<LotSummary[]>([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const filterItemId = ref('')

const itemNameMap = ref<Map<string, string>>(new Map())

// Drawer state
const drawerVisible = ref(false)
const selectedLotId = ref<string | null>(null)

async function loadNameMap() {
  const resp = await fetchItems({ pageSize: 1000 })
  const map = new Map<string, string>()
  for (const item of resp.items) {
    map.set(item.id, item.name)
  }
  itemNameMap.value = map
}

async function loadData() {
  loading.value = true
  try {
    const resp = await fetchLots({
      itemId: filterItemId.value || undefined,
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

function onRowClick(row: LotSummary) {
  selectedLotId.value = row.lotId
  drawerVisible.value = true
}

onMounted(async () => {
  await loadNameMap()
  await loadData()
})
</script>

<style scoped>
.lots-tab {
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
