<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">物品资料</h2>
        <p class="page-subtitle">维护家庭物品的主数据</p>
      </div>
      <el-button type="primary" @click="openCreate">新建物品</el-button>
    </div>

    <div class="items-filters">
      <el-input v-model="filters.q" placeholder="搜索物品" clearable class="filter-search" @input="debouncedFetch" />
      <el-select v-model="filters.managementType" placeholder="管理类型" clearable @change="fetchItems">
        <el-option label="消耗品" value="CONSUMABLE" />
        <el-option label="耐用品" value="DURABLE" />
      </el-select>
      <el-select v-model="filters.status" placeholder="状态" clearable @change="fetchItems">
        <el-option label="活跃" value="ACTIVE" />
        <el-option label="归档" value="ARCHIVED" />
      </el-select>
      <el-tree-select
        v-model="filters.categoryId"
        :data="categoryTree"
        :props="({ label: 'name', value: 'id', children: 'children' } as any)"
        placeholder="分类"
        clearable
        check-strictly
        @change="fetchItems"
      />
      <el-select v-model="filters.brandId" placeholder="品牌" clearable @change="fetchItems">
        <el-option v-for="b in brands" :key="b.id" :label="b.name" :value="b.id" />
      </el-select>
      <el-select v-model="filters.tagId" placeholder="标签" clearable @change="fetchItems">
        <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id" />
      </el-select>
      <el-select v-model="filters.sort" placeholder="排序" clearable @change="fetchItems">
        <el-option label="名称↑" value="name,asc" />
        <el-option label="名称↓" value="name,desc" />
        <el-option label="创建时间↑" value="createdAt,asc" />
        <el-option label="创建时间↓" value="createdAt,desc" />
        <el-option label="更新时间↑" value="updatedAt,asc" />
        <el-option label="更新时间↓" value="updatedAt,desc" />
      </el-select>
    </div>

    <el-table :data="items" v-loading="loading" class="items-table table-clickable" @row-click="(row: any) => openDetail(row as CatalogItem)">
      <el-table-column label="封面" width="60" class-name="col-cover">
        <template #default="{ row }">
          <img v-if="row.coverUrl" :src="row.coverUrl" class="cover-thumb" alt="封面" />
          <span v-else class="cover-placeholder">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="managementType" label="类型" width="90">
        <template #default="{ row }">
          <span :class="row.managementType === 'CONSUMABLE' ? 'zj-dot zj-dot-warn' : 'zj-dot zj-dot-pine'"></span>{{ row.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}
        </template>
      </el-table-column>
      <el-table-column label="分类" width="110" class-name="col-secondary">
        <template #default="{ row }"><span class="cell-secondary">{{ categoryMap[(row.categoryId as string)] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="品牌" width="90" class-name="col-secondary">
        <template #default="{ row }"><span class="cell-secondary">{{ brandMap[(row.brandId as string)] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="单位" width="70" class-name="col-secondary">
        <template #default="{ row }"><span class="cell-secondary">{{ unitMap[row.unitId] || '—' }}</span></template>
      </el-table-column>
      <el-table-column label="标签" width="125" class-name="col-secondary">
        <template #default="{ row }">
          <template v-if="row.tagIds?.length">
            <el-tag v-for="tid in row.tagIds.slice(0, 2)" :key="tid" size="small" effect="plain" class="tag-item">
              {{ tagMap[tid] || tid }}
            </el-tag>
            <el-tag v-if="row.tagIds.length > 2" size="small" type="info" effect="plain">+{{ row.tagIds.length - 2 }}</el-tag>
          </template>
          <span v-else class="cell-secondary">—</span>
        </template>
      </el-table-column>
      <el-table-column label="低库存阈值" width="100" class-name="col-secondary">
        <template #default="{ row }">
          <span class="cell-secondary zj-num">{{ row.managementType === 'CONSUMABLE' && row.lowStockThreshold ? row.lowStockThreshold : '—' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <span :class="row.status === 'ACTIVE' ? 'zj-dot zj-dot-pine' : 'zj-dot zj-dot-off'"></span>{{ row.status === 'ACTIVE' ? '活跃' : '归档' }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="165">
        <template #default="{ row }"><span class="cell-secondary zj-num">{{ formatDate(row.updatedAt) }}</span></template>
      </el-table-column>
      <el-table-column label="操作" width="70" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text @click.stop="openEdit(row as CatalogItem)">编辑</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <div class="table-empty">
          <p class="table-empty-title">还没有物品</p>
          <p class="table-empty-hint">从新建第一件物品开始，让每一件物品都有迹可循。</p>
        </div>
      </template>
    </el-table>

    <el-pagination
      v-model:current-page="pagination.page"
      v-model:page-size="pagination.pageSize"
      :total="pagination.total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="fetchItems"
      @size-change="fetchItems"
    />

    <el-drawer v-model="detailVisible" title="物品详情" size="520px">
      <div v-if="selectedItem" class="item-detail">
        <div class="item-head">
          <img v-if="selectedItem.coverUrl" :src="selectedItem.coverUrl" class="detail-cover" alt="封面" />
          <div v-else class="detail-cover-placeholder" aria-hidden="true">—</div>
          <div class="item-head-main">
            <h3 class="detail-name">{{ selectedItem.name }}</h3>
            <div class="item-badges">
              <span :class="selectedItem.managementType === 'CONSUMABLE' ? 'zj-badge zj-badge-plain' : 'zj-badge zj-badge-pine'">
                {{ selectedItem.managementType === 'CONSUMABLE' ? '消耗品' : '耐用品' }}
              </span>
              <span :class="selectedItem.status === 'ACTIVE' ? 'zj-badge zj-badge-pine' : 'zj-badge zj-badge-plain'">
                {{ selectedItem.status === 'ACTIVE' ? '活跃' : '归档' }}
              </span>
            </div>
          </div>
        </div>

        <dl class="detail-grid">
          <div class="detail-field">
            <dt>分类</dt>
            <dd>{{ categoryMap[(selectedItem.categoryId as string)] || '—' }}</dd>
          </div>
          <div class="detail-field">
            <dt>品牌</dt>
            <dd>{{ brandMap[(selectedItem.brandId as string)] || '—' }}</dd>
          </div>
          <div class="detail-field">
            <dt>单位</dt>
            <dd>{{ unitMap[selectedItem.unitId] || '—' }}<span v-if="unitDetailMap[selectedItem.unitId]" class="detail-field-sub">精度 {{ unitDetailMap[selectedItem.unitId] }} 位小数</span></dd>
          </div>
          <div class="detail-field">
            <dt>标签</dt>
            <dd>
              <template v-if="selectedItem.tagIds?.length">
                <el-tag v-for="tid in selectedItem.tagIds" :key="tid" size="small" effect="plain" class="tag-item">{{ tagMap[tid] || tid }}</el-tag>
              </template>
              <template v-else>—</template>
            </dd>
          </div>
          <div v-if="selectedItem.expiryReminderMode" class="detail-field">
            <dt>临期提醒</dt>
            <dd>{{ reminderModeLabel(selectedItem.expiryReminderMode) }}<span v-if="selectedItem.expiryReminderDays?.length" class="detail-field-sub">{{ selectedItem.expiryReminderDays.join('、') }} 天</span></dd>
          </div>
          <div v-if="selectedItem.lowStockMode" class="detail-field">
            <dt>低库存</dt>
            <dd>{{ reminderModeLabel(selectedItem.lowStockMode) }}<span v-if="selectedItem.lowStockThreshold" class="detail-field-sub">阈值 {{ selectedItem.lowStockThreshold }}</span></dd>
          </div>
          <div v-if="selectedItem.memo" class="detail-field detail-field-wide">
            <dt>备注</dt>
            <dd>{{ selectedItem.memo }}</dd>
          </div>
          <div class="detail-field">
            <dt>创建时间</dt>
            <dd class="zj-num">{{ formatDate(selectedItem.createdAt) }}</dd>
          </div>
        </dl>

        <div class="inventory-summary">
          <div class="summary-stat">
            <span class="summary-num zj-num">{{ inventoryTotal }}</span>
            <span class="summary-label">库存总量</span>
          </div>
          <div class="summary-stat">
            <span class="summary-num zj-num">{{ lotCount }}</span>
            <span class="summary-label">批次数</span>
          </div>
          <el-button type="primary" size="small" @click="goToInbound">入库</el-button>
        </div>

        <div class="detail-actions">
          <el-button v-if="selectedItem.status === 'ACTIVE'" @click="archiveItem(selectedItem)">归档</el-button>
          <el-button v-if="selectedItem.status === 'ARCHIVED'" @click="restoreItem(selectedItem)">恢复</el-button>
        </div>

        <el-divider />
        <div class="attachments-section">
          <div class="section-header">
            <h4 class="section-title">附件</h4>
            <el-button size="small" type="primary" @click="triggerAttachmentUpload">上传</el-button>
            <input
              ref="attachmentInput"
              class="file-input"
              type="file"
              @change="onAttachmentChosen"
            />
          </div>
          <el-table
            v-loading="attachmentsLoading"
            :data="itemAttachments"
            size="small"
            class="att-table"
            empty-text="还没有附件"
          >
            <el-table-column label="文件" min-width="150">
              <template #default="{ row }">
                <div class="att-file">
                  <div class="att-tile">
                    <img
                      v-if="attIsImage(row.mediaType) && !failedAttImages.has(row.id)"
                      :src="row.url"
                      :alt="row.name"
                      class="att-thumb"
                      @error="onAttImgError(row.id)"
                    />
                    <span v-else class="att-ext">{{ attFileExt(row as Attachment) }}</span>
                  </div>
                  <div class="att-main">
                    <div class="att-name">{{ row.name }}</div>
                    <div class="att-type">{{ mediaTypeLabel(row.mediaType) }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="72">
              <template #default="{ row }"><span class="cell-secondary zj-num">{{ formatBytes(row.byteSize) }}</span></template>
            </el-table-column>
            <el-table-column label="" width="200" align="right">
              <template #default="{ row }">
                <div class="att-actions">
                  <el-button
                    v-if="canDesignateCover(row as Attachment)"
                    size="small"
                    text
                    type="primary"
                    data-testid="designate-cover"
                    @click="designateCover(row as Attachment)"
                  >
                    {{ isCurrentCover(row as Attachment) ? '当前封面' : '设为封面' }}
                  </el-button>
                  <el-button size="small" text @click="moveAttachmentToHousehold(row as Attachment)">移走</el-button>
                  <el-dropdown trigger="click" @command="(c: string) => onAttCommand(c, row as Attachment)">
                    <el-button size="small" text type="primary">
                      更多<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                    </el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="rename">改名</el-dropdown-item>
                        <el-dropdown-item command="download">下载</el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                  <el-button size="small" text type="danger" @click="deleteAttachment(row as Attachment)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-drawer>

    <ItemFormDrawer
      v-model="formDrawerVisible"
      :item="editingItem"
      @saved="onFormSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import {
  fetchItems as apiFetchItems,
  fetchItem as apiFetchItem,
  archiveItem as apiArchiveItem,
  restoreItem as apiRestoreItem,
  fetchCategories, fetchBrands, fetchUnits, fetchTags
} from '../api/catalog'
import { fetchStockPositions, fetchLots } from '../api/inventory'
import {
  listItemAttachments,
  uploadItemAttachment,
  renameAttachment as apiRenameAttachment,
  deleteAttachment as apiDeleteAttachment,
  designateItemCover as apiDesignateItemCover,
  remountAttachmentToHousehold as apiRemountAttachmentToHousehold,
  COVER_IMAGE_TYPES,
  type Attachment
} from '../api/file'
import { ApiError } from '../api/http'
import { formatBytes } from '../utils/format'
import type { CatalogItem, Category, Brand, Unit, Tag } from '../types/catalog'
import ItemFormDrawer from './ItemFormDrawer.vue'

const router = useRouter()
const route = useRoute()

const items = ref<CatalogItem[]>([])
const loading = ref(false)
const detailVisible = ref(false)
const selectedItem = ref<CatalogItem | null>(null)
const formDrawerVisible = ref(false)
const editingItem = ref<CatalogItem | null>(null)

// Inventory summary for detail drawer
const inventoryTotal = ref(0)
const lotCount = ref(0)

// Dictionary data
const categories = ref<Category[]>([])
const brands = ref<Brand[]>([])
const units = ref<Unit[]>([])
const tags = ref<Tag[]>([])

const filters = reactive({ q: '', managementType: '', status: 'ACTIVE', categoryId: '', brandId: '', tagId: '', sort: '' })
const pagination = reactive({ page: 1, pageSize: 20, total: 0 })

// Lookup maps
const categoryMap = computed(() => {
  const map: Record<string, string> = {}
  function walk(nodes: (Category & { children?: Category[] })[]) {
    for (const n of nodes) {
      map[n.id] = n.name
      if (n.children) walk(n.children)
    }
  }
  walk(categories.value)
  return map
})

const brandMap = computed(() => {
  const map: Record<string, string> = {}
  for (const b of brands.value) map[b.id] = b.name
  return map
})

const unitMap = computed(() => {
  const map: Record<string, string> = {}
  for (const u of units.value) map[u.id] = u.name
  return map
})

const unitDetailMap = computed(() => {
  const map: Record<string, number> = {}
  for (const u of units.value) map[u.id] = u.decimalScale
  return map
})

const tagMap = computed(() => {
  const map: Record<string, string> = {}
  for (const t of tags.value) map[t.id] = t.name
  return map
})

const categoryTree = computed(() => buildCategoryTree(categories.value))

function buildCategoryTree(list: Category[]): Category[] {
  const map = new Map<string, Category & { children: Category[] }>()
  for (const cat of list) {
    map.set(cat.id, { ...cat, children: [] })
  }
  const roots: (Category & { children: Category[] })[] = []
  for (const node of map.values()) {
    if (node.parentId && map.has(node.parentId)) {
      map.get(node.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
}

let debounceTimer: ReturnType<typeof setTimeout>

function debouncedFetch() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(fetchItems, 300)
}

async function fetchItems() {
  loading.value = true
  try {
    const res = await apiFetchItems({
      q: filters.q || undefined,
      managementType: filters.managementType || undefined,
      status: filters.status || undefined,
      categoryId: filters.categoryId || undefined,
      brandId: filters.brandId || undefined,
      tagId: filters.tagId || undefined,
      sort: filters.sort || undefined,
      page: pagination.page,
      pageSize: pagination.pageSize,
    })
    items.value = res.items
    pagination.total = res.total
  } catch (e: any) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadDictionaries() {
  const [c, b, u, t] = await Promise.all([
    fetchCategories().catch(() => []),
    fetchBrands().catch(() => []),
    fetchUnits().catch(() => []),
    fetchTags().catch(() => []),
  ])
  categories.value = c
  brands.value = b
  units.value = u
  tags.value = t
}

function openCreate() {
  editingItem.value = null
  formDrawerVisible.value = true
}

function openEdit(item: CatalogItem) {
  editingItem.value = item
  formDrawerVisible.value = true
}

function onFormSaved() {
  formDrawerVisible.value = false
  fetchItems()
}

async function openDetail(item: CatalogItem) {
  selectedItem.value = item
  detailVisible.value = true
  inventoryTotal.value = 0
  lotCount.value = 0
  itemAttachments.value = []
  try {
    const [pos, lots] = await Promise.all([
      fetchStockPositions({ itemId: item.id, pageSize: 10000 }),
      fetchLots({ itemId: item.id, pageSize: 1 }),
    ])
    inventoryTotal.value = pos.items.reduce((sum, p) => sum + Number(p.quantity), 0)
    lotCount.value = lots.total
  } catch {
    // silently ignore — inventory module may not be available yet
  }
  loadAttachments()
}

// ==================== 附件 ====================

const itemAttachments = ref<Attachment[]>([])
const attachmentsLoading = ref(false)
const attachmentInput = ref<HTMLInputElement | null>(null)
/** 缩略图加载失败的附件 id：回退为扩展名瓦片 */
const failedAttImages = ref<Set<string>>(new Set())

async function loadAttachments() {
  if (!selectedItem.value) return
  attachmentsLoading.value = true
  try {
    itemAttachments.value = await listItemAttachments(selectedItem.value.id)
  } catch {
    // 附件模块异常时静默，详情其余部分照常
  } finally {
    attachmentsLoading.value = false
  }
}

/**
 * 附件操作清除了当前封面时重新拉取物品：服务器会清除封面指定并递增版本号，
 * 本地只清 coverFileId/coverUrl 会留下过期版本，后续封面/归档操作携带旧版本
 * 触发 CATALOG_VERSION_CONFLICT（409）。刷新同时同步抽屉与列表行。
 */
async function refreshSelectedItem() {
  if (!selectedItem.value) return
  try {
    const fresh = await apiFetchItem(selectedItem.value.id)
    selectedItem.value = fresh
    const idx = items.value.findIndex((it) => it.id === fresh.id)
    if (idx >= 0) items.value[idx] = fresh
  } catch {
    // 静默：附件操作已成功，仅封面/版本展示可能滞后，列表刷新兜底
  }
}

function triggerAttachmentUpload() {
  attachmentInput.value?.click()
}

async function onAttachmentChosen(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file || !selectedItem.value) return
  try {
    await uploadItemAttachment(selectedItem.value.id, file)
    ElMessage.success('已上传')
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '上传失败')
  }
}

function canDesignateCover(row: Attachment): boolean {
  return COVER_IMAGE_TYPES.includes(row.mediaType)
}

function isCurrentCover(row: Attachment): boolean {
  return selectedItem.value?.coverFileId === row.id
}

// ==================== 附件：文件瓦片与类型标签 ====================

const MEDIA_LABELS: Record<string, string> = {
  "image/jpeg": "JPEG 图片",
  "image/png": "PNG 图片",
  "image/webp": "WebP 图片",
  "image/heic": "HEIC 图片",
  "image/heif": "HEIF 图片",
  "application/pdf": "PDF 文档",
  "text/markdown": "Markdown",
  "text/plain": "TXT 文本",
  "application/msword": "Word (.doc)",
  "application/vnd.openxmlformats-officedocument.wordprocessingml.document": "Word (.docx)",
  "application/vnd.ms-excel": "Excel (.xls)",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": "Excel (.xlsx)",
  "application/vnd.ms-powerpoint": "PowerPoint (.ppt)",
  "application/vnd.openxmlformats-officedocument.presentationml.presentation": "PowerPoint (.pptx)"
}

function mediaTypeLabel(mediaType: string): string {
  return MEDIA_LABELS[mediaType] ?? mediaType
}

function attIsImage(mediaType: string): boolean {
  return mediaType.startsWith("image/")
}

/** 附件瓦片上的扩展名标识：图片取类型缩写，其余取文件名末段 */
function attFileExt(row: Attachment): string {
  if (attIsImage(row.mediaType)) {
    const subtype = row.mediaType.split("/")[1] ?? ""
    return subtype.slice(0, 4).toUpperCase()
  }
  const dot = row.name.lastIndexOf(".")
  if (dot > 0 && dot < row.name.length - 1) {
    return row.name.slice(dot + 1).slice(0, 4).toUpperCase()
  }
  return "FILE"
}

function onAttImgError(id: string) {
  failedAttImages.value = new Set(failedAttImages.value).add(id)
}

function onAttCommand(command: string, row: Attachment) {
  if (command === "rename") {
    renameAttachment(row)
  } else if (command === "download") {
    downloadAttachment(row)
  }
}

/** 把合格图片附件指定为封面；已有封面时先问旧封面处置。 */
async function designateCover(row: Attachment) {
  if (!selectedItem.value || isCurrentCover(row)) return
  let oldCoverAction: 'KEEP' | 'RECYCLE' | undefined
  if (selectedItem.value.coverFileId) {
    try {
      await ElMessageBox.confirm(
        '指定此图为封面后，旧封面留作普通附件，还是送进回收站？',
        '更换封面',
        {
          confirmButtonText: '留作附件',
          cancelButtonText: '送回收站',
          distinguishCancelAndClose: true,
          type: 'info',
        },
      )
      oldCoverAction = 'KEEP'
    } catch (reason) {
      if (reason !== 'cancel') return // close 或其它：不更换
      oldCoverAction = 'RECYCLE'
    }
  }
  try {
    const result = await apiDesignateItemCover(selectedItem.value.id, row.id, selectedItem.value.version, oldCoverAction)
    selectedItem.value.coverFileId = result.id
    selectedItem.value.coverUrl = result.url
    selectedItem.value.version = result.version
    ElMessage.success('已设为封面')
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '设置封面失败')
  }
}

async function renameAttachment(row: Attachment) {
  try {
    const picked = await ElMessageBox.prompt('新名字', '改名', {
      inputValue: row.name,
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /\S+/,
      inputErrorMessage: '名字不能为空',
    })
    await apiRenameAttachment(row.id, picked.value.trim())
    ElMessage.success('已改名')
    await loadAttachments()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof ApiError ? error.message : '改名失败')
  }
}

function downloadAttachment(row: Attachment) {
  window.open(row.url, '_blank')
}

async function moveAttachmentToHousehold(row: Attachment) {
  try {
    await apiRemountAttachmentToHousehold(row.id)
    if (selectedItem.value?.coverFileId === row.id) {
      // 改挂的是当前封面：服务器已清除封面指定并递增版本，重新拉取以刷新版本与封面
      await refreshSelectedItem()
    }
    ElMessage.success('已移到家庭')
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '改挂失败')
  }
}

async function deleteAttachment(row: Attachment) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？删除后进入回收站，保留期内可以恢复。`, '删除附件', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await apiDeleteAttachment(row.id)
    if (selectedItem.value?.coverFileId === row.id) {
      // 删除的是当前封面：服务器已清除封面指定并递增版本，重新拉取以刷新版本与封面
      await refreshSelectedItem()
    }
    ElMessage.success('已删除，可在回收站恢复')
    await loadAttachments()
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : '删除失败')
  }
}


function goToInbound() {
  if (!selectedItem.value) return
  router.push({ name: 'inventory', query: { action: 'inbound', itemId: selectedItem.value.id } })
}

async function archiveItem(item: CatalogItem) {
  await ElMessageBox.confirm('确定归档此物品？', '确认')
  await apiArchiveItem(item.id, item.version)
  ElMessage.success('已归档')
  fetchItems()
}

async function restoreItem(item: CatalogItem) {
  await apiRestoreItem(item.id, item.version)
  ElMessage.success('已恢复')
  fetchItems()
}

/** 提醒模式枚举的中文化映射（详情抽屉展示用） */
function reminderModeLabel(mode: string): string {
  return { INHERIT: '继承全局设置', DISABLED: '禁用', CUSTOM: '自定义' }[mode] ?? mode
}

function formatDate(iso: string): string {
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  loadDictionaries()
  fetchItems()
  // 来自全局搜索的 ?highlight=<itemId>：定位并打开物品详情
  const highlight = route.query.highlight as string | undefined
  if (highlight) {
    openHighlightedItem(highlight)
  }
})

async function openHighlightedItem(itemId: string) {
  try {
    const item = await apiFetchItem(itemId)
    openDetail(item)
  } catch {
    // 物品不存在或已删除时静默忽略，保持列表页正常展示
  }
}
</script>

<style scoped>
.items-filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  padding: 10px 12px;
  flex-wrap: wrap;
  background: var(--zj-surface-sunken);
  border-radius: var(--zj-radius-md);
}
.filter-search {
  width: 200px;
}
.items-filters .el-select {
  width: 130px;
}
.items-filters .el-tree-select {
  width: 160px;
}
.items-table {
  border-radius: var(--zj-radius-md);
  overflow: hidden;
  box-shadow: var(--zj-shadow-sm);
}
.cell-secondary {
  color: var(--zj-ink-600);
}
.table-empty {
  padding: 40px 0 44px;
}
.table-empty-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}
.table-empty-hint {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--zj-ink-400);
}
.detail-name {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.35;
}

/* ---------- 物品详情抽屉 ---------- */
.item-detail {
  display: flex;
  flex-direction: column;
}

/* 头部：64px 封面 + 衬线名称 + 徽章 */
.item-head {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.detail-cover {
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  object-fit: cover;
  border-radius: var(--zj-radius-md);
  background: var(--zj-surface-sunken);
}

.detail-cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64px;
  height: 64px;
  flex-shrink: 0;
  border-radius: var(--zj-radius-md);
  background: var(--zj-surface-sunken);
  color: var(--zj-ink-300);
  font-size: 20px;
}

.item-head-main {
  min-width: 0;
}

.item-badges {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.item-badges .zj-badge {
  padding: 0 10px;
  line-height: 18px;
  font-size: 11px;
}

/* 属性 label/value 双列网格 */
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  column-gap: 24px;
  row-gap: 18px;
  margin: 0 0 24px;
}

.detail-field {
  min-width: 0;
}

.detail-field-wide {
  grid-column: 1 / -1;
}

.detail-field dt {
  margin: 0 0 4px;
  font-size: 12px;
  color: var(--zj-ink-400);
  letter-spacing: 0.04em;
}

.detail-field dd {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--zj-ink-900);
  word-break: break-word;
}

.detail-field-sub {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: var(--zj-ink-400);
}

/* 库存汇总：双统计 + 入库 */
.inventory-summary {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 16px 0;
  border-top: 1px solid var(--zj-line);
  border-bottom: 1px solid var(--zj-line);
}

.summary-stat {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.summary-num {
  font-size: 22px;
  font-weight: 600;
  color: var(--zj-pine-600);
  line-height: 1.2;
}

.summary-label {
  font-size: 12px;
  color: var(--zj-ink-400);
}

.inventory-summary .el-button {
  margin-left: auto;
}

.detail-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
}

.detail-actions .el-button + .el-button {
  margin-left: 0;
}
.attachments-section {
  margin-top: 8px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.section-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 15px;
  font-weight: 600;
}
.file-input {
  display: none;
}
.cover-thumb {
  width: 36px;
  height: 36px;
  object-fit: cover;
  border-radius: var(--zj-radius-sm);
}
.cover-placeholder {
  color: var(--zj-ink-300);
}
.tag-item {
  margin-right: 4px;
  margin-bottom: 2px;
}

/* ---------- 附件表格 ---------- */
.att-table {
  border-radius: var(--zj-radius-sm);
  overflow: hidden;
}

/* 文件单元格：小瓦片 + 名称/类型双行 */
.att-file {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.att-tile {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  border-radius: var(--zj-radius-sm);
  background: var(--zj-surface-sunken);
  overflow: hidden;
}

.att-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.att-ext {
  font-family: var(--zj-mono);
  font-size: 9px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--zj-ink-600);
}

.att-main {
  min-width: 0;
}

.att-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
  color: var(--zj-ink-900);
}

.att-type {
  margin-top: 1px;
  font-size: 11px;
  color: var(--zj-ink-400);
}

.att-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 2px;
  white-space: nowrap;
}

.att-actions .el-button {
  margin-left: 0;
}

.att-actions :deep(.el-button.is-text) {
  padding-left: 6px;
  padding-right: 6px;
}

.att-actions :deep(.el-icon--right) {
  margin-left: 2px;
  font-size: 12px;
}

.att-actions :deep(.el-dropdown) {
  display: inline-flex;
}

@media (max-width: 1024px) {
  :deep(.col-cover),
  :deep(.col-secondary) {
    display: none;
  }
}
</style>
