<template>
  <el-dialog
    :model-value="modelValue"
    title="入库"
    width="800px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:modelValue', $event)"
    @open="onOpen"
  >
    <el-steps :active="currentStep" finish-status="success" align-center style="margin-bottom: 24px">
      <el-step title="入库信息" />
      <el-step title="确认入库" />
    </el-steps>

    <!-- Step 0: Form -->
    <div v-if="currentStep === 0">
      <el-form label-width="100px" v-loading="formLoading">
        <!-- Mode selection -->
        <el-form-item label="入库方式">
          <el-radio-group v-model="mode" @change="onModeChange">
            <el-radio value="new">新建批次</el-radio>
            <el-radio value="existing">补充现有批次</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- Item selection (both modes) -->
        <el-form-item label="物品">
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

        <!-- Lot selection (existing mode only) -->
        <el-form-item v-if="mode === 'existing'" label="批次">
          <el-select
            v-model="form.lotId"
            filterable
            placeholder="选择批次"
            style="width: 100%"
            :disabled="!form.itemId"
            @change="resetIdempotencyKey"
          >
            <el-option
              v-for="lot in availableLots"
              :key="lot.lotId"
              :label="lotLabel(lot)"
              :value="lot.lotId"
            />
          </el-select>
        </el-form-item>

        <!-- Quantity -->
        <el-form-item label="数量">
          <el-input-number
            v-model="form.quantity"
            :min="0.01"
            :precision="quantityPrecision"
            :step="1"
            controls-position="right"
            style="width: 200px"
            @change="resetIdempotencyKey"
          />
          <span v-if="unitName" style="margin-left: 8px">{{ unitName }}</span>
        </el-form-item>

        <!-- Lot metadata (new mode only) -->
        <template v-if="mode === 'new'">
          <el-form-item label="购入日期">
            <el-date-picker
              v-model="form.purchaseDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @change="resetIdempotencyKey"
            />
          </el-form-item>
          <el-form-item label="生产日期">
            <el-date-picker
              v-model="form.productionDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @change="resetIdempotencyKey"
            />
          </el-form-item>
          <el-form-item label="有效期至">
            <el-date-picker
              v-model="form.expiryDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
              @change="resetIdempotencyKey"
            />
          </el-form-item>
          <el-form-item label="批次号">
            <el-input
              v-model="form.lotNumber"
              placeholder="可选"
              @change="resetIdempotencyKey"
            />
          </el-form-item>
          <el-form-item label="序列号">
            <el-input
              v-model="form.serialNumber"
              placeholder="可选"
              @change="resetIdempotencyKey"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="form.memo"
              type="textarea"
              :rows="2"
              placeholder="可选"
              @change="resetIdempotencyKey"
            />
          </el-form-item>
        </template>

        <!-- Location selection -->
        <el-form-item label="入库位置">
          <el-tree-select
            v-model="form.locationId"
            :data="locationTreeData"
            :props="{ label: 'name', value: 'id', children: 'children' }"
            placeholder="选择位置"
            check-strictly
            style="width: 100%"
            @change="resetIdempotencyKey"
          />
        </el-form-item>
      </el-form>
    </div>

    <!-- Step 1: Confirm preview -->
    <div v-if="currentStep === 1">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="物品">{{ selectedItemName }}</el-descriptions-item>
        <el-descriptions-item label="批次">
          {{ mode === 'new' ? '新建批次' : selectedLotLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="数量">
          {{ form.quantity }} {{ unitName }}
        </el-descriptions-item>
        <el-descriptions-item label="入库位置">{{ selectedLocationName }}</el-descriptions-item>
        <el-descriptions-item v-if="mode === 'new' && form.purchaseDate" label="购入日期">
          {{ form.purchaseDate }}
        </el-descriptions-item>
        <el-descriptions-item v-if="mode === 'new' && form.expiryDate" label="有效期至">
          {{ form.expiryDate }}
        </el-descriptions-item>
        <el-descriptions-item v-if="mode === 'new' && form.lotNumber" label="批次号">
          {{ form.lotNumber }}
        </el-descriptions-item>
        <el-descriptions-item v-if="mode === 'new' && form.serialNumber" label="序列号">
          {{ form.serialNumber }}
        </el-descriptions-item>
      </el-descriptions>

      <el-alert
        v-if="serialDuplicated"
        title="序列号重复提醒"
        description="该序列号已存在于同一批次中，请确认是否继续。"
        type="warning"
        show-icon
        :closable="false"
        style="margin-top: 16px"
      />
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="close">取消</el-button>
        <div class="footer-right">
          <el-button v-if="currentStep > 0" @click="prevStep">上一步</el-button>
          <el-button
            v-if="currentStep === 0"
            type="primary"
            :disabled="!canProceedToPreview"
            @click="nextStep"
          >
            下一步
          </el-button>
          <el-button
            v-if="currentStep === 1"
            type="primary"
            :loading="submitting"
            @click="handleSubmit"
          >
            确认入库
          </el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchItems, fetchUnits } from '../../api/catalog'
import { fetchLots, inboundNewLot, inboundExistingLot } from '../../api/inventory'
import { fetchLocationTree } from '../../api/location'
import { ApiError } from '../../api/http'
import type { CatalogItem, Unit } from '../../types/catalog'
import type { LotSummary } from '../../types/inventory'
import type { LocationNode, LocationTree } from '../../types/location'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  done: []
}>()

const currentStep = ref(0)
const mode = ref<'new' | 'existing'>('new')
const formLoading = ref(false)
const submitting = ref(false)
const serialDuplicated = ref(false)

const idempotencyKey = ref(crypto.randomUUID())

const activeItems = ref<CatalogItem[]>([])
const units = ref<Unit[]>([])
const availableLots = ref<LotSummary[]>([])
const locationTreeData = ref<LocationNode[]>([])

const form = ref({
  itemId: '',
  lotId: '',
  quantity: 1,
  purchaseDate: '',
  productionDate: '',
  expiryDate: '',
  lotNumber: '',
  serialNumber: '',
  memo: '',
  locationId: '',
})

const unitMap = computed(() => {
  const map = new Map<string, Unit>()
  for (const u of units.value) {
    map.set(u.id, u)
  }
  return map
})

const selectedItem = computed(() =>
  activeItems.value.find((i) => i.id === form.value.itemId),
)

const unitName = computed(() => {
  if (!selectedItem.value) return ''
  return unitMap.value.get(selectedItem.value.unitId)?.name ?? ''
})

const quantityPrecision = computed(() => {
  if (!selectedItem.value) return 2
  const unit = unitMap.value.get(selectedItem.value.unitId)
  return unit?.decimalScale ?? 2
})

const selectedItemName = computed(() => selectedItem.value?.name ?? '')

const selectedLotLabel = computed(() => {
  const lot = availableLots.value.find((l) => l.lotId === form.value.lotId)
  return lot ? lotLabel(lot) : ''
})

const selectedLocationName = computed(() => {
  const find = (nodes: LocationNode[]): string => {
    for (const n of nodes) {
      if (n.id === form.value.locationId) return n.name
      const child = find(n.children)
      if (child) return child
    }
    return ''
  }
  return find(locationTreeData.value)
})

const canProceedToPreview = computed(() => {
  if (!form.value.itemId || !form.value.locationId || form.value.quantity <= 0) return false
  if (mode.value === 'existing' && !form.value.lotId) return false
  return true
})

function lotLabel(lot: LotSummary): string {
  const parts = [lot.itemName]
  if (lot.lotNumber) parts.push(lot.lotNumber)
  if (lot.serialNumber) parts.push(`SN:${lot.serialNumber}`)
  if (lot.expiryDate) parts.push(`效期:${lot.expiryDate}`)
  return parts.join(' - ')
}

function resetIdempotencyKey() {
  idempotencyKey.value = crypto.randomUUID()
}

function onModeChange() {
  form.value.lotId = ''
  form.value.quantity = 1
  form.value.purchaseDate = ''
  form.value.productionDate = ''
  form.value.expiryDate = ''
  form.value.lotNumber = ''
  form.value.serialNumber = ''
  form.value.memo = ''
  resetIdempotencyKey()
}

async function onItemChange() {
  form.value.lotId = ''
  form.value.quantity = 1
  resetIdempotencyKey()

  if (mode.value === 'existing' && form.value.itemId) {
    try {
      const resp = await fetchLots({ itemId: form.value.itemId, pageSize: 1000 })
      availableLots.value = resp.items
    } catch {
      availableLots.value = []
    }
  }
}

function nextStep() {
  if (!canProceedToPreview.value) return
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
  submitting.value = true
  serialDuplicated.value = false

  try {
    let result
    if (mode.value === 'new') {
      result = await inboundNewLot(
        {
          itemId: form.value.itemId,
          quantity: String(form.value.quantity),
          locationId: form.value.locationId,
          purchaseDate: form.value.purchaseDate || null,
          productionDate: form.value.productionDate || null,
          expiryDate: form.value.expiryDate || null,
          lotNumber: form.value.lotNumber || null,
          serialNumber: form.value.serialNumber || null,
          memo: form.value.memo || null,
        },
        idempotencyKey.value,
      )
    } else {
      result = await inboundExistingLot(
        {
          lotId: form.value.lotId,
          locationId: form.value.locationId,
          quantity: String(form.value.quantity),
          memo: form.value.memo || null,
        },
        idempotencyKey.value,
      )
    }

    if (result.serialDuplicated) {
      serialDuplicated.value = true
      ElMessage.warning('序列号重复，入库已完成')
    } else {
      ElMessage.success('入库成功')
    }

    emit('done')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.errorCode === 'INVENTORY_ARCHIVED_ITEM') {
        ElMessage.error('该物品已归档，无法入库。请先恢复物品状态。')
      } else {
        ElMessage.error(err.message)
      }
    } else {
      ElMessage.error(err instanceof Error ? err.message : '入库失败')
    }
  } finally {
    submitting.value = false
  }
}

function resetState() {
  currentStep.value = 0
  mode.value = 'new'
  serialDuplicated.value = false
  idempotencyKey.value = crypto.randomUUID()
  form.value = {
    itemId: '',
    lotId: '',
    quantity: 1,
    purchaseDate: '',
    productionDate: '',
    expiryDate: '',
    lotNumber: '',
    serialNumber: '',
    memo: '',
    locationId: '',
  }
  availableLots.value = []
}

async function onOpen() {
  resetState()
  formLoading.value = true
  try {
    const [itemsResp, locTree, unitsResp] = await Promise.all([
      fetchItems({ status: 'ACTIVE', pageSize: 1000 }),
      fetchLocationTree(),
      fetchUnits(),
    ])
    activeItems.value = itemsResp.items
    locationTreeData.value = locTree.roots
    units.value = unitsResp
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
