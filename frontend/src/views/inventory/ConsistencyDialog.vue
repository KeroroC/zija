<template>
  <el-dialog
    :model-value="modelValue"
    title="一致性检查"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <div v-loading="loading">
      <el-alert
        v-if="total > 0"
        :title="`发现 ${total} 处不一致`"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-alert
        v-if="errorMessage"
        :title="errorMessage"
        type="error"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />
      <el-table v-if="total > 0" :data="rows" size="small" style="width: 100%">
        <el-table-column label="物品/批次" min-width="160">
          <template #default="{ row }">
            {{ lotName(row.lotId) }}
          </template>
        </el-table-column>
        <el-table-column label="位置" min-width="120">
          <template #default="{ row }">
            {{ locationName(row.locationId) }}
          </template>
        </el-table-column>
        <el-table-column label="账面数量" min-width="90" prop="expected" />
        <el-table-column label="实际数量" min-width="90" prop="actual" />
      </el-table>
      <el-result v-else-if="!loading && !errorMessage" icon="success" title="库存数据一致" sub-title="未发现不一致" />
    </div>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchConsistencyReport, fetchLots } from '../../api/inventory'
import { fetchLocationTree } from '../../api/location'
import type { ConsistencyDiscrepancy } from '../../types/inventory'
import type { LocationNode } from '../../types/location'

defineProps<{
  modelValue: boolean
}>()

defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const loading = ref(false)
const rows = ref<ConsistencyDiscrepancy[]>([])
const total = ref(0)
const errorMessage = ref('')
const lotNameMap = ref<Map<string, string>>(new Map())
const locationNameMap = ref<Map<string, string>>(new Map())

function collectLocations(nodes: LocationNode[], map: Map<string, string>) {
  for (const node of nodes) {
    map.set(node.id, node.name)
    if (node.children?.length) collectLocations(node.children, map)
  }
}

async function onOpen() {
  loading.value = true
  rows.value = []
  total.value = 0
  errorMessage.value = ''
  try {
    const [report, lots, tree] = await Promise.all([
      fetchConsistencyReport(),
      fetchLots({ pageSize: 1000 }),
      fetchLocationTree(),
    ])
    const lMap = new Map<string, string>()
    for (const lot of lots.items) {
      lMap.set(lot.lotId, lot.lotNumber ?? lot.itemName)
    }
    lotNameMap.value = lMap
    const locMap = new Map<string, string>()
    collectLocations(tree.roots, locMap)
    locationNameMap.value = locMap
    rows.value = report.discrepancies
    total.value = report.total
  } catch (err) {
    errorMessage.value = err instanceof Error ? err.message : '一致性检查失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

function lotName(lotId: string): string {
  return lotNameMap.value.get(lotId) ?? lotId.substring(0, 8)
}

function locationName(locationId: string): string {
  return locationNameMap.value.get(locationId) ?? locationId.substring(0, 8)
}
</script>
