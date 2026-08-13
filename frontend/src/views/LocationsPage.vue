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
              <span class="tree-node-label">{{ data.name }}</span>
              <span class="tree-node-actions">
                <el-tooltip content="新增子位置" placement="top" :show-after="300">
                  <el-button size="small" text data-testid="loc-add" @click.stop="openCreate(data.id)">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="重命名" placement="top" :show-after="300">
                  <el-button size="small" text data-testid="loc-rename" @click.stop="openRename(data)">
                    <el-icon><Edit /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip content="移动到其他位置" placement="top" :show-after="300">
                  <el-button size="small" text data-testid="loc-move" @click.stop="openMove(data)">
                    <el-icon><Position /></el-icon>
                  </el-button>
                </el-tooltip>
                <el-tooltip :content="deleteTooltip(data)" placement="top" :show-after="300">
                  <el-button
                    size="small"
                    text
                    type="danger"
                    data-testid="loc-delete"
                    :disabled="!canDelete(data)"
                    @click.stop="deleteNode(data)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <div class="card location-detail-panel">
        <template v-if="selectedLocation">
          <h3 class="detail-name">{{ selectedLocation.name }}</h3>
          <p class="detail-path">{{ ancestorPath }}</p>

          <dl class="detail-grid">
            <dt>子位置</dt>
            <dd>
              {{ selectedLocation.children?.length ? `${selectedLocation.children.length} 个` : '无' }}
            </dd>

            <dt>库存记录</dt>
            <dd>
              <template v-if="locationStockLoading">
                <span class="detail-loading">加载中…</span>
              </template>
              <template v-else>
                <span class="zj-num">{{ locationStockCount }}</span> 条
                <span class="detail-hint">前往库存页查看按单位汇总</span>
              </template>
            </dd>
          </dl>

          <div class="inventory-actions">
            <el-button size="small" type="primary" @click="goToLocationInventory">查看库存</el-button>
            <el-button size="small" @click="goToStocktake">发起盘点</el-button>
          </div>
        </template>

        <div v-else class="detail-empty">
          <span class="zj-dot zj-dot-off"></span>
          <p>从左侧选择一个位置以查看详情</p>
        </div>
      </div>
    </div>

    <!-- Create/Rename Dialog -->
    <el-dialog v-model="nameDialogVisible" :title="nameDialogTitle" width="400px" @opened="focusNameInput">
      <el-form :model="nameForm" label-width="80px" @submit.prevent="submitName">
        <el-form-item label="名称">
          <el-input ref="nameInputRef" v-model="nameForm.name" maxlength="100" />
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
        <p class="move-dialog-intro">将 <strong>{{ movingNode.name }}</strong> 移动到：</p>
        <el-tree
          :data="moveTargetTreeData"
          node-key="id"
          default-expand-all
          :expand-on-click-node="false"
          highlight-current
          @current-change="onTargetChange"
        >
          <template #default="{ data }">
            <span :class="{ 'is-disabled-target': data.disabled }">
              {{ data.name }}
              <span v-if="data.disabled" class="disabled-tag">不可选</span>
            </span>
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
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, type InputInstance } from 'element-plus'
import { Plus, Edit, Position, Delete } from '@element-plus/icons-vue'
import { fetchLocationTree, createLocation, renameLocation, deleteLocation, moveLocation } from '../api/location'
import { fetchStockPositions } from '../api/inventory'
import type { LocationNode } from '../types/location'

const router = useRouter()
const route = useRoute()

// 移动对话框的目标树节点：原节点上附加 disabled 字段，标记是否可选
interface TargetNode extends LocationNode {
  disabled?: boolean
}

const treeData = ref<LocationNode[]>([])
const selectedLocation = ref<LocationNode | null>(null)
const locationStockCount = ref(0)
const locationStockLoading = ref(false)
const moveDialogVisible = ref(false)
const movingNode = ref<LocationNode | null>(null)
const targetParentId = ref<string | null>(null)
const targetSortOrder = ref(0)

const nameDialogVisible = ref(false)
const nameDialogTitle = ref('')
const nameInputRef = ref<InputInstance>()
const nameForm = reactive({
  name: '',
  parentId: null as string | null,
  editingId: null as string | null,
  version: 0,
})

function focusNameInput() {
  nameInputRef.value?.focus()
  if (nameForm.editingId) {
    nameInputRef.value?.select()
  }
}

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

// 为移动对话框构造一棵克隆树：把移动节点自身及其全部子孙标记为 disabled，
// 避免把位置移进自己的子树（成环）。原 treeData 不被改动。
const moveTargetTreeData = computed<TargetNode[]>(() => {
  if (!movingNode.value) return []
  const movingId = movingNode.value.id
  const descendants = collectDescendantIds(movingNode.value)
  return treeData.value.map((root) => markSubtreeDisabled(root, movingId, descendants))
})

function collectDescendantIds(node: LocationNode): Set<string> {
  const ids = new Set<string>()
  const walk = (n: LocationNode) => {
    ids.add(n.id)
    n.children?.forEach(walk)
  }
  walk(node)
  return ids
}

function markSubtreeDisabled(
  node: LocationNode,
  movingId: string,
  descendants: Set<string>,
): TargetNode {
  const disabled = node.id === movingId || descendants.has(node.id)
  return {
    ...node,
    disabled,
    children: node.children?.map((c) => markSubtreeDisabled(c, movingId, descendants)) ?? [],
  }
}

function canDelete(node: LocationNode): boolean {
  // 已引用的位置不能删除，否则会留下孤儿库存引用
  return !node.everReferenced
}

function deleteTooltip(node: LocationNode): string {
  if (node.everReferenced) return '已存在库存引用，无法删除'
  return '删除位置'
}

async function loadTree() {
  const res = await fetchLocationTree()
  treeData.value = res.roots
  // 来自全局搜索的 ?highlight=<locationId>：定位并选中该位置
  const highlight = route.query.highlight as string | undefined
  if (highlight) {
    const node = findNodeById(treeData.value, highlight)
    if (node) await selectNode(node)
  }
}

function findNodeById(nodes: LocationNode[], id: string): LocationNode | null {
  for (const node of nodes) {
    if (node.id === id) return node
    if (node.children?.length) {
      const found = findNodeById(node.children, id)
      if (found) return found
    }
  }
  return null
}

async function selectNode(data: LocationNode) {
  selectedLocation.value = data
  locationStockCount.value = 0
  locationStockLoading.value = true
  try {
    // 只取少量用于核对服务端 total，避免拉整张库存表
    const pos = await fetchStockPositions({ locationId: data.id, pageSize: 200 })
    locationStockCount.value = pos.total
  } catch (e: any) {
    ElMessage.error(e?.message || '库存加载失败')
    locationStockCount.value = 0
  } finally {
    locationStockLoading.value = false
  }
}

function goToLocationInventory() {
  if (!selectedLocation.value) return
  router.push({ name: 'inventory', query: { locationId: selectedLocation.value.id } })
}

function goToStocktake() {
  if (!selectedLocation.value) return
  router.push({ name: 'inventory', query: { action: 'stocktake', locationId: selectedLocation.value.id } })
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

function onTargetChange(data: TargetNode) {
  if (data.disabled) {
    targetParentId.value = null
    return
  }
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
  if (!canDelete(node)) return
  if (node.children?.length) {
    ElMessage.warning(`该位置下有 ${node.children.length} 个子位置，请先删除子位置`)
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除位置"${node.name}"？`, '确认')
  } catch {
    return
  }
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

/* 两面板内边距统一到同一令牌 */
.location-tree-panel,
.location-detail-panel {
  padding: 20px 22px;
}
.location-tree-panel {
  flex: 1;
  min-width: 300px;
}
.location-detail-panel {
  width: 320px;
  min-height: 220px;
}

.detail-name {
  margin: 0 0 6px;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
}
.detail-path {
  margin: 0 0 18px;
  font-size: 13px;
  color: var(--zj-ink-600);
}

/* 键值对网格：label 一列、value 一列，提升扫读性 */
.detail-grid {
  display: grid;
  grid-template-columns: 84px 1fr;
  row-gap: 10px;
  column-gap: 16px;
  margin: 0 0 20px;
}
.detail-grid dt {
  font-size: 12px;
  color: var(--zj-ink-400);
  letter-spacing: 0.04em;
}
.detail-grid dd {
  margin: 0;
  font-size: 13px;
  color: var(--zj-ink-900);
}
.detail-loading {
  font-size: 13px;
  color: var(--zj-ink-400);
}
.detail-hint {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--zj-ink-400);
}

.inventory-actions {
  display: flex;
  gap: 8px;
}

/* 空状态：未选中节点时引导用户 */
.detail-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 180px;
  color: var(--zj-ink-400);
  font-size: 13px;
}
.detail-empty .zj-dot {
  margin: 0 0 12px;
}
.detail-empty p {
  margin: 0;
}

.move-dialog-intro {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--zj-ink-600);
}
.is-disabled-target {
  color: var(--zj-ink-300);
}
.disabled-tag {
  margin-left: 8px;
  font-size: 11px;
  color: var(--zj-ink-300);
}

.tree-node {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  padding-right: 4px;
}
.tree-node-label {
  flex: 1;
  min-width: 0;
}

/* 操作按钮：默认以低透明度常驻（兼顾鼠标可发现性 + 键盘/触屏可达），
   hover / focus-within 时提亮，避免 hover-only 隐藏导致可达性问题。 */
.tree-node-actions {
  display: inline-flex;
  gap: 0;
  margin-left: 8px;
  opacity: 0.35;
  transition: opacity var(--zj-dur-fast) var(--zj-ease-out);
}
.tree-node:hover .tree-node-actions,
.tree-node:focus-within .tree-node-actions {
  opacity: 1;
}
.tree-node-actions .el-button + .el-button {
  margin-left: 2px;
}
</style>