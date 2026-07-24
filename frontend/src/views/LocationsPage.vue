<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">位置管理</h2>
        <p class="page-subtitle">家庭储物空间的树形结构</p>
      </div>
      <el-button type="primary" @click="openCreate()">新增根位置</el-button>
    </div>

    <div class="locations-workspace">
      <div class="card location-tree-panel">
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
                <el-button size="small" text data-testid="loc-add" @click.stop="openCreate(data.id)">
                  <el-icon><Plus /></el-icon>
                </el-button>
                <el-button size="small" text data-testid="loc-rename" @click.stop="openRename(data)">
                  <el-icon><Edit /></el-icon>
                </el-button>
                <el-button size="small" text data-testid="loc-move" @click.stop="openMove(data)">
                  <el-icon><TopRight /></el-icon>
                </el-button>
                <el-button v-if="!data.everReferenced" size="small" text type="danger" data-testid="loc-delete" @click.stop="deleteNode(data)">
                  <el-icon><Close /></el-icon>
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="card location-detail-panel" v-if="selectedLocation">
        <h3 class="detail-name">{{ selectedLocation.name }}</h3>
        <p class="detail-path"><strong>路径：</strong>{{ ancestorPath }}</p>
        <p class="detail-meta">ID: <span class="zj-mono">{{ selectedLocation.id }}</span></p>
        <p class="detail-meta">版本: <span class="zj-mono">{{ selectedLocation.version }}</span></p>
        <p class="detail-meta">已引用: {{ selectedLocation.everReferenced ? '是' : '否' }}</p>
        <template v-if="selectedLocation.children?.length">
          <el-divider />
          <p class="detail-meta"><strong>子位置：</strong></p>
          <ul class="child-list">
            <li v-for="child in selectedLocation.children" :key="child.id">{{ child.name }}</li>
          </ul>
        </template>
        <el-divider />
        <div class="empty-box">库存将在阶段四启用</div>
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
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, TopRight, Close } from '@element-plus/icons-vue'
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

// Build a flat map for ancestor path lookup
function buildNodeMap(nodes: LocationNode[], parentPath: string[] = []): Map<string, string[]> {
  const map = new Map<string, string[]>()
  for (const node of nodes) {
    const path = [...parentPath, node.name]
    map.set(node.id, path)
    if (node.children?.length) {
      for (const [id, p] of buildNodeMap(node.children, path)) {
        map.set(id, p)
      }
    }
  }
  return map
}

const ancestorPath = computed(() => {
  if (!selectedLocation.value) return ''
  const nodeMap = buildNodeMap(treeData.value)
  const path = nodeMap.get(selectedLocation.value.id)
  return path ? path.join(' / ') : selectedLocation.value.name
})

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
      await createLocation({ name: nameForm.name, parentId: nameForm.parentId ?? null, sortOrder: 0 })
    }
    nameDialogVisible.value = false
    await loadTree()
    ElMessage.success(nameForm.editingId ? '已重命名' : '已创建')
  } catch (e: any) {
    ElMessage.error(e.message || '操作失败')
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
    ElMessage.error(e.message || '移动失败')
  }
}

async function deleteNode(node: LocationNode) {
  if (node.children?.length) {
    ElMessage.warning(`该位置下有 ${node.children.length} 个子位置，请先删除子位置`)
    return
  }
  await ElMessageBox.confirm(`确定删除位置"${node.name}"？`, '确认')
  try {
    await deleteLocation(node.id, node.version)
    await loadTree()
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(loadTree)
</script>

<style scoped>
.locations-workspace {
  display: flex;
  gap: 20px;
  align-items: flex-start;
}
.location-tree-panel {
  flex: 1;
  min-width: 300px;
  padding: 12px 8px;
}
.location-detail-panel {
  width: 320px;
  padding: 22px 22px 26px;
}
.detail-name {
  margin: 0 0 6px;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
}
.detail-path {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--zj-ink-600);
}
.detail-meta {
  margin: 0 0 8px;
  font-size: 13px;
}
.empty-box {
  border: 1px dashed var(--zj-line-strong);
  border-radius: var(--zj-radius-md);
  padding: 22px 16px;
  text-align: center;
  font-size: 13px;
  color: var(--zj-ink-400);
}
.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 4px;
}
.tree-node-actions {
  display: inline-flex;
  gap: 0;
  margin-left: 8px;
  opacity: 0;
  transition: opacity var(--zj-dur-fast) var(--zj-ease-out);
}
.tree-node:hover .tree-node-actions {
  opacity: 1;
}
.tree-node-actions .el-button + .el-button {
  margin-left: 2px;
}
.child-list {
  margin: 0;
  padding-left: 20px;
}
.child-list li {
  line-height: 1.8;
}
</style>
