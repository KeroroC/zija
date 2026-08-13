<template>
  <el-drawer
    :model-value="modelValue"
    title="批次详情"
    direction="rtl"
    size="560px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template v-if="lot">
      <!-- Position distribution -->
      <section class="drawer-section">
        <h4 class="section-title">库存分布</h4>
        <el-table :data="lot.positions" style="width: 100%" size="small">
          <el-table-column prop="locationName" label="位置" min-width="120" />
          <el-table-column label="数量" min-width="100">
            <template #default="{ row }">
              {{ row.quantity }} {{ lot.unitName }}
            </template>
          </el-table-column>
        </el-table>
      </section>

      <!-- Lot metadata -->
      <section class="drawer-section">
        <div class="section-header">
          <h4 class="section-title">批次信息</h4>
          <el-button
            v-if="!editing"
            type="primary"
            link
            size="small"
            @click="startEdit"
          >
            编辑
          </el-button>
        </div>

        <!-- View mode -->
        <el-descriptions v-if="!editing" :column="1" border size="small">
          <el-descriptions-item label="物品名称">{{ lot.itemName }}</el-descriptions-item>
          <el-descriptions-item label="总数量">{{ lot.totalQuantity }} {{ lot.unitName }}</el-descriptions-item>
          <el-descriptions-item label="批次号">{{ lot.lotNumber ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="序列号">{{ lot.serialNumber ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="采购日期">{{ lot.purchaseDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="生产日期">{{ lot.productionDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ lot.expiryDate ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注">{{ lot.memo ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- Edit mode -->
        <el-form
          v-else
          :model="editForm"
          label-width="80px"
          size="small"
          @submit.prevent="submitEdit"
        >
          <el-form-item label="序列号">
            <el-input v-model="editForm.serialNumber" placeholder="留空清除" clearable />
          </el-form-item>
          <el-form-item label="采购日期">
            <el-date-picker
              v-model="editForm.purchaseDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              :shortcuts="pastDateShortcuts"
              clearable
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="生产日期">
            <el-date-picker
              v-model="editForm.productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              :shortcuts="pastDateShortcuts"
              clearable
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="有效期至">
            <el-date-picker
              v-model="editForm.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              :shortcuts="futureDateShortcuts"
              clearable
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input
              v-model="editForm.memo"
              type="textarea"
              :rows="2"
              placeholder="留空清除"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" @click="submitEdit">
              保存
            </el-button>
            <el-button @click="cancelEdit">取消</el-button>
          </el-form-item>
        </el-form>
      </section>

      <!-- Related movements -->
      <section class="drawer-section">
        <h4 class="section-title">相关流水</h4>
        <el-table
          :data="movements"
          v-loading="movementsLoading"
          style="width: 100%"
          size="small"
        >
          <el-table-column label="类型" min-width="70">
            <template #default="{ row }">
              {{ movementTypeLabel(row.type) }}
            </template>
          </el-table-column>
          <el-table-column label="数量" min-width="80">
            <template #default="{ row }">
              {{ row.quantity }} {{ lot.unitName }}
            </template>
          </el-table-column>
          <el-table-column label="来源/目标" min-width="100">
            <template #default="{ row }">
              {{ row.fromLocationName ?? row.toLocationName ?? '-' }}
            </template>
          </el-table-column>
          <el-table-column label="时间" min-width="140">
            <template #default="{ row }">
              {{ formatTime(row.businessTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="memo" label="备注" min-width="100">
            <template #default="{ row }">
              {{ row.memo ?? '-' }}
            </template>
          </el-table-column>
        </el-table>
      </section>
    </template>

    <template v-else-if="loading">
      <div v-loading="true" style="min-height: 200px" />
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { fetchLot, fetchMovements, updateLotMeta } from '../../api/inventory'
import { ApiError } from '../../api/http'
import type { LotSummary, Movement, MovementType } from '../../types/inventory'
import { futureDateShortcuts, pastDateShortcuts } from '../../utils/date'

const props = defineProps<{
  modelValue: boolean
  lotId: string | null
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  updated: []
}>()

const lot = ref<LotSummary | null>(null)
const loading = ref(false)
const editing = ref(false)
const saving = ref(false)
const movements = ref<Movement[]>([])
const movementsLoading = ref(false)

const editForm = ref({
  serialNumber: '',
  purchaseDate: '',
  productionDate: '',
  expiryDate: '',
  memo: '',
})

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

function formatTime(iso: string): string {
  if (!iso) return '-'
  return iso.replace('T', ' ').replace(/\.\d+Z$/, '')
}

async function loadLot(lotId: string) {
  loading.value = true
  try {
    lot.value = await fetchLot(lotId)
  } finally {
    loading.value = false
  }
}

async function loadMovements(lotId: string) {
  movementsLoading.value = true
  try {
    const resp = await fetchMovements({ lotId, page: 1, pageSize: 50 })
    movements.value = resp.items
  } finally {
    movementsLoading.value = false
  }
}

function startEdit() {
  if (!lot.value) return
  editForm.value = {
    serialNumber: lot.value.serialNumber ?? '',
    purchaseDate: lot.value.purchaseDate ?? '',
    productionDate: lot.value.productionDate ?? '',
    expiryDate: lot.value.expiryDate ?? '',
    memo: lot.value.memo ?? '',
  }
  editing.value = true
}

function cancelEdit() {
  editing.value = false
}

async function submitEdit() {
  if (!lot.value) return
  saving.value = true
  try {
    const updated = await updateLotMeta(lot.value.lotId, {
      version: lot.value.version,
      purchaseDate: editForm.value.purchaseDate || null,
      productionDate: editForm.value.productionDate || null,
      expiryDate: editForm.value.expiryDate || null,
      serialNumber: editForm.value.serialNumber || null,
      memo: editForm.value.memo || null,
    })
    lot.value = updated
    editing.value = false
    ElMessage.success('批次信息已更新')
    emit('updated')
  } catch (err) {
    if (err instanceof ApiError && err.errorCode === 'lot_version_conflict') {
      ElMessage.error('数据已被其他人修改，请关闭后重新打开')
    } else {
      ElMessage.error(err instanceof Error ? err.message : '保存失败')
    }
  } finally {
    saving.value = false
  }
}

const activeLotId = computed(() => (props.modelValue ? props.lotId : null))

watch(
  activeLotId,
  (lotId) => {
    if (lotId) {
      editing.value = false
      loadLot(lotId)
      loadMovements(lotId)
    }
  },
  { immediate: true },
)
</script>

<style scoped>
.drawer-section {
  margin-bottom: 24px;
}
.drawer-section:last-child {
  margin-bottom: 0;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.section-header .section-title {
  margin-bottom: 0;
}
</style>
