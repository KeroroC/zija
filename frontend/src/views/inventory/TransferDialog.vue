<template>
  <el-dialog
    :model-value="modelValue"
    title="移位"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <el-steps :active="currentStep" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="选择来源" />
      <el-step title="移位详情" />
    </el-steps>

    <!-- Step 0: Select lot+source location -->
    <div v-if="currentStep === 0">
      <div v-loading="positionsLoading">
        <el-table
          :data="stockPositions"
          style="width: 100%"
          size="small"
          highlight-current-row
          @current-change="onPositionSelect"
        >
          <el-table-column label="物品" min-width="100" prop="itemName" />
          <el-table-column label="批次号" min-width="100">
            <template #default="{ row }">
              {{ row.lotNumber ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="位置" min-width="120">
            <template #default="{ row }">
              {{ locationNameMap.get(row.locationId) ?? row.locationId }}
            </template>
          </el-table-column>
          <el-table-column label="可用数量" min-width="100">
            <template #default="{ row }">
              {{ row.quantity }} {{ row.unitName }}
            </template>
          </el-table-column>
          <el-table-column label="有效期" min-width="110">
            <template #default="{ row }">
              {{ row.expiryDate ?? '-' }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!positionsLoading && stockPositions.length === 0"
          description="暂无库存"
        />
      </div>
    </div>

    <!-- Step 1: Transfer details -->
    <div v-if="currentStep === 1">
      <el-form label-width="80px">
        <el-form-item label="物品">
          <span>{{ selectedPosition?.itemName }}</span>
        </el-form-item>
        <el-form-item label="批次">
          <span>{{ selectedPosition?.lotNumber ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="来源位置">
          <span>{{ sourceLocationName }}</span>
        </el-form-item>
        <el-form-item label="目标位置" required>
          <el-tree-select
            v-model="form.toLocationId"
            :data="locationTreeData"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            placeholder="选择目标位置"
            check-strictly
            style="width: 100%"
            @change="resetIdempotencyKey"
          />
          <el-alert
            v-if="form.toLocationId && form.toLocationId === selectedPosition?.locationId"
            title="目标位置不能与来源位置相同"
            type="error"
            show-icon
            :closable="false"
            style="margin-top: 8px"
          />
        </el-form-item>
        <el-form-item label="数量" required>
          <el-input-number
            v-model="form.quantity"
            :min="0.01"
            :max="maxQuantity"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
            @change="resetIdempotencyKey"
          />
          <span style="margin-left: 8px">{{ selectedPosition?.unitName }}</span>
          <span style="margin-left: 8px; color: var(--el-text-color-secondary)">
            (可用: {{ selectedPosition?.quantity }})
          </span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="form.memo"
            type="textarea"
            :rows="2"
            placeholder=""
            @change="resetIdempotencyKey"
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="close">取消</el-button>
        <div class="footer-right">
          <el-button v-if="currentStep > 0" @click="prevStep">上一步</el-button>
          <el-button
            v-if="currentStep === 0"
            type="primary"
            :disabled="!selectedPosition"
            @click="nextStep"
          >
            下一步
          </el-button>
          <el-button
            v-if="currentStep === 1"
            type="primary"
            :loading="submitting"
            :disabled="!canSubmit"
            @click="handleSubmit"
          >
            确认移位
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchStockPositions, transferStock } from '../../api/inventory'
import { fetchLocationTree } from '../../api/location'
import { ApiError } from '../../api/http'
import type { StockPosition } from '../../types/inventory'
import type { LocationNode } from '../../types/location'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  done: []
}>()

const currentStep = ref(0)
const positionsLoading = ref(false)
const submitting = ref(false)

const idempotencyKey = ref(crypto.randomUUID())

const stockPositions = ref<StockPosition[]>([])
const locationTreeData = ref<LocationNode[]>([])
const locationNameMap = ref<Map<string, string>>(new Map())

const selectedPosition = ref<StockPosition | null>(null)

const form = ref({
  toLocationId: '',
  quantity: 1,
  memo: '',
})

const maxQuantity = computed(() => {
  if (!selectedPosition.value) return 0
  return parseFloat(selectedPosition.value.quantity)
})

const sourceLocationName = computed(() => {
  if (!selectedPosition.value) return ''
  return locationNameMap.value.get(selectedPosition.value.locationId) ?? selectedPosition.value.locationId
})

const canSubmit = computed(() => {
  if (!selectedPosition.value) return false
  if (!form.value.toLocationId) return false
  if (form.value.toLocationId === selectedPosition.value.locationId) return false
  if (form.value.quantity <= 0 || form.value.quantity > maxQuantity.value) return false
  return true
})

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

function resetIdempotencyKey() {
  idempotencyKey.value = crypto.randomUUID()
}

function onPositionSelect(row: StockPosition | null) {
  selectedPosition.value = row
  if (row) {
    form.value.quantity = parseFloat(row.quantity)
    form.value.toLocationId = ''
    resetIdempotencyKey()
  }
}

function nextStep() {
  if (!selectedPosition.value) return
  currentStep.value = 1
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

function close() {
  emit('update:modelValue', false)
}

async function handleSubmit() {
  if (!selectedPosition.value || !canSubmit.value) return

  submitting.value = true
  try {
    await transferStock(
      {
        lotId: selectedPosition.value.lotId,
        fromLocationId: selectedPosition.value.locationId,
        toLocationId: form.value.toLocationId,
        quantity: String(form.value.quantity),
        memo: form.value.memo || null,
      },
      idempotencyKey.value,
    )

    ElMessage.success('移位成功')
    emit('done')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiError) {
      ElMessage.error(err.message)
    } else {
      ElMessage.error(err instanceof Error ? err.message : '移位失败')
    }
  } finally {
    submitting.value = false
  }
}

function resetState() {
  currentStep.value = 0
  selectedPosition.value = null
  idempotencyKey.value = crypto.randomUUID()
  form.value = {
    toLocationId: '',
    quantity: 1,
    memo: '',
  }
  stockPositions.value = []
}

async function onOpen() {
  resetState()
  positionsLoading.value = true
  try {
    const [posResp, locTree] = await Promise.all([
      fetchStockPositions({ pageSize: 1000 }),
      fetchLocationTree(),
    ])
    stockPositions.value = posResp.items.filter(
      (p) => parseFloat(p.quantity) > 0,
    )
    locationTreeData.value = locTree.roots
    locationNameMap.value = new Map(flattenLocations(locTree.roots))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载数据失败')
  } finally {
    positionsLoading.value = false
  }
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.footer-right {
  display: flex;
  gap: 8px;
}
</style>
