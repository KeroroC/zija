<template>
  <el-dialog
    :model-value="modelValue"
    title="盘点"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <!-- Steps -->
    <el-steps :active="currentStep" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="选择位置" />
      <el-step title="录入数据" />
      <el-step title="确认盘点" />
    </el-steps>

    <!-- Step 0: Select location -->
    <div v-if="currentStep === 0">
      <el-form label-width="80px">
        <el-form-item label="盘点位置">
          <el-tree-select
            v-model="selectedLocationId"
            :data="locationTreeData"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            placeholder="选择盘点位置"
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
    </div>

    <!-- Step 1: Edit items -->
    <div v-if="currentStep === 1">
      <div v-loading="detailLoading">
        <el-table :data="editItems" style="width: 100%" size="small">
          <el-table-column label="批次号" min-width="100">
            <template #default="{ row }">
              {{ lotNameMap.get(row.lotId) ?? row.lotId }}
            </template>
          </el-table-column>
          <el-table-column label="账面数量" min-width="80">
            <template #default="{ row }">
              {{ row.bookQuantity }}
            </template>
          </el-table-column>
          <el-table-column label="实际数量" min-width="120">
            <template #default="{ row }">
              <el-input-number
                v-model="row.actualQuantity"
                :min="0"
                :precision="2"
                size="small"
                controls-position="right"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="差异原因" min-width="160">
            <template #default="{ row }">
              <el-input
                v-model="row.reason"
                placeholder="差异必填"
                size="small"
                :disabled="row.actualQuantity === row.bookQuantity"
              />
            </template>
          </el-table-column>
        </el-table>

        <!-- Backfill section -->
        <div class="backfill-section">
          <el-button type="primary" link size="small" @click="showBackfill = !showBackfill">
            {{ showBackfill ? '收起补录' : '补录零库存批次' }}
          </el-button>
          <div v-if="showBackfill" class="backfill-form">
            <el-form :inline="true" size="small">
              <el-form-item label="批次">
                <el-select
                  v-model="backfillLotId"
                  filterable
                  placeholder="选择批次"
                  style="width: 200px"
                >
                  <el-option
                    v-for="lot in availableBackfillLots"
                    :key="lot.lotId"
                    :label="`${lot.itemName} - ${lot.lotNumber ?? lot.lotId}`"
                    :value="lot.lotId"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="实际数量">
                <el-input-number
                  v-model="backfillQuantity"
                  :min="0"
                  :precision="2"
                  size="small"
                  controls-position="right"
                  style="width: 120px"
                />
              </el-form-item>
              <el-form-item label="原因">
                <el-input v-model="backfillReason" placeholder="差异原因" size="small" style="width: 160px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="addBackfillItem">添加</el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </div>
    </div>

    <!-- Step 2: Confirm preview -->
    <div v-if="currentStep === 2">
      <div v-loading="confirmLoading">
        <el-alert
          v-if="isStale"
          title="盘点数据已过期"
          description="库存位数据已发生变化，请刷新盘点快照后重新确认。"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
        />
        <el-table :data="previewItems" style="width: 100%" size="small">
          <el-table-column label="批次" min-width="100">
            <template #default="{ row }">
              {{ lotNameMap.get(row.lotId) ?? row.lotId }}
            </template>
          </el-table-column>
          <el-table-column label="账面" min-width="60" prop="bookQuantity" />
          <el-table-column label="实际" min-width="60" prop="actualQuantity" />
          <el-table-column label="差异" min-width="80">
            <template #default="{ row }">
              {{ computeDiff(row) }}
            </template>
          </el-table-column>
          <el-table-column label="原因" min-width="120">
            <template #default="{ row }">
              {{ row.reason ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Footer -->
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel" :loading="cancelLoading">取消盘点</el-button>
        <div class="footer-right">
          <el-button v-if="currentStep > 0" @click="prevStep">上一步</el-button>
          <el-button
            v-if="currentStep === 0"
            type="primary"
            :disabled="!selectedLocationId"
            :loading="createLoading"
            @click="handleCreate"
          >
            创建盘点
          </el-button>
          <el-button
            v-if="currentStep === 1"
            type="primary"
            :loading="saveLoading"
            @click="handleSaveDraft"
          >
            保存
          </el-button>
          <el-button
            v-if="currentStep === 2 && isStale"
            type="warning"
            :loading="refreshLoading"
            @click="handleRefresh"
          >
            刷新快照
          </el-button>
          <el-button
            v-if="currentStep === 2 && !isStale"
            type="primary"
            :loading="confirmLoading"
            @click="handleConfirm"
          >
            确认盘点
          </el-button>
          <el-button
            v-if="currentStep === 2"
            @click="currentStep = 1"
          >
            返回编辑
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createStocktake,
  fetchStocktake,
  updateStocktakeDraft,
  refreshStocktakeDraft,
  confirmStocktake,
  cancelStocktake,
  fetchLots,
} from '../../api/inventory'
import { fetchLocationTree } from '../../api/location'
import { ApiError } from '../../api/http'
import type {
  StocktakeDetail,
  StocktakeItem,
  LotSummary,
} from '../../types/inventory'
import type { LocationNode, LocationTree } from '../../types/location'

const props = defineProps<{
  modelValue: boolean
  stocktakeId: string | null
  startStep: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: []
}>()

const currentStep = ref(0)
const selectedLocationId = ref('')
const locationTreeData = ref<LocationNode[]>([])
const detailLoading = ref(false)
const createLoading = ref(false)
const saveLoading = ref(false)
const confirmLoading = ref(false)
const refreshLoading = ref(false)
const cancelLoading = ref(false)

const stocktakeDetail = ref<StocktakeDetail | null>(null)
const editItems = ref<(Omit<StocktakeItem, 'actualQuantity'> & { actualQuantity: number })[]>([])
const lotNameMap = ref<Map<string, string>>(new Map())

// Backfill state
const showBackfill = ref(false)
const backfillLotId = ref('')
const backfillQuantity = ref(0)
const backfillReason = ref('')
const availableBackfillLots = ref<LotSummary[]>([])

const isStale = ref(false)

const previewItems = computed(() => editItems.value)

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function computeDiff(row: any): string {
  const book = parseFloat(String(row.bookQuantity))
  const actual = Number(row.actualQuantity)
  const diff = actual - book
  if (diff === 0) return '0'
  return diff > 0 ? `+${diff}` : String(diff)
}

async function loadLocationTree() {
  const tree: LocationTree = await fetchLocationTree()
  locationTreeData.value = tree.roots
}

async function loadAvailableBackfillLots() {
  try {
    const resp = await fetchLots({ pageSize: 1000 })
    availableBackfillLots.value = resp.items.filter(
      (lot) => lot.positions.some((p) => parseFloat(p.quantity) === 0),
    )
  } catch {
    // Ignore - backfill is optional
  }
}

async function loadStocktakeDetail(id: string) {
  detailLoading.value = true
  try {
    const detail = await fetchStocktake(id)
    stocktakeDetail.value = detail

    // Build lot name map from items
    const lMap = new Map<string, string>()
    for (const item of detail.items) {
      lMap.set(item.lotId, item.lotId.substring(0, 8))
    }
    lotNameMap.value = lMap

    editItems.value = detail.items.map((item) => ({
      ...item,
      actualQuantity: parseFloat(item.actualQuantity),
    }))

    // Load available backfill lots
    await loadAvailableBackfillLots()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载盘点详情失败')
  } finally {
    detailLoading.value = false
  }
}

function addBackfillItem() {
  if (!backfillLotId.value) {
    ElMessage.warning('请选择批次')
    return
  }
  if (backfillQuantity.value <= 0) {
    ElMessage.warning('实际数量必须大于 0')
    return
  }

  // Check if already exists
  const exists = editItems.value.some((item) => item.lotId === backfillLotId.value)
  if (exists) {
    ElMessage.warning('该批次已在列表中')
    return
  }

  const lot = availableBackfillLots.value.find((l) => l.lotId === backfillLotId.value)
  editItems.value.push({
    lotId: backfillLotId.value,
    locationId: selectedLocationId.value,
    bookQuantity: '0',
    actualQuantity: backfillQuantity.value,
    reason: backfillReason.value || null,
  })

  // Update lot name map
  if (lot) {
    lotNameMap.value.set(
      lot.lotId,
      lot.lotNumber ?? lot.lotId.substring(0, 8),
    )
  }

  // Reset backfill form
  backfillLotId.value = ''
  backfillQuantity.value = 0
  backfillReason.value = ''
}

async function handleCreate() {
  if (!selectedLocationId.value) return

  createLoading.value = true
  try {
    const result = await createStocktake({ locationId: selectedLocationId.value })
    stocktakeDetail.value = { ...stocktakeDetail.value!, id: result.id }
    await loadStocktakeDetail(result.id)
    currentStep.value = 1
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建盘点失败')
  } finally {
    createLoading.value = false
  }
}

async function handleSaveDraft() {
  if (!stocktakeDetail.value) return

  // Validate: differences must have reasons
  for (const item of editItems.value) {
    const book = parseFloat(item.bookQuantity)
    if (item.actualQuantity !== book && !item.reason) {
      ElMessage.error('盘点差异必须填写原因')
      return
    }
  }

  saveLoading.value = true
  try {
    await updateStocktakeDraft(stocktakeDetail.value.id, {
      version: stocktakeDetail.value.version,
      updates: editItems.value.map((item) => ({
        lotId: item.lotId,
        locationId: item.locationId,
        actualQuantity: String(item.actualQuantity),
        reason: item.reason || null,
      })),
    })
    // Reload detail to get updated version (optimistic lock increments version)
    await loadStocktakeDetail(stocktakeDetail.value.id)
    ElMessage.success('盘点草稿已保存')
    currentStep.value = 2
    isStale.value = false
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.errorCode === 'INVENTORY_STOCKTAKE_STALE') {
        isStale.value = true
        currentStep.value = 2
        ElMessage.warning('盘点数据已过期，请刷新')
      } else {
        ElMessage.error(err.message)
      }
    } else {
      ElMessage.error(err instanceof Error ? err.message : '保存失败')
    }
  } finally {
    saveLoading.value = false
  }
}

async function handleRefresh() {
  if (!stocktakeDetail.value) return

  refreshLoading.value = true
  try {
    await refreshStocktakeDraft(stocktakeDetail.value.id, {
      version: stocktakeDetail.value.version,
      locationId: selectedLocationId.value,
    })
    await loadStocktakeDetail(stocktakeDetail.value.id)
    isStale.value = false
    ElMessage.success('盘点快照已刷新')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '刷新失败')
  } finally {
    refreshLoading.value = false
  }
}

async function handleConfirm() {
  if (!stocktakeDetail.value) return

  // Validate: differences must have reasons
  for (const item of editItems.value) {
    const book = parseFloat(item.bookQuantity)
    if (item.actualQuantity !== book && !item.reason) {
      ElMessage.error('盘点差异必须填写原因')
      currentStep.value = 1
      return
    }
  }

  try {
    await ElMessageBox.confirm('确认盘点后将自动生成调整流水，是否继续？', '确认盘点', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return // User cancelled
  }

  confirmLoading.value = true
  try {
    const result = await confirmStocktake(stocktakeDetail.value.id, stocktakeDetail.value.version)
    ElMessage.success(`盘点完成，调整 ${result.adjustedCount} 条记录`)
    emit('saved')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.errorCode === 'INVENTORY_STOCKTAKE_STALE') {
        isStale.value = true
        ElMessage.warning('盘点数据已过期，请刷新后重试')
      } else {
        ElMessage.error(err.message)
      }
    } else {
      ElMessage.error(err instanceof Error ? err.message : '确认失败')
    }
  } finally {
    confirmLoading.value = false
  }
}

async function handleCancel() {
  if (!stocktakeDetail.value) {
    emit('update:modelValue', false)
    return
  }

  try {
    await ElMessageBox.confirm('取消盘点将删除所有已录入数据，是否继续？', '取消盘点', {
      confirmButtonText: '确认取消',
      cancelButtonText: '返回',
      type: 'warning',
    })
  } catch {
    return // User cancelled
  }

  cancelLoading.value = true
  try {
    await cancelStocktake(stocktakeDetail.value.id, stocktakeDetail.value.version)
    ElMessage.success('盘点已取消')
    emit('saved')
    emit('update:modelValue', false)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '取消失败')
  } finally {
    cancelLoading.value = false
  }
}

function prevStep() {
  if (currentStep.value > 0) {
    currentStep.value--
  }
}

function resetState() {
  currentStep.value = 0
  selectedLocationId.value = ''
  stocktakeDetail.value = null
  editItems.value = []
  lotNameMap.value = new Map()
  isStale.value = false
  showBackfill.value = false
  backfillLotId.value = ''
  backfillQuantity.value = 0
  backfillReason.value = ''
}

async function onOpen() {
  resetState()
  await loadLocationTree()

  // If editing existing stocktake
  if (props.stocktakeId) {
    selectedLocationId.value = '' // Will be set from detail
    await loadStocktakeDetail(props.stocktakeId)
    currentStep.value = props.startStep
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      onOpen()
    }
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
.backfill-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.backfill-form {
  margin-top: 12px;
}
</style>
