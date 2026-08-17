<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">附件</h2>
        <p class="page-subtitle">保管家庭、物品与批次上的文档和图片</p>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="view" @change="onViewChange">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="recycled">回收站</el-radio-button>
        </el-radio-group>
        <el-button type="primary" :disabled="view === 'recycled'" data-testid="attachment-upload" @click="triggerUpload">
          <el-icon class="el-icon--left"><Upload /></el-icon>上传
        </el-button>
        <input ref="fileInput" class="file-input" type="file" @change="onFileChosen" />
      </div>
    </div>

    <div class="filters">
      <el-select
        v-model="filterMountType"
        placeholder="挂载点"
        clearable
        style="width: 160px"
        data-testid="attachment-mount-filter"
        @change="onFilterChange"
      >
        <el-option label="家庭" value="HOUSEHOLD" />
        <el-option label="物品" value="ITEM" />
        <el-option label="批次" value="LOT" />
      </el-select>
      <el-input
        v-model="filterQ"
        placeholder="按名字搜索"
        clearable
        style="width: 240px"
        @input="onSearchInput"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <span v-if="!loading && pagination.total > 0" class="filter-count">
        共 <span class="zj-num">{{ pagination.total }}</span> 份
      </span>
    </div>

    <div class="card file-card">
      <!-- 加载骨架：贴合行结构 -->
      <el-skeleton v-if="loading" animated :loading="true" class="file-skeleton">
        <template #template>
          <div v-for="i in 6" :key="i" class="file-row skeleton-row">
            <el-skeleton-item variant="rect" class="skeleton-tile" />
            <div class="file-main">
              <el-skeleton-item variant="text" style="width: 42%" />
              <el-skeleton-item variant="text" style="width: 24%" />
            </div>
            <el-skeleton-item variant="text" style="width: 64px" class="skeleton-stat" />
          </div>
        </template>
      </el-skeleton>

      <!-- 空状态 -->
      <div v-else-if="items.length === 0" class="file-empty">
        <div class="file-empty-icon">
          <el-icon><FolderOpened /></el-icon>
        </div>
        <p class="file-empty-title">{{ view === 'all' ? '还没有附件' : '回收站是空的' }}</p>
        <p class="file-empty-hint">
          {{ view === 'all' ? '上传保修单、说明书或照片，把每一件物品的凭证收进账册。' : '删除的附件会在这里保留一段时间，恢复后重新回到原挂载点。' }}
        </p>
        <el-button v-if="view === 'all'" type="primary" @click="triggerUpload">上传第一个附件</el-button>
      </div>

      <!-- 附件列表 -->
      <div v-else class="file-list">
        <div
          v-for="row in items"
          :key="row.id"
          class="file-row"
          :class="{ 'is-recycled': view === 'recycled' }"
        >
          <div class="file-tile" :class="{ 'is-image': isImageType(row.mediaType) && !failedImages.has(row.id) }">
            <img
              v-if="isImageType(row.mediaType) && !failedImages.has(row.id)"
              :src="row.url"
              :alt="row.name"
              loading="lazy"
              class="file-thumb"
              @error="onImageError(row.id)"
            />
            <span v-else class="file-ext">{{ fileExtension(row) }}</span>
          </div>

          <div class="file-main">
            <div class="file-name" :title="row.name">{{ row.name }}</div>
            <div class="file-sub">
              <span v-if="row.mountType === 'HOUSEHOLD'" class="zj-badge zj-badge-plain mount-badge">家庭</span>
              <el-link
                v-else-if="row.mountType === 'ITEM'"
                type="primary"
                underline="never"
                @click="goToItem(row.mountId)"
              >
                {{ itemName(row.mountId) }}
              </el-link>
              <el-link
                v-else
                type="primary"
                underline="never"
                @click="goToLot(row.mountId)"
              >
                {{ lotLabel(row.mountId) }}
              </el-link>
              <span class="file-type">{{ mediaTypeLabel(row.mediaType) }}</span>
            </div>
          </div>

          <div class="file-stat">
            <span class="zj-num file-size">{{ formatBytes(row.byteSize) }}</span>
            <span
              class="zj-num file-date"
              :title="view === 'recycled' ? '删除于 ' + formatDateTime(row.deletedAt ?? '') : undefined"
            >
              {{ formatDateTime(view === 'recycled' ? (row.deletedAt ?? row.createdAt) : row.createdAt) }}
            </span>
          </div>

          <!-- 知识来源状态与操作（仅未删除附件）；已停用回到可重新选择状态 -->
          <div v-if="view === 'all'" class="file-knowledge">
            <template v-if="knowledgeOf(row.id) && knowledgeOf(row.id)!.status !== 'DISABLED'">
              <span class="ks-status" data-testid="knowledge-status">
                <span class="zj-dot" :class="ksDotClass(knowledgeOf(row.id)!)"></span>
                {{ ksLabel(knowledgeOf(row.id)!) }}
              </span>
              <span v-if="knowledgeOf(row.id)!.status === 'FAILED'" class="ks-failure" :title="knowledgeOf(row.id)!.failureMessage ?? '处理失败'">
                失败原因
              </span>
              <div class="ks-actions">
                <el-button
                  v-if="knowledgeOf(row.id)!.status === 'FAILED'"
                  text
                  type="primary"
                  data-testid="knowledge-retry"
                  @click="retrySource(row as Attachment)"
                >
                  重试
                </el-button>
                <el-button
                  text
                  data-testid="knowledge-cancel"
                  @click="cancelSource(row as Attachment)"
                >
                  取消
                </el-button>
              </div>
            </template>
            <el-tooltip
              v-else
              :disabled="isKnowledgeEligible(row.mediaType)"
              content="图片与旧版 Office 格式暂不支持作为知识来源"
              placement="top"
            >
              <span>
                <el-button
                  text
                  type="primary"
                  :disabled="!isKnowledgeEligible(row.mediaType)"
                  data-testid="knowledge-select"
                  @click="selectSource(row as Attachment)"
                >
                  设为知识来源
                </el-button>
              </span>
            </el-tooltip>
          </div>

          <div class="file-actions">
            <el-button text data-testid="attachment-rename" @click="rename(row as Attachment)">改名</el-button>
            <el-button
              v-if="view === 'all' && row.mountType !== 'HOUSEHOLD'"
              text
              type="primary"
              @click="moveToHousehold(row as Attachment)"
            >
              移到家庭
            </el-button>
            <el-button
              v-if="view === 'all' && row.mountType === 'HOUSEHOLD'"
              text
              type="primary"
              data-testid="attachment-move-item"
              @click="openMoveDialog('item', row as Attachment)"
            >
              移到物品
            </el-button>
            <el-button
              v-if="view === 'all' && row.mountType === 'HOUSEHOLD'"
              text
              type="primary"
              data-testid="attachment-move-lot"
              @click="openMoveDialog('lot', row as Attachment)"
            >
              移到批次
            </el-button>
            <el-button v-if="view === 'all'" text type="danger" data-testid="attachment-delete" @click="remove(row as Attachment)">
              删除
            </el-button>
            <el-button v-if="view === 'recycled'" text type="primary" data-testid="attachment-restore" @click="restore(row as Attachment)">
              恢复
            </el-button>
            <el-button v-if="view === 'recycled'" text type="danger" data-testid="attachment-purge" @click="purge(row as Attachment)">
              永久删除
            </el-button>
            <el-button text @click="download(row as Attachment)">下载</el-button>
          </div>
        </div>

        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          class="file-pagination"
          @current-change="load"
          @size-change="load"
        />
      </div>
    </div>

    <!-- 改挂目标选择框 -->
    <el-dialog
      v-model="moveDialogVisible"
      :title="moveType === 'item' ? '移到物品' : '移到批次'"
      width="480px"
      align-center
    >
      <p class="move-dialog-subtitle">
        把「{{ moveTarget?.name }}」挂到所选目标上，内容与展示名保持不变。
      </p>
      <div v-if="moveOptions.length === 0" class="move-empty" data-testid="attachment-move-empty">
        {{ mountNamesFailed
          ? '目标信息加载失败，请刷新后重试。'
          : (moveType === 'item' ? '家庭还没有可选的物品，先到物品页添加。' : '家庭还没有可选的批次，先到库存页录入批次。') }}
      </div>
      <el-select
        v-else
        v-model="moveSelection"
        filterable
        :placeholder="moveType === 'item' ? '搜索并选择物品' : '搜索并选择批次'"
        style="width: 100%"
        data-testid="attachment-move-select"
      >
        <el-option v-for="opt in moveOptions" :key="opt.id" :label="opt.label" :value="opt.id">
          <span>{{ opt.label }}</span>
          <span v-if="opt.archived" class="zj-badge zj-badge-plain move-badge">已归档</span>
        </el-option>
        <template #empty>
          <span data-testid="attachment-move-empty-filter">没有匹配的目标</span>
        </template>
      </el-select>
      <template #footer>
        <el-button @click="closeMoveDialog">取消</el-button>
        <el-button type="primary" :disabled="!moveSelection" data-testid="attachment-move-confirm" @click="confirmMove">
          {{ moveType === 'item' ? '移到物品' : '移到批次' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Upload, Search, FolderOpened } from "@element-plus/icons-vue";
import {
  listAttachments,
  renameAttachment,
  deleteAttachment,
  restoreAttachment,
  purgeAttachment,
  remountAttachmentToHousehold,
  remountAttachmentToItem,
  remountAttachmentToLot,
  uploadHouseholdAttachment,
  type Attachment
} from "../api/file";
import {
  fetchKnowledgeSources,
  selectKnowledgeSource,
  cancelKnowledgeSource,
  retryKnowledgeSource,
  type KnowledgeSourceInfo
} from "../api/ai";
import { KNOWLEDGE_SOURCE_MEDIA_TYPES } from "../types/ai";
import { fetchItems } from "../api/catalog";
import { fetchLots } from "../api/inventory";
import { ApiError } from "../api/http";
import { FILE_NAME_DUPLICATE } from "../types/errorCodes";
import { formatDateTime } from "../utils/date";
import { formatBytes } from "../utils/format";

const router = useRouter();

const loading = ref(false);
const items = ref<Attachment[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);
const view = ref<"all" | "recycled">("all");
const filterMountType = ref("");
const filterQ = ref("");
const pagination = ref({ page: 1, pageSize: 20, total: 0 });

/** 知识来源状态（fileId → 状态），异步准备期间轮询刷新。 */
const knowledgeSources = ref<Map<string, KnowledgeSourceInfo>>(new Map());
const KNOWLEDGE_POLL_INTERVAL_MS = 5000;
let knowledgePollTimer: ReturnType<typeof setInterval> | null = null;

interface ItemMeta {
  name: string;
  archived: boolean;
}

const itemMeta = ref<Map<string, ItemMeta>>(new Map());
const lotLabels = ref<Map<string, string>>(new Map());

/** 目标映射加载失败标记：改挂选择框据此区分「无目标」与「加载失败」。 */
const mountNamesFailed = ref(false);

/** 改挂目标选择框状态 */
const moveDialogVisible = ref(false);
const moveType = ref<"item" | "lot">("item");
const moveTarget = ref<Attachment | null>(null);
const moveSelection = ref("");

const moveOptions = computed(() => {
  if (moveType.value === "item") {
    return Array.from(itemMeta.value.entries()).map(([id, meta]) => ({
      id,
      label: meta.name,
      archived: meta.archived
    }));
  }
  return Array.from(lotLabels.value.entries()).map(([id, label]) => ({
    id,
    label,
    archived: false
  }));
});

/** 缩略图加载失败的附件 id：回退为扩展名瓦片 */
const failedImages = ref<Set<string>>(new Set());

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
};

onMounted(() => {
  load();
  loadMountNames();
  loadKnowledgeSources();
});

onBeforeUnmount(() => {
  stopKnowledgePolling();
});

let searchTimer: ReturnType<typeof setTimeout>;

function onSearchInput() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(onFilterChange, 300);
}

function onViewChange() {
  pagination.value.page = 1;
  load();
  syncKnowledgePolling();
}

function onFilterChange() {
  pagination.value.page = 1;
  load();
}

async function load() {
  loading.value = true;
  try {
    const page = await listAttachments({
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
      mountType: filterMountType.value || undefined,
      q: filterQ.value || undefined,
      recycled: view.value === "recycled"
    });
    items.value = page.items;
    pagination.value.total = page.total;
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "加载附件失败");
  } finally {
    loading.value = false;
  }
}

/** 加载物品名与批次标识，用于挂载点列展示与改挂目标选择。 */
async function loadMountNames() {
  try {
    const [itemsResp, lotsResp] = await Promise.all([
      fetchItems({ pageSize: 1000 }),
      fetchLots({ pageSize: 1000 })
    ]);
    itemMeta.value = new Map(
      itemsResp.items.map((i) => [i.id, { name: i.name, archived: i.status === "ARCHIVED" }])
    );
    lotLabels.value = new Map(
      lotsResp.items.map((l) => [
        l.lotId,
        [l.itemName, l.lotNumber, l.serialNumber].filter(Boolean).join(" · ") || l.lotId.slice(0, 8)
      ])
    );
    mountNamesFailed.value = false;
  } catch {
    // 名称加载失败时回退为短 ID；改挂选择框显示加载失败提示
    mountNamesFailed.value = true;
  }
}

// ==================== 知识来源 ====================

const KNOWLEDGE_STATUS_LABELS: Record<string, string> = {
  PROCESSING: "处理中",
  AVAILABLE: "可用",
  FAILED: "失败",
  DISABLED: "已停用"
};

function knowledgeOf(fileId: string): KnowledgeSourceInfo | undefined {
  return knowledgeSources.value.get(fileId);
}

function ksLabel(source: KnowledgeSourceInfo): string {
  return KNOWLEDGE_STATUS_LABELS[source.status] ?? source.status;
}

function ksDotClass(source: KnowledgeSourceInfo): string {
  switch (source.status) {
    case "AVAILABLE":
      return "zj-dot-pine";
    case "PROCESSING":
      return "zj-dot-warn";
    case "FAILED":
      return "zj-dot-danger";
    default:
      return "zj-dot-off";
  }
}

function isKnowledgeEligible(mediaType: string): boolean {
  return KNOWLEDGE_SOURCE_MEDIA_TYPES.includes(mediaType);
}

async function loadKnowledgeSources() {
  try {
    const sources = await fetchKnowledgeSources();
    knowledgeSources.value = new Map(sources.map((s) => [s.fileId, s]));
  } catch {
    // 知识来源状态加载失败不阻塞附件列表
  }
  syncKnowledgePolling();
}

/** 有处理中的来源时轮询其状态（含自动重试触发的状态变化）。 */
function syncKnowledgePolling() {
  const hasLive = Array.from(knowledgeSources.value.values()).some(
    (s) => s.status === "PROCESSING"
  );
  if (hasLive && view.value === "all") {
    startKnowledgePolling();
  } else {
    stopKnowledgePolling();
  }
}

function startKnowledgePolling() {
  if (knowledgePollTimer !== null) return;
  knowledgePollTimer = setInterval(() => {
    loadKnowledgeSources();
  }, KNOWLEDGE_POLL_INTERVAL_MS);
}

function stopKnowledgePolling() {
  if (knowledgePollTimer !== null) {
    clearInterval(knowledgePollTimer);
    knowledgePollTimer = null;
  }
}

async function selectSource(row: Attachment) {
  try {
    await selectKnowledgeSource(row.id);
    ElMessage.success("已加入知识来源，正在准备内容");
    await loadKnowledgeSources();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "选择失败");
  }
}

async function cancelSource(row: Attachment) {
  try {
    await cancelKnowledgeSource(row.id);
    ElMessage.success("已取消知识来源");
    await loadKnowledgeSources();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "取消失败");
  }
}

async function retrySource(row: Attachment) {
  try {
    await retryKnowledgeSource(row.id);
    ElMessage.success("已重新处理");
    await loadKnowledgeSources();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "重试失败");
  }
}

function isDuplicateNameError(error: unknown): boolean {
  return error instanceof ApiError && error.errorCode === FILE_NAME_DUPLICATE;
}

function itemName(id: string): string {
  return itemMeta.value.get(id)?.name ?? id.slice(0, 8);
}

function lotLabel(id: string): string {
  return lotLabels.value.get(id) ?? id.slice(0, 8);
}

function mediaTypeLabel(mediaType: string): string {
  return MEDIA_LABELS[mediaType] ?? mediaType;
}

function isImageType(mediaType: string): boolean {
  return mediaType.startsWith("image/");
}

/** 文件瓦片上的扩展名标识：取文件名末段，图片取类型缩写 */
function fileExtension(row: Attachment): string {
  if (isImageType(row.mediaType)) {
    const subtype = row.mediaType.split("/")[1] ?? "";
    return subtype.slice(0, 4).toUpperCase();
  }
  const dot = row.name.lastIndexOf(".");
  if (dot > 0 && dot < row.name.length - 1) {
    return row.name.slice(dot + 1).slice(0, 4).toUpperCase();
  }
  return "FILE";
}

function onImageError(id: string) {
  failedImages.value = new Set(failedImages.value).add(id);
}

function triggerUpload() {
  fileInput.value?.click();
}

async function onFileChosen(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = "";
  if (!file) return;
  try {
    await uploadHouseholdAttachment(file);
    ElMessage.success("已上传");
    await load();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "上传失败");
  }
}

function download(row: Attachment) {
  window.open(row.url, "_blank");
}

async function rename(row: Attachment) {
  try {
    const picked = await ElMessageBox.prompt("新名字", "改名", {
      inputValue: row.name,
      confirmButtonText: "确定",
      cancelButtonText: "取消",
      inputPattern: /\S+/,
      inputErrorMessage: "名字不能为空"
    });
    const name = picked.value.trim();
    await renameAttachment(row.id, name);
    ElMessage.success("已改名");
    await load();
  } catch (error) {
    if (error === "cancel" || error === "close") return;
    ElMessage.error(error instanceof ApiError ? error.message : "改名失败");
  }
}

async function remove(row: Attachment) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.name}」？删除后进入回收站，保留期内可以恢复。`, "删除附件", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning"
    });
  } catch {
    return;
  }
  try {
    await deleteAttachment(row.id);
    ElMessage.success("已删除，可在回收站恢复");
    await load();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "删除失败");
  }
}

async function restore(row: Attachment) {
  try {
    await restoreAttachment(row.id);
    ElMessage.success("已恢复");
    await load();
  } catch (error) {
    if (isDuplicateNameError(error)) {
      ElMessage.warning("原挂载点已有一份同名附件，请先改名再恢复");
      return;
    }
    ElMessage.error(error instanceof ApiError ? error.message : "恢复失败");
  }
}

async function purge(row: Attachment) {
  try {
    await ElMessageBox.confirm(
      `永久删除「${row.name}」后不可恢复，此后生成的备份中也不再包含此文件。确定继续吗？`,
      "永久删除附件",
      {
        confirmButtonText: "永久删除",
        cancelButtonText: "取消",
        type: "warning"
      }
    );
  } catch {
    return;
  }
  try {
    await purgeAttachment(row.id);
    ElMessage.success("已永久删除");
    await load();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "永久删除失败");
  }
}

async function moveToHousehold(row: Attachment) {
  try {
    await remountAttachmentToHousehold(row.id);
    ElMessage.success("已移到家庭");
    await load();
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "改挂失败");
  }
}

function openMoveDialog(type: "item" | "lot", row: Attachment) {
  moveType.value = type;
  moveTarget.value = row;
  moveSelection.value = "";
  moveDialogVisible.value = true;
}

function closeMoveDialog() {
  moveDialogVisible.value = false;
}

async function confirmMove() {
  const target = moveTarget.value;
  if (!target || !moveSelection.value) return;
  try {
    if (moveType.value === "item") {
      await remountAttachmentToItem(moveSelection.value, target.id);
      ElMessage.success("已移到物品");
    } else {
      await remountAttachmentToLot(moveSelection.value, target.id);
      ElMessage.success("已移到批次");
    }
    closeMoveDialog();
    await load();
  } catch (error) {
    if (isDuplicateNameError(error)) {
      ElMessage.warning("目标已有一份同名附件，请先改名再移动");
      return;
    }
    ElMessage.error(error instanceof ApiError ? error.message : "改挂失败");
  }
}

function goToItem(itemId: string) {
  router.push({ name: "items", query: { highlight: itemId } });
}

function goToLot(lotId: string) {
  router.push({ name: "inventory", query: { lotId } });
}

</script>

<style scoped>
.file-input {
  display: none;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.filters {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
  padding: 10px 12px;
  background: var(--zj-surface-sunken);
  border-radius: var(--zj-radius-md);
}

.filter-count {
  margin-left: auto;
  font-size: 13px;
  color: var(--zj-ink-400);
}

/* ---------- 文件卡片 ---------- */
.file-card {
  padding: 4px 16px 16px;
}

.file-list {
  display: flex;
  flex-direction: column;
}

/* 行：瓦片 | 主信息 | 数值 | 操作，hover 时轻浮起 */
.file-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 4px;
  border-bottom: 1px solid var(--zj-line);
  transition: background-color var(--zj-dur-fast) var(--zj-ease-out);
}

.file-row:hover {
  background: var(--zj-pine-50);
}

.file-row:last-child {
  border-bottom: 0;
}

/* 回收站行：整体降调，操作仍可用 */
.file-row.is-recycled .file-name {
  color: var(--zj-ink-400);
}

/* ---------- 文件瓦片 ---------- */
.file-tile {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: var(--zj-radius-sm);
  background: var(--zj-surface-sunken);
  overflow: hidden;
}

.file-tile.is-image {
  background: var(--zj-canvas);
}

.file-thumb {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.file-ext {
  font-family: var(--zj-mono);
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--zj-ink-600);
}

/* ---------- 主信息 ---------- */
.file-main {
  flex: 1;
  min-width: 0;
}

.file-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  font-weight: 500;
  color: var(--zj-ink-900);
}

.file-sub {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
  min-width: 0;
}

.mount-badge {
  padding: 0 8px;
  line-height: 16px;
  font-size: 11px;
}

.file-type {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--zj-ink-400);
}

/* ---------- 数值列 ---------- */
.file-stat {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 3px;
  flex-shrink: 0;
}

.file-size {
  font-size: 13px;
  color: var(--zj-ink-600);
}

.file-date {
  font-size: 12px;
  color: var(--zj-ink-400);
}

/* ---------- 知识来源列 ---------- */
.file-knowledge {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 96px;
  flex-shrink: 0;
}

.ks-status {
  display: inline-flex;
  align-items: center;
  font-size: 12px;
  color: var(--zj-ink-600);
  white-space: nowrap;
}

.ks-status .zj-dot {
  margin-right: 5px;
}

.ks-failure {
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: var(--zj-danger);
  cursor: help;
}

.ks-actions {
  display: flex;
  gap: 0;
}

.ks-actions .el-button {
  margin-left: 0;
  padding: 0 4px;
  height: 22px;
  font-size: 12px;
}

/* ---------- 操作 ---------- */
.file-actions {
  display: flex;
  gap: 2px;
  flex-shrink: 0;
}

.file-actions .el-button {
  margin-left: 0;
}

/* ---------- 骨架 ---------- */
.file-skeleton :deep(.skeleton-row) {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 4px;
}

.file-skeleton :deep(.skeleton-tile) {
  width: 44px;
  height: 44px;
  border-radius: var(--zj-radius-sm);
  flex-shrink: 0;
}

.file-skeleton :deep(.file-main) {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.file-skeleton :deep(.skeleton-stat) {
  margin-left: auto;
}

/* ---------- 空状态 ---------- */
.file-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 0 64px;
}

.file-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin-bottom: 16px;
  border-radius: var(--zj-radius-md);
  background: var(--zj-pine-50);
  color: var(--zj-pine-500);
}

.file-empty-icon .el-icon {
  font-size: 26px;
}

.file-empty-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.file-empty-hint {
  margin: 8px 0 20px;
  max-width: 420px;
  text-align: center;
  font-size: 13px;
  line-height: 1.6;
  color: var(--zj-ink-600);
}

.file-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

/* ---------- 改挂目标选择框 ---------- */
.move-dialog-subtitle {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--zj-ink-600);
}

.move-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: var(--zj-ink-400);
}

.move-badge {
  margin-left: 8px;
  padding: 0 8px;
  line-height: 16px;
  font-size: 11px;
}
</style>
