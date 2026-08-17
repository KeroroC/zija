<template>
  <el-dialog
    :model-value="modelValue"
    :title="parentId ? '新增子位置' : '新增根位置'"
    width="400px"
    append-to-body
    destroy-on-close
    data-testid="location-create-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p v-if="parentName" class="parent-hint">父位置：{{ parentName }}</p>
    <el-form @submit.prevent="submit">
      <el-form-item label="名称" :error="nameError">
        <el-input
          v-model="name"
          maxlength="100"
          placeholder="请输入位置名称"
          @input="nameError = ''"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createLocation } from '../api/location'
import type { LocationInfo } from '../types/location'

const props = defineProps<{
  modelValue: boolean
  parentId: string | null
  parentName: string | null
  existingNames: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  created: [location: LocationInfo]
}>()

const name = ref('')
const nameError = ref('')
const submitting = ref(false)

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      name.value = ''
      nameError.value = ''
      submitting.value = false
    }
  },
)

function normalizeName(value: string): string {
  return value.normalize('NFKC').trim().toLocaleLowerCase()
}

async function submit() {
  const trimmed = name.value.trim()
  if (!trimmed) {
    ElMessage.warning('请输入名称')
    return
  }
  const normalized = normalizeName(trimmed)
  if (props.existingNames.some((s) => normalizeName(s) === normalized)) {
    nameError.value = '该名称已存在'
    return
  }
  submitting.value = true
  try {
    const location = await createLocation({
      name: trimmed,
      parentId: props.parentId ?? null,
      sortOrder: 0,
    })
    emit('created', location)
    emit('update:modelValue', false)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建位置失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.parent-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--zj-ink-600);
}
</style>