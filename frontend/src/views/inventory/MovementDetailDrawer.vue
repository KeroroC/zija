<template>
  <el-drawer
    :model-value="modelValue"
    title="流水详情"
    direction="rtl"
    size="560px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template v-if="movement">
      <section class="drawer-section">
        <h4 class="section-title">基本信息</h4>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="流水类型">
            <el-tag :type="tagType(movement.type)" size="small">
              {{ typeLabel(movement.type) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="物品名称">
            {{ itemName }}
          </el-descriptions-item>
          <el-descriptions-item label="数量">
            {{ movement.quantity }} {{ unitName }}
          </el-descriptions-item>
          <el-descriptions-item label="来源位置">
            {{ fromLocationName }}
          </el-descriptions-item>
          <el-descriptions-item label="目标位置">
            {{ toLocationName }}
          </el-descriptions-item>
          <el-descriptions-item label="原因">
            {{ movement.reason ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="备注">
            {{ movement.memo ?? '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ operatorName }}
          </el-descriptions-item>
          <el-descriptions-item label="业务时间">
            {{ formatTime(movement.businessTime) }}
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ formatTime(movement.createdAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <!-- Reversal relationship -->
      <section v-if="movement.reversalOf || movement.reversedBy" class="drawer-section">
        <h4 class="section-title">冲正关系</h4>
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item v-if="movement.reversalOf" label="冲正来源">
            本流水是对 {{ movement.reversalOf }} 的冲销
          </el-descriptions-item>
          <el-descriptions-item v-if="movement.reversedBy" label="已被冲正">
            已被流水 {{ movement.reversedBy }} 冲销
          </el-descriptions-item>
        </el-descriptions>
      </section>

      <!-- Admin reverse action -->
      <section v-if="canReverse" class="drawer-section">
        <el-button
          type="danger"
          :loading="reversing"
          @click="onReverse"
        >
          冲正此流水
        </el-button>
      </section>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { reverseMovement } from '../../api/inventory'
import { useSessionStore } from '../../stores/session'
import { ApiError } from '../../api/http'
import type { Movement, MovementType } from '../../types/inventory'

const props = defineProps<{
  modelValue: boolean
  movement: Movement | null
  itemNameMap: Map<string, string>
  locationNameMap: Map<string, string>
  itemUnitNameMap: Map<string, string>
  operatorNameMap: Map<string, string>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  reversed: []
}>()

const sessionStore = useSessionStore()
const reversing = ref(false)

const canReverse = computed(() => {
  if (!props.movement) return false
  const role = sessionStore.role
  if (role !== 'OWNER' && role !== 'ADMIN') return false
  if (props.movement.type === 'REVERSAL') return false
  if (props.movement.reversedBy) return false
  return true
})

const itemName = computed(() => {
  if (!props.movement) return '-'
  return props.itemNameMap.get(props.movement.itemId) ?? props.movement.itemName ?? '-'
})

const unitName = computed(() => {
  if (!props.movement) return ''
  return props.itemUnitNameMap.get(props.movement.itemId) ?? props.movement.unitName ?? ''
})

const fromLocationName = computed(() => {
  if (!props.movement?.fromLocationId) return '-'
  return props.locationNameMap.get(props.movement.fromLocationId) ?? props.movement.fromLocationName ?? '-'
})

const toLocationName = computed(() => {
  if (!props.movement?.toLocationId) return '-'
  return props.locationNameMap.get(props.movement.toLocationId) ?? props.movement.toLocationName ?? '-'
})

const operatorName = computed(() => {
  if (!props.movement) return '-'
  // The API returns operatorAccountId; try operatorNameMap first, then operatorUsername, then raw ID
  const accountId = (props.movement as unknown as Record<string, unknown>)['operatorAccountId'] as string | undefined
  if (accountId && props.operatorNameMap.has(accountId)) {
    return props.operatorNameMap.get(accountId)
  }
  return props.movement.operatorUsername ?? accountId ?? '-'
})

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

async function onReverse() {
  if (!props.movement) return
  try {
    await ElMessageBox.confirm(
      '确定要冲正此流水吗？此操作将创建一笔反向流水。',
      '冲正确认',
      { confirmButtonText: '确定冲正', cancelButtonText: '取消', type: 'warning' },
    )
  } catch {
    return // user cancelled
  }

  reversing.value = true
  try {
    await reverseMovement(
      props.movement.id,
      { reason: null, memo: null },
      crypto.randomUUID(),
    )
    ElMessage.success('冲正成功')
    emit('reversed')
    emit('update:modelValue', false)
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.errorCode === 'INVENTORY_MOVEMENT_ALREADY_REVERSED') {
        ElMessage.error('该流水已被冲正')
      } else if (err.errorCode === 'INVENTORY_REVERSAL_NOT_ALLOWED') {
        ElMessage.error('该类型流水不允许冲正')
      } else if (err.errorCode === 'INVENTORY_REVERSAL_WOULD_NEGATIVE') {
        ElMessage.error('冲正会导致库存为负，无法执行')
      } else {
        ElMessage.error(err.message || '冲正失败')
      }
    } else {
      ElMessage.error('冲正失败')
    }
  } finally {
    reversing.value = false
  }
}
</script>

<style scoped>
.drawer-section {
  margin-bottom: 24px;
}
.drawer-section:last-child {
  margin-bottom: 0;
}
.section-title {
  margin: 0 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
</style>
