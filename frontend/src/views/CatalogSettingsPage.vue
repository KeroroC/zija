<template>
  <div class="catalog-settings-page">
    <div class="page-header">
      <h2>目录设置</h2>
    </div>

    <el-tabs v-model="activeTab">
      <!-- 分类 -->
      <el-tab-pane label="分类" name="categories">
        <div class="tab-toolbar">
          <el-switch v-model="includeArchived.categories" active-text="显示已归档" @change="loadCategories" />
          <el-button type="primary" size="small" @click="openCreateCategory()">添加根分类</el-button>
        </div>
        <el-tree
          v-loading="loading.categories"
          :data="categoryTree"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
        >
          <template #default="{ data }">
            <div class="tree-node">
              <span>
                {{ data.name }}
                <el-tag v-if="data.status === 'ARCHIVED'" type="info" size="small" style="margin-left: 8px">已归档</el-tag>
              </span>
              <span class="tree-node-actions">
                <el-button size="small" text @click.stop="openCreateCategory(data.id, data.name)">+</el-button>
                <el-button size="small" text @click.stop="openRename('category', data)">重命名</el-button>
                <el-button v-if="data.status === 'ACTIVE'" size="small" text type="danger" @click.stop="handleArchiveCategory(data)">归档</el-button>
                <el-button v-if="data.status === 'ARCHIVED'" size="small" text type="success" @click.stop="handleRestoreCategory(data)">恢复</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </el-tab-pane>

      <!-- 品牌 -->
      <el-tab-pane label="品牌" name="brands">
        <div class="tab-toolbar">
          <el-switch v-model="includeArchived.brands" active-text="显示已归档" @change="loadBrands" />
        </div>
        <div class="create-form">
          <el-input v-model="newBrandName" placeholder="新品牌名称" maxlength="100" style="width: 240px" />
          <el-button type="primary" @click="handleCreateBrand">添加</el-button>
        </div>
        <el-table :data="brands" v-loading="loading.brands">
          <el-table-column prop="name" label="名称" min-width="200" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ row.status === 'ACTIVE' ? '活跃' : '已归档' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'ACTIVE'" size="small" text type="danger" @click="handleArchiveBrand(row)">归档</el-button>
              <el-button v-if="row.status === 'ARCHIVED'" size="small" text type="success" @click="handleRestoreBrand(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 单位 -->
      <el-tab-pane label="单位" name="units">
        <div class="tab-toolbar">
          <el-switch v-model="includeArchived.units" active-text="显示已归档" @change="loadUnits" />
        </div>
        <div class="create-form">
          <el-input v-model="newUnitName" placeholder="新单位名称" maxlength="100" style="width: 200px" />
          <el-input-number v-model="newUnitDecimalScale" :min="0" :max="10" placeholder="小数位" style="width: 140px" />
          <el-button type="primary" @click="handleCreateUnit">添加</el-button>
        </div>
        <el-table :data="units" v-loading="loading.units">
          <el-table-column prop="name" label="名称" min-width="200" />
          <el-table-column prop="decimalScale" label="小数位" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ row.status === 'ACTIVE' ? '活跃' : '已归档' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openRename('unit', row)">重命名</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 标签 -->
      <el-tab-pane label="标签" name="tags">
        <div class="tab-toolbar">
          <el-switch v-model="includeArchived.tags" active-text="显示已归档" @change="loadTags" />
        </div>
        <div class="create-form">
          <el-input v-model="newTagName" placeholder="新标签名称" maxlength="100" style="width: 240px" />
          <el-button type="primary" @click="handleCreateTag">添加</el-button>
        </div>
        <el-table :data="tags" v-loading="loading.tags">
          <el-table-column prop="name" label="名称" min-width="200" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ row.status === 'ACTIVE' ? '活跃' : '已归档' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" text @click="openRename('tag', row)">重命名</el-button>
              <el-button v-if="row.status === 'ACTIVE'" size="small" text type="danger" @click="handleArchiveTag(row)">归档</el-button>
              <el-button v-if="row.status === 'ARCHIVED'" size="small" text type="success" @click="handleRestoreTag(row)">恢复</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- Create Category Dialog -->
    <el-dialog v-model="createCategoryVisible" :title="createCategoryTitle" width="400px">
      <el-form :model="createCategoryForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="createCategoryForm.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="createCategoryForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createCategoryVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateCategory">确定</el-button>
      </template>
    </el-dialog>

    <!-- Rename Dialog (category / unit / tag) -->
    <el-dialog v-model="renameDialogVisible" :title="renameDialogTitle" width="400px">
      <el-form :model="renameForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="renameForm.name" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRename">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchCategories, createCategory, renameCategory, archiveCategory, restoreCategory,
  fetchBrands, createBrand, archiveBrand, restoreBrand,
  fetchUnits, createUnit, renameUnit,
  fetchTags, createTag, renameTag, archiveTag, restoreTag,
} from '../api/catalog'
import type { Category, Brand, Unit, Tag } from '../types/catalog'

// --------------- Types ---------------

interface CategoryTreeNode extends Category {
  children: CategoryTreeNode[]
}

// --------------- State ---------------

const activeTab = ref('categories')

const loading = reactive({
  categories: false,
  brands: false,
  units: false,
  tags: false,
})

const includeArchived = reactive({
  categories: false,
  brands: false,
  units: false,
  tags: false,
})

const categories = ref<Category[]>([])
const brands = ref<Brand[]>([])
const units = ref<Unit[]>([])
const tags = ref<Tag[]>([])

// Create forms – brands / tags (inline)
const newBrandName = ref('')
const newTagName = ref('')

// Create form – units (inline)
const newUnitName = ref('')
const newUnitDecimalScale = ref(0)

// Create category dialog
const createCategoryVisible = ref(false)
const createCategoryForm = reactive({
  parentId: null as string | null,
  parentName: '',
  name: '',
  sortOrder: 0,
})
const createCategoryTitle = computed(() =>
  createCategoryForm.parentId
    ? `添加子分类（${createCategoryForm.parentName}）`
    : '添加根分类',
)

// Rename dialog (shared for category / unit / tag)
const renameDialogVisible = ref(false)
const renameDialogTitle = ref('')
const renameForm = reactive({
  type: '' as 'category' | 'unit' | 'tag',
  id: '',
  name: '',
  version: 0,
})

// --------------- Computed ---------------

const categoryTree = computed<CategoryTreeNode[]>(() => {
  const map = new Map<string, CategoryTreeNode>()
  const roots: CategoryTreeNode[] = []

  for (const cat of categories.value) {
    map.set(cat.id, { ...cat, children: [] })
  }
  for (const cat of categories.value) {
    const node = map.get(cat.id)!
    if (cat.parentId && map.has(cat.parentId)) {
      map.get(cat.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

// --------------- Load ---------------

async function loadCategories() {
  loading.categories = true
  try {
    categories.value = await fetchCategories(includeArchived.categories)
  } catch (e: any) {
    ElMessage.error(e.title || '加载分类失败')
  } finally {
    loading.categories = false
  }
}

async function loadBrands() {
  loading.brands = true
  try {
    brands.value = await fetchBrands(includeArchived.brands)
  } catch (e: any) {
    ElMessage.error(e.title || '加载品牌失败')
  } finally {
    loading.brands = false
  }
}

async function loadUnits() {
  loading.units = true
  try {
    units.value = await fetchUnits(includeArchived.units)
  } catch (e: any) {
    ElMessage.error(e.title || '加载单位失败')
  } finally {
    loading.units = false
  }
}

async function loadTags() {
  loading.tags = true
  try {
    tags.value = await fetchTags(includeArchived.tags)
  } catch (e: any) {
    ElMessage.error(e.title || '加载标签失败')
  } finally {
    loading.tags = false
  }
}

// --------------- Category actions ---------------

function openCreateCategory(parentId: string | null = null, parentName: string = '') {
  createCategoryForm.parentId = parentId
  createCategoryForm.parentName = parentName
  createCategoryForm.name = ''
  createCategoryForm.sortOrder = 0
  createCategoryVisible.value = true
}

async function handleCreateCategory() {
  if (!createCategoryForm.name.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  try {
    await createCategory({
      name: createCategoryForm.name.trim(),
      parentId: createCategoryForm.parentId || undefined,
      sortOrder: createCategoryForm.sortOrder,
    })
    createCategoryVisible.value = false
    await loadCategories()
    ElMessage.success('已创建')
  } catch (e: any) {
    ElMessage.error(e.title || '创建失败')
  }
}

async function handleArchiveCategory(cat: CategoryTreeNode) {
  await ElMessageBox.confirm(`确定归档分类"${cat.name}"？`, '确认')
  try {
    await archiveCategory(cat.id, cat.version)
    await loadCategories()
    ElMessage.success('已归档')
  } catch (e: any) {
    ElMessage.error(e.title || '归档失败')
  }
}

async function handleRestoreCategory(cat: CategoryTreeNode) {
  try {
    await restoreCategory(cat.id, cat.version)
    await loadCategories()
    ElMessage.success('已恢复')
  } catch (e: any) {
    ElMessage.error(e.title || '恢复失败')
  }
}

// --------------- Brand actions ---------------

async function handleCreateBrand() {
  if (!newBrandName.value.trim()) {
    ElMessage.warning('请输入品牌名称')
    return
  }
  try {
    await createBrand(newBrandName.value.trim())
    newBrandName.value = ''
    await loadBrands()
    ElMessage.success('已创建')
  } catch (e: any) {
    ElMessage.error(e.title || '创建失败')
  }
}

async function handleArchiveBrand(brand: any) {
  await ElMessageBox.confirm(`确定归档品牌"${brand.name}"？`, '确认')
  try {
    await archiveBrand(brand.id, brand.version)
    await loadBrands()
    ElMessage.success('已归档')
  } catch (e: any) {
    ElMessage.error(e.title || '归档失败')
  }
}

async function handleRestoreBrand(brand: any) {
  try {
    await restoreBrand(brand.id, brand.version)
    await loadBrands()
    ElMessage.success('已恢复')
  } catch (e: any) {
    ElMessage.error(e.title || '恢复失败')
  }
}

// --------------- Unit actions ---------------

async function handleCreateUnit() {
  if (!newUnitName.value.trim()) {
    ElMessage.warning('请输入单位名称')
    return
  }
  try {
    await createUnit({ name: newUnitName.value.trim(), decimalScale: newUnitDecimalScale.value })
    newUnitName.value = ''
    newUnitDecimalScale.value = 0
    await loadUnits()
    ElMessage.success('已创建')
  } catch (e: any) {
    ElMessage.error(e.title || '创建失败')
  }
}

// --------------- Tag actions ---------------

async function handleCreateTag() {
  if (!newTagName.value.trim()) {
    ElMessage.warning('请输入标签名称')
    return
  }
  try {
    await createTag(newTagName.value.trim())
    newTagName.value = ''
    await loadTags()
    ElMessage.success('已创建')
  } catch (e: any) {
    ElMessage.error(e.title || '创建失败')
  }
}

async function handleArchiveTag(tag: any) {
  await ElMessageBox.confirm(`确定归档标签"${tag.name}"？`, '确认')
  try {
    await archiveTag(tag.id, tag.version)
    await loadTags()
    ElMessage.success('已归档')
  } catch (e: any) {
    ElMessage.error(e.title || '归档失败')
  }
}

async function handleRestoreTag(tag: any) {
  try {
    await restoreTag(tag.id, tag.version)
    await loadTags()
    ElMessage.success('已恢复')
  } catch (e: any) {
    ElMessage.error(e.title || '恢复失败')
  }
}

// --------------- Rename dialog ---------------

function openRename(type: 'category' | 'unit' | 'tag', item: any) {
  renameForm.type = type
  renameForm.id = item.id
  renameForm.name = item.name
  renameForm.version = item.version
  const titles: Record<string, string> = {
    category: '重命名分类',
    unit: '重命名单位',
    tag: '重命名标签',
  }
  renameDialogTitle.value = titles[type] || '重命名'
  renameDialogVisible.value = true
}

async function submitRename() {
  if (!renameForm.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    if (renameForm.type === 'category') {
      await renameCategory(renameForm.id, renameForm.name.trim(), renameForm.version)
      await loadCategories()
    } else if (renameForm.type === 'unit') {
      await renameUnit(renameForm.id, renameForm.name.trim(), renameForm.version)
      await loadUnits()
    } else if (renameForm.type === 'tag') {
      await renameTag(renameForm.id, renameForm.name.trim(), renameForm.version)
      await loadTags()
    }
    renameDialogVisible.value = false
    ElMessage.success('已重命名')
  } catch (e: any) {
    ElMessage.error(e.title || '重命名失败')
  }
}

// --------------- Init ---------------

onMounted(() => {
  loadCategories()
  loadBrands()
  loadUnits()
  loadTags()
})
</script>

<style scoped>
.catalog-settings-page {
  padding: 20px;
}
.page-header {
  margin-bottom: 20px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.create-form {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
}
.tree-node-actions {
  margin-left: 8px;
}
</style>
