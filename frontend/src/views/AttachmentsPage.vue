<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">附件</h2>
        <p class="page-subtitle">保管家庭、物品与批次上的文档和图片</p>
      </div>
      <div class="header-actions">
        <el-radio-group v-model="view" size="default" @change="onViewChange">
          <el-radio-button value="all">全部</el-radio-button>
          <el-radio-button value="recycled">回收站</el-radio-button>
        </el-radio-group>
        <el-button v-if="view === 'all'" type="primary" data-testid="attachment-upload" @click="triggerUpload">
          上传
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
        style="width: 220px"
        @input="onSearchInput"
      />
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="items" :empty-text="view === 'all' ? '还没有附件' : '回收站是空的'">
        <el-table-column prop="name" label="名字" min-width="200">
          <template #default="{ row }">
            <span class="attachment-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="挂载点" min-width="160">
          <template #default="{ row }">
            <span v-if="row.mountType === 'HOUSEHOLD'" class="mount-label">家庭</span>
            <el-link
              v-else-if="row.mountType === 'ITEM'"
              type="primary"
              :underline="false"
              @click="goToItem(row.mountId)"
            >
              {{ itemName(row.mountId) }}
            </el-link>
            <el-link
              v-else
              type="primary"
              :underline="false"
              @click="goToLot(row.mountId)"
            >
              {{ lotLabel(row.mountId) }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="190" class-name="col-secondary">
          <template #default="{ row }"><span class="cell-secondary">{{ mediaTypeLabel(row.mediaType) }}</span></template>
        </el-table-column>
        <el-table-column label="大小" width="100">
          <template #default="{ row }"><span class="zj-num">{{ formatBytes(row.byteSize) }}</span></template>
        </el-table-column>
        <el-table-column label="日期" width="160" class-name="col-secondary">
          <template #default="{ row }">
            <span class="cell-secondary zj-num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="" width="220" align="right">
          <template #default="{ row }">
            <el-button text type="primary" data-testid="attachment-rename" @click="rename(row as Attachment)">改名</el-button>
            <el-button
              v-if="view === 'all' && row.mountType !== 'HOUSEHOLD'"
              text
              type="primary"
              @click="moveToHousehold(row as Attachment)"
            >
              移到家庭
            </el-button>
            <el-button v-if="view === 'all'" text type="danger" data-testid="attachment-delete" @click="remove(row as Attachment)">
              删除
            </el-button>
            <el-button v-else text type="primary" data-testid="attachment-restore" @click="restore(row as Attachment)">
              恢复
            </el-button>
            <el-button text @click="download(row as Attachment)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="load"
        @size-change="load"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  listAttachments,
  renameAttachment,
  deleteAttachment,
  restoreAttachment,
  remountAttachmentToHousehold,
  uploadHouseholdAttachment,
  type Attachment
} from "../api/file";
import { fetchItems } from "../api/catalog";
import { fetchLots } from "../api/inventory";
import { ApiError } from "../api/http";
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

const itemNames = ref<Map<string, string>>(new Map());
const lotLabels = ref<Map<string, string>>(new Map());

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
});

let searchTimer: ReturnType<typeof setTimeout>;

function onSearchInput() {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(onFilterChange, 300);
}

function onViewChange() {
  pagination.value.page = 1;
  load();
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

/** 加载物品名与批次标识，用于挂载点列展示。 */
async function loadMountNames() {
  try {
    const [itemsResp, lotsResp] = await Promise.all([
      fetchItems({ pageSize: 1000 }),
      fetchLots({ pageSize: 1000 })
    ]);
    itemNames.value = new Map(itemsResp.items.map((i) => [i.id, i.name]));
    lotLabels.value = new Map(
      lotsResp.items.map((l) => [
        l.lotId,
        [l.itemName, l.lotNumber, l.serialNumber].filter(Boolean).join(" · ") || l.lotId.slice(0, 8)
      ])
    );
  } catch {
    // 名称加载失败时回退为短 ID
  }
}

function itemName(id: string): string {
  return itemNames.value.get(id) ?? id.slice(0, 8);
}

function lotLabel(id: string): string {
  return lotLabels.value.get(id) ?? id.slice(0, 8);
}

function mediaTypeLabel(mediaType: string): string {
  return MEDIA_LABELS[mediaType] ?? mediaType;
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
    if (error instanceof ApiError && error.errorCode === "FILE_NAME_DUPLICATE") {
      ElMessage.warning("原挂载点已有一份同名附件，请先改名再恢复");
      return;
    }
    ElMessage.error(error instanceof ApiError ? error.message : "恢复失败");
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
  margin-bottom: 16px;
  padding: 10px 12px;
  background: var(--zj-surface-sunken);
  border-radius: var(--zj-radius-md);
}
.card {
  background: var(--zj-surface);
  border-radius: var(--zj-radius-md);
  padding: 8px 16px 16px;
  box-shadow: var(--zj-shadow-sm);
}
.attachment-name {
  color: var(--zj-ink-900);
}
.mount-label {
  color: var(--zj-ink-600);
}
.cell-secondary {
  color: var(--zj-ink-600);
}
</style>
