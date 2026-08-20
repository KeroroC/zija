<template>
  <el-dialog
    :model-value="modelValue"
    title="领用"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <el-steps :active="currentStep" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="选择物品" />
      <el-step title="选择批次" />
      <el-step title="领用详情" />
    </el-steps>

    <!-- Step 0: Select item -->
    <div v-if="currentStep === 0">
      <el-form label-width="80px" v-loading="formLoading">
        <el-form-item label="物品" required>
          <el-select
            v-model="form.itemId"
            filterable
            placeholder="选择物品"
            style="width: 100%"
            @change="onItemChange"
          >
            <el-option
              v-for="item in activeItems"
              :key="item.id"
              :label="item.name"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <!-- Step 1: Select lot+location -->
    <div v-if="currentStep === 1">
      <div v-loading="positionsLoading">
        <el-table
          :data="sortedPositions"
          style="width: 100%"
          size="small"
          highlight-current-row
          @current-change="onPositionSelect"
        >
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
          <el-table-column label="序列号" min-width="100">
            <template #default="{ row }">
              {{ row.serialNumber ?? '-' }}
            </template>
          </el-table-column>
        </el-table>

        <el-empty
          v-if="!positionsLoading && sortedPositions.length === 0"
          description="该物品暂无库存"
        />
      </div>
    </div>

    <!-- Step 2: Quantity + reason/memo -->
    <div v-if="currentStep === 2">
      <el-form label-width="80px">
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
        <el-form-item label="原因">
          <el-input
            v-model="form.reason"
            placeholder=""
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
            :disabled="!form.itemId"
            @click="loadPositionsAndNext"
          >
            下一步
          </el-button>
          <el-button
            v-if="currentStep === 1"
            type="primary"
            :disabled="!selectedPosition"
            @click="nextStep"
          >
            下一步
          </el-button>
          <el-button
            v-if="currentStep === 2"
            type="primary"
            :loading="submitting"
            :disabled="form.quantity <= 0 || form.quantity > maxQuantity"
            @click="handleSubmit"
          >
            确认领用
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchItems } from '../../api/catalog'
import { fetchStockPositions, consumeStock } from '../../api/inventory'
import { fetchLocationTree } from '../../api/location'
import { ApiError } from '../../api/http'
import { INVENTORY_INSUFFICIENT_STOCK } from '../../types/errorCodes'
import type { CatalogItem } from '../../types/catalog'
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
const formLoading = ref(false)
const positionsLoading = ref(false)
const submitting = ref(false)

const idempotencyKey = ref(crypto.randomUUID())

const activeItems = ref<CatalogItem[]>([])
const stockPositions = ref<StockPosition[]>([])
const locationTreeData = ref<LocationNode[]>([])
const locationNameMap = ref<Map<string, string>>(new Map())

const selectedPosition = ref<StockPosition | null>(null)

const form = ref({
  itemId: '',
  quantity: 1,
  reason: '',
  memo: '',
})

const sortedPositions = computed(() => {
  return [...stockPositions.value].sort((a, b) => {
    // Sort by expiry ASC, null last
    if (!a.expiryDate && !b.expiryDate) return 0
    if (!a.expiryDate) return 1
    if (!b.expiryDate) return -1
    return a.expiryDate.localeCompare(b.expiryDate)
  })
})

const maxQuantity = computed(() => {
  if (!selectedPosition.value) return 0
  return parseFloat(selectedPosition.value.quantity)
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

function onItemChange() {
  selectedPosition.value = null
  form.value.quantity = 1
  form.value.reason = ''
  form.value.memo = ''
  resetIdempotencyKey()
}

async function loadPositionsAndNext() {
  if (!form.value.itemId) return

  positionsLoading.value = true
  try {
    const resp = await fetchStockPositions({
      itemId: form.value.itemId,
      pageSize: 1000,
    })
    stockPositions.value = resp.items.filter(
      (p) => parseFloat(p.quantity) > 0,
    )
    currentStep.value = 1
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载库存失败')
  } finally {
    positionsLoading.value = false
  }
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
  currentStep.value = 2
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
  if (!selectedPosition.value) return

  submitting.value = true
  try {
    await consumeStock(
      {
        lotId: selectedPosition.value.lotId,
        locationId: selectedPosition.value.locationId,
        quantity: String(form.value.quantity),
        reason: form.value.reason || null,
        memo: form.value.memo || null,
      },
      idempotencyKey.value,
    )

    ElMessage.success('领用成功')
    emit('done')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.errorCode === INVENTORY_INSUFFICIENT_STOCK) {
        ElMessage.error('库存不足，请减少领用数量或选择其他批次。')
      } else {
        ElMessage.error(err.message)
      }
    } else {
      ElMessage.error(err instanceof Error ? err.message : '领用失败')
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
    itemId: '',
    quantity: 1,
    reason: '',
    memo: '',
  }
  stockPositions.value = []
}

async function onOpen() {
  resetState()
  formLoading.value = true
  try {
    const [itemsResp, locTree] = await Promise.all([
      fetchItems({ status: 'ACTIVE', pageSize: 1000 }),
      fetchLocationTree(),
    ])
    activeItems.value = itemsResp.items
    locationTreeData.value = locTree.roots
    locationNameMap.value = new Map(flattenLocations(locTree.roots))
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载数据失败')
  } finally {
    formLoading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) onOpen()
  },
  { immediate: true },
)
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
