<template>
  <el-dialog
    :model-value="modelValue"
    title="报损"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <el-steps :active="currentStep" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="选择批次" />
      <el-step title="报损详情" />
    </el-steps>

    <!-- Step 0: Select lot+location -->
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

    <!-- Step 1: Loss details -->
    <div v-if="currentStep === 1">
      <el-form label-width="80px">
        <el-form-item label="物品">
          <span>{{ selectedPosition?.itemName }}</span>
        </el-form-item>
        <el-form-item label="批次">
          <span>{{ selectedPosition?.lotNumber ?? '-' }}</span>
        </el-form-item>
        <el-form-item label="位置">
          <span>{{ locationNameMap.get(selectedPosition?.locationId ?? '') ?? '-' }}</span>
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
        <el-form-item label="报损原因" required>
          <el-input
            v-model="form.reason"
            placeholder="请输入报损原因"
            @change="resetIdempotencyKey"
          />
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
            确认报损
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchStockPositions, lossStock } from '../../api/inventory'
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
const locationNameMap = ref<Map<string, string>>(new Map())

const selectedPosition = ref<StockPosition | null>(null)

const form = ref({
  quantity: 1,
  reason: '',
  memo: '',
})

const maxQuantity = computed(() => {
  if (!selectedPosition.value) return 0
  return parseFloat(selectedPosition.value.quantity)
})

const canSubmit = computed(() => {
  return (
    form.value.quantity > 0 &&
    form.value.quantity <= maxQuantity.value &&
    form.value.reason.trim().length > 0
  )
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
    await lossStock(
      {
        lotId: selectedPosition.value.lotId,
        locationId: selectedPosition.value.locationId,
        quantity: String(form.value.quantity),
        reason: form.value.reason,
        memo: form.value.memo || null,
      },
      idempotencyKey.value,
    )

    ElMessage.success('报损成功')
    emit('done')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiError) {
      ElMessage.error(err.message)
    } else {
      ElMessage.error(err instanceof Error ? err.message : '报损失败')
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
    quantity: 1,
    reason: '',
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
