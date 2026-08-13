<template>
  <el-drawer
    :model-value="modelValue"
    :title="isEdit ? '编辑物品' : '新建物品'"
    size="520px"
    append-to-body
    @close="close"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      label-position="top"
    >
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入物品名称" maxlength="100" />
      </el-form-item>

      <el-form-item label="管理类型" prop="managementType">
        <el-select v-model="form.managementType" placeholder="请选择管理类型" style="width: 100%">
          <el-option label="消耗品" value="CONSUMABLE" />
          <el-option label="耐用品" value="DURABLE" />
        </el-select>
      </el-form-item>

      <el-form-item label="单位" prop="unitId">
        <el-select
          v-model="form.unitId"
          placeholder="请选择单位"
          filterable
          :filter-method="onUnitFilter"
          style="width: 100%"
          @visible-change="onUnitSelectVisible"
        >
          <el-option
            v-for="u in filteredUnits"
            :key="u.id"
            :label="u.name"
            :value="u.id"
          />
          <template #footer>
            <el-button text type="primary" @click.stop="openCreateUnitDialog">
              + 新建单位…
            </el-button>
          </template>
        </el-select>
      </el-form-item>

      <el-form-item label="分类">
        <el-tree-select
          v-model="form.categoryId"
          :data="categoryTree"
          :props="{ label: 'name', value: 'id', children: 'children' } as any"
          placeholder="请选择分类（可选）"
          clearable
          check-strictly
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="品牌">
        <el-select
          v-model="form.brandId"
          placeholder="请选择品牌（可选）"
          clearable
          filterable
          :filter-method="onBrandFilter"
          style="width: 100%"
          @visible-change="onBrandSelectVisible"
        >
          <el-option
            v-for="b in filteredBrands"
            :key="b.id"
            :label="b.name"
            :value="b.id"
          />
          <template #footer>
            <el-button text type="primary" @click.stop="openCreateBrandDialog">
              + 新建品牌…
            </el-button>
          </template>
        </el-select>
      </el-form-item>

      <el-form-item label="标签">
        <el-select
          v-model="form.tagIds"
          placeholder="请选择标签（可选）"
          multiple
          clearable
          filterable
          :filter-method="onTagFilter"
          style="width: 100%"
          @visible-change="onTagSelectVisible"
        >
          <el-option
            v-for="t in filteredTags"
            :key="t.id"
            :label="t.name"
            :value="t.id"
          />
          <template #footer>
            <el-button text type="primary" @click.stop="openCreateTagDialog">
              + 新建标签…
            </el-button>
          </template>
        </el-select>
      </el-form-item>

      <el-form-item label="备注">
        <el-input
          v-model="form.memo"
          type="textarea"
          :rows="3"
          placeholder="请输入备注（可选）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-divider />

      <el-form-item label="到期提醒模式">
        <el-select v-model="form.expiryReminderMode" style="width: 100%">
          <el-option label="继承全局设置" value="INHERIT" />
          <el-option label="禁用" value="DISABLED" />
          <el-option label="自定义" value="CUSTOM" />
        </el-select>
      </el-form-item>

      <el-form-item
        v-if="form.expiryReminderMode === 'CUSTOM'"
        label="提醒天数"
      >
        <el-select
          v-model="form.expiryReminderDays"
          multiple
          filterable
          allow-create
          default-first-option
          placeholder="输入天数后回车添加（1–3650）"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item label="低库存提醒模式">
        <el-select v-model="form.lowStockMode" style="width: 100%">
          <el-option label="继承全局设置" value="INHERIT" />
          <el-option label="禁用" value="DISABLED" />
          <el-option label="自定义" value="CUSTOM" />
        </el-select>
      </el-form-item>

      <el-form-item
        v-if="form.lowStockMode === 'CUSTOM'"
        label="低库存阈值"
      >
        <el-input
          v-model="form.lowStockThreshold"
          placeholder="库存低于此值时提醒"
        >
          <template #append>
            <span style="color: var(--el-text-color-secondary); font-size: 12px">
              {{ selectedUnitDecimalScale !== null ? `精度：${selectedUnitDecimalScale} 位小数` : '请根据单位精度输入' }}
            </span>
          </template>
        </el-input>
      </el-form-item>

      <el-divider v-if="isEdit" />

      <el-form-item v-if="isEdit" label="封面图片">
        <ItemCoverUpload
          :item-id="props.item!.id"
          :cover-url="props.item!.coverUrl ?? null"
          :version="props.item!.version"
          @uploaded="onCoverUploaded"
          @removed="onCoverRemoved"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-drawer>

  <el-dialog
    v-model="createUnitVisible"
    title="新建单位"
    width="360px"
    append-to-body
    destroy-on-close
    class="create-unit-dialog"
    @closed="resetCreateUnitForm"
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="名称">
        <el-input
          v-model="createUnitForm.name"
          placeholder="请输入单位名称"
          maxlength="100"
        />
      </el-form-item>
      <el-form-item label="小数位">
        <el-input-number v-model="createUnitForm.decimalScale" :min="0" :max="6" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createUnitVisible = false">取消</el-button>
      <el-button type="primary" :loading="creatingUnit" @click="submitCreateUnit">
        确定
      </el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="createBrandVisible"
    title="新建品牌"
    width="360px"
    append-to-body
    destroy-on-close
    class="create-brand-dialog"
    @closed="resetCreateBrandForm"
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="名称">
        <el-input
          v-model="createBrandForm.name"
          placeholder="请输入品牌名称"
          maxlength="100"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createBrandVisible = false">取消</el-button>
      <el-button type="primary" :loading="creatingBrand" @click="submitCreateBrand">
        确定
      </el-button>
    </template>
  </el-dialog>

  <el-dialog
    v-model="createTagVisible"
    title="新建标签"
    width="360px"
    append-to-body
    destroy-on-close
    class="create-tag-dialog"
    @closed="resetCreateTagForm"
  >
    <el-form label-position="top" @submit.prevent>
      <el-form-item label="名称">
        <el-input
          v-model="createTagForm.name"
          placeholder="请输入标签名称"
          maxlength="100"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createTagVisible = false">取消</el-button>
      <el-button type="primary" :loading="creatingTag" @click="submitCreateTag">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  createItem,
  updateItem,
  fetchCategories,
  fetchBrands,
  fetchUnits,
  fetchTags,
  createBrand as apiCreateBrand,
  createTag as apiCreateTag,
  createUnit as apiCreateUnit,
} from '../api/catalog'
import type { CatalogItem, Category, Brand, Unit, Tag } from '../types/catalog'
import ItemCoverUpload from './ItemCoverUpload.vue'

interface CategoryTreeNode extends Category {
  children?: CategoryTreeNode[]
}

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    item: CatalogItem | null
    /** Prefill name when opening create mode (e.g. from inbound filter text). */
    presetName?: string
  }>(),
  {
    presetName: '',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  saved: [item: CatalogItem]
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)
const createUnitVisible = ref(false)
const creatingUnit = ref(false)
const unitFilterQuery = ref('')
const createUnitForm = reactive({
  name: '',
  decimalScale: 0,
})
const createBrandVisible = ref(false)
const creatingBrand = ref(false)
const brandFilterQuery = ref('')
const createBrandForm = reactive({
  name: '',
})
const createTagVisible = ref(false)
const creatingTag = ref(false)
const tagFilterQuery = ref('')
const createTagForm = reactive({
  name: '',
})

const units = ref<Unit[]>([])
const brands = ref<Brand[]>([])
const tags = ref<Tag[]>([])
const categoryTree = ref<CategoryTreeNode[]>([])

const isEdit = computed(() => !!props.item)

const filteredUnits = computed(() => {
  const q = unitFilterQuery.value.trim().toLowerCase()
  if (!q) return units.value
  return units.value.filter((u) => u.name.toLowerCase().includes(q))
})

const filteredBrands = computed(() => {
  const q = brandFilterQuery.value.trim().toLowerCase()
  if (!q) return brands.value
  return brands.value.filter((b) => b.name.toLowerCase().includes(q))
})

const filteredTags = computed(() => {
  const q = tagFilterQuery.value.trim().toLowerCase()
  if (!q) return tags.value
  return tags.value.filter((t) => t.name.toLowerCase().includes(q))
})

const defaultForm = () => ({
  name: '',
  managementType: 'CONSUMABLE' as string,
  unitId: '',
  categoryId: null as string | null,
  brandId: null as string | null,
  tagIds: [] as string[],
  memo: '',
  expiryReminderMode: 'INHERIT' as string,
  expiryReminderDays: [] as number[],
  lowStockMode: 'INHERIT' as string,
  lowStockThreshold: '' as string,
})

const selectedUnitDecimalScale = computed(() => {
  if (!form.unitId) return null
  const unit = units.value.find(u => u.id === form.unitId)
  return unit ? unit.decimalScale : null
})

const form = reactive(defaultForm())

const rules: FormRules = {
  name: [
    { required: true, message: '请输入物品名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过100个字符', trigger: 'blur' },
  ],
  managementType: [
    { required: true, message: '请选择管理类型', trigger: 'change' },
  ],
  unitId: [
    { required: true, message: '请选择单位', trigger: 'change' },
  ],
}

function buildCategoryTree(list: Category[]): CategoryTreeNode[] {
  const map = new Map<string, CategoryTreeNode>()
  const roots: CategoryTreeNode[] = []

  for (const cat of list) {
    map.set(cat.id, { ...cat, children: [] })
  }

  for (const node of map.values()) {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId)!.children!.push(node)
    } else {
      roots.push(node)
    }
  }

  return roots
}

async function loadDictionaries() {
  const [cats, b, u, t] = await Promise.all([
    fetchCategories(),
    fetchBrands(),
    fetchUnits(),
    fetchTags(),
  ])
  categoryTree.value = buildCategoryTree(cats)
  brands.value = b
  units.value = u
  tags.value = t
}

function resetForm() {
  const d = defaultForm()
  Object.assign(form, d)
  formRef.value?.clearValidate()
}

function fillForm(item: CatalogItem) {
  form.name = item.name
  form.managementType = item.managementType
  form.unitId = item.unitId
  form.categoryId = item.categoryId
  form.brandId = item.brandId
  form.tagIds = item.tagIds ? [...item.tagIds] : []
  form.memo = item.memo || ''
  form.expiryReminderMode = item.expiryReminderMode
  form.expiryReminderDays = item.expiryReminderDays ? [...item.expiryReminderDays] : []
  form.lowStockMode = item.lowStockMode
  form.lowStockThreshold = item.lowStockThreshold || ''
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      if (props.item) {
        fillForm(props.item)
      } else {
        resetForm()
        if (props.presetName.trim()) {
          form.name = props.presetName.trim()
        }
      }
    }
  },
  { immediate: true },
)

function close() {
  emit('update:modelValue', false)
}

function onCoverUploaded(payload: { coverFileId: string; coverUrl: string; version: number }) {
  if (props.item) {
    props.item.coverFileId = payload.coverFileId
    props.item.coverUrl = payload.coverUrl
    props.item.version = payload.version
  }
}

function onCoverRemoved() {
  if (props.item) {
    props.item.coverFileId = null
    props.item.coverUrl = undefined
    props.item.version++
  }
}

function onUnitFilter(query: string) {
  unitFilterQuery.value = query
}

function onUnitSelectVisible(visible: boolean) {
  if (!visible) {
    unitFilterQuery.value = ''
  }
}

function resetCreateUnitForm() {
  createUnitForm.name = ''
  createUnitForm.decimalScale = 0
}

function openCreateUnitDialog() {
  createUnitForm.name = unitFilterQuery.value.trim()
  createUnitForm.decimalScale = 0
  createUnitVisible.value = true
}

async function submitCreateUnit() {
  const name = createUnitForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入单位名称')
    return
  }
  creatingUnit.value = true
  try {
    const created = await apiCreateUnit({
      name,
      decimalScale: createUnitForm.decimalScale ?? 0,
    })
    units.value.push(created)
    unitFilterQuery.value = ''
    form.unitId = created.id
    createUnitVisible.value = false
    ElMessage.success('单位已创建')
  } catch (e: any) {
    ElMessage.error(e.message || '创建单位失败')
  } finally {
    creatingUnit.value = false
  }
}

function onBrandFilter(query: string) {
  brandFilterQuery.value = query
}

function onBrandSelectVisible(visible: boolean) {
  if (!visible) {
    brandFilterQuery.value = ''
  }
}

function resetCreateBrandForm() {
  createBrandForm.name = ''
}

function openCreateBrandDialog() {
  createBrandForm.name = brandFilterQuery.value.trim()
  createBrandVisible.value = true
}

async function submitCreateBrand() {
  const name = createBrandForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入品牌名称')
    return
  }
  creatingBrand.value = true
  try {
    const created = await apiCreateBrand(name)
    brands.value.push(created)
    brandFilterQuery.value = ''
    form.brandId = created.id
    createBrandVisible.value = false
    ElMessage.success('品牌已创建')
  } catch (e: any) {
    ElMessage.error(e.message || '创建品牌失败')
  } finally {
    creatingBrand.value = false
  }
}

function onTagFilter(query: string) {
  tagFilterQuery.value = query
}

function onTagSelectVisible(visible: boolean) {
  if (!visible) {
    tagFilterQuery.value = ''
  }
}

function resetCreateTagForm() {
  createTagForm.name = ''
}

function openCreateTagDialog() {
  createTagForm.name = tagFilterQuery.value.trim()
  createTagVisible.value = true
}

async function submitCreateTag() {
  const name = createTagForm.name.trim()
  if (!name) {
    ElMessage.warning('请输入标签名称')
    return
  }
  creatingTag.value = true
  try {
    const created = await apiCreateTag(name)
    tags.value.push(created)
    tagFilterQuery.value = ''
    if (!form.tagIds.includes(created.id)) {
      form.tagIds.push(created.id)
    }
    createTagVisible.value = false
    ElMessage.success('标签已创建')
  } catch (e: any) {
    ElMessage.error(e.message || '创建标签失败')
  } finally {
    creatingTag.value = false
  }
}

function buildSubmitData() {
  const data: Record<string, unknown> = {
    name: form.name,
    managementType: form.managementType,
    unitId: form.unitId,
    categoryId: form.categoryId || undefined,
    brandId: form.brandId || undefined,
    tagIds: form.tagIds.length > 0 ? form.tagIds : undefined,
    memo: form.memo || undefined,
    expiryReminderMode: form.expiryReminderMode,
    expiryReminderDays: form.expiryReminderMode === 'CUSTOM' && form.expiryReminderDays.length > 0
      ? [...form.expiryReminderDays].sort((a, b) => b - a)
      : undefined,
    lowStockMode: form.lowStockMode,
    lowStockThreshold: form.lowStockMode === 'CUSTOM' && form.lowStockThreshold
      ? form.lowStockThreshold
      : undefined,
  }
  return data
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = buildSubmitData()
    let saved: CatalogItem
    if (isEdit.value && props.item) {
      data.version = props.item.version
      saved = await updateItem(props.item.id, data)
      ElMessage.success('物品已更新')
    } else {
      saved = await createItem(data as Parameters<typeof createItem>[0])
      ElMessage.success('物品已创建')
    }
    emit('saved', saved)
    close()
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

onMounted(loadDictionaries)
</script>
