<template>
  <div class="locations-page">
    <div class="locations-header">
      <h2>位置管理</h2>
      <el-button type="primary" @click="openCreate()">新增根位置</el-button>
    </div>

    <div class="locations-workspace">
      <div class="location-tree-panel">
        <el-tree
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          @node-click="selectNode"
        >
          <template #default="{ node, data }">
            <div class="tree-node">
              <span>{{ data.name }}</span>
              <span class="tree-node-actions">
                <el-button size="small" text @click.stop="openCreate(data.id)">+</el-button>
                <el-button size="small" text @click.stop="openRename(data)">✏</el-button>
                <el-button size="small" text @click.stop="openMove(data)">↗</el-button>
                <el-button v-if="!data.everReferenced" size="small" text type="danger" @click.stop="deleteNode(data)">×</el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="location-detail-panel" v-if="selectedLocation">
        <h3>{{ selectedLocation.name }}</h3>
        <p>ID: {{ selectedLocation.id }}</p>
        <p>版本: {{ selectedLocation.version }}</p>
        <p>已引用: {{ selectedLocation.everReferenced ? '是' : '否' }}</p>
        <el-divider />
        <p class="placeholder-text">库存将在阶段四启用</p>
      </div>
    </div>

    <!-- Create/Rename Dialog -->
    <el-dialog v-model="nameDialogVisible" :title="nameDialogTitle" width="400px">
      <el-form :model="nameForm" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="nameForm.name" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitName">确定</el-button>
      </template>
    </el-dialog>

    <!-- Move Dialog -->
    <el-dialog v-model="moveDialogVisible" title="移动位置" width="500px">
      <div v-if="movingNode">
        <p>移动 <strong>{{ movingNode.name }}</strong> 到：</p>
        <el-tree
          :data="treeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          highlight-current
          @current-change="onTargetChange"
        >
          <template #default="{ data }">
            <span>{{ data.name }}</span>
          </template>
        </el-tree>
        <el-form label-width="100px" style="margin-top: 16px">
          <el-form-item label="目标排序">
            <el-input-number v-model="targetSortOrder" :min="0" />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitMove" :disabled="!targetParentId">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fetchLocationTree, createLocation, renameLocation, deleteLocation, moveLocation } from '../api/location'
import type { LocationNode } from '../types/location'

const treeData = ref<LocationNode[]>([])
const selectedLocation = ref<LocationNode | null>(null)
const moveDialogVisible = ref(false)
const movingNode = ref<LocationNode | null>(null)
const targetParentId = ref<string | null>(null)
const targetSortOrder = ref(0)

const nameDialogVisible = ref(false)
const nameDialogTitle = ref('')
const nameForm = reactive({ name: '', parentId: null as string | null, editingId: null as string | null, version: 0 })

async function loadTree() {
  const res = await fetchLocationTree()
  treeData.value = res.roots
}

function selectNode(data: LocationNode) {
  selectedLocation.value = data
}

function openCreate(parentId: string | null = null) {
  nameDialogTitle.value = parentId ? '新增子位置' : '新增根位置'
  nameForm.name = ''
  nameForm.parentId = parentId
  nameForm.editingId = null
  nameForm.version = 0
  nameDialogVisible.value = true
}

function openRename(node: LocationNode) {
  nameDialogTitle.value = '重命名位置'
  nameForm.name = node.name
  nameForm.editingId = node.id
  nameForm.version = node.version
  nameDialogVisible.value = true
}

async function submitName() {
  if (!nameForm.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    if (nameForm.editingId) {
      await renameLocation(nameForm.editingId, { name: nameForm.name, version: nameForm.version })
    } else {
      await createLocation({ name: nameForm.name, parentId: nameForm.parentId || undefined })
    }
    nameDialogVisible.value = false
    await loadTree()
    ElMessage.success(nameForm.editingId ? '已重命名' : '已创建')
  } catch (e: any) {
    ElMessage.error(e.title || '操作失败')
  }
}

function openMove(node: LocationNode) {
  movingNode.value = node
  targetParentId.value = null
  targetSortOrder.value = 0
  moveDialogVisible.value = true
}

function onTargetChange(data: LocationNode) {
  targetParentId.value = data.id
}

async function submitMove() {
  if (!movingNode.value || !targetParentId.value) return
  try {
    await moveLocation(movingNode.value.id, {
      parentId: targetParentId.value,
      sortOrder: targetSortOrder.value,
      version: movingNode.value.version,
    })
    moveDialogVisible.value = false
    await loadTree()
    ElMessage.success('已移动')
  } catch (e: any) {
    ElMessage.error(e.title || '移动失败')
  }
}

async function deleteNode(node: LocationNode) {
  await ElMessageBox.confirm(`确定删除位置"${node.name}"？`, '确认')
  try {
    await deleteLocation(node.id, node.version)
    await loadTree()
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e.title || '删除失败')
  }
}

onMounted(loadTree)
</script>

<style scoped>
.locations-page {
  padding: 20px;
}
.locations-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.locations-workspace {
  display: flex;
  gap: 20px;
}
.location-tree-panel {
  flex: 1;
  min-width: 300px;
}
.location-detail-panel {
  width: 300px;
  padding: 16px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
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
.placeholder-text {
  color: var(--el-text-color-secondary);
  font-style: italic;
}
</style>
