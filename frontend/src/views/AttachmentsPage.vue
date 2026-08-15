<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">附件</h2>
        <p class="page-subtitle">保管挂在家庭上的文档与图片</p>
      </div>
      <el-button type="primary" @click="triggerUpload">上传</el-button>
      <input
        ref="fileInput"
        class="file-input"
        type="file"
        @change="onFileChosen"
      />
    </div>

    <div class="card">
      <el-table v-loading="loading" :data="items" empty-text="还没有家庭附件">
        <el-table-column prop="name" label="名字" min-width="200" />
        <el-table-column prop="mediaType" label="类型" width="180" />
        <el-table-column label="大小" width="120">
          <template #default="{ row }">{{ formatSize(row.byteSize) }}</template>
        </el-table-column>
        <el-table-column label="日期" width="180">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="" width="148" align="right">
          <template #default="{ row }">
            <el-button text type="primary" data-testid="attachment-rename" @click="rename(row as Attachment)">改名</el-button>
            <el-button text type="primary" @click="download(row as Attachment)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { listAttachments, renameAttachment, uploadHouseholdAttachment, type Attachment } from "../api/file";
import { ApiError } from "../api/http";
import { formatDateTime } from "../utils/date";

const loading = ref(false);
const items = ref<Attachment[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);

onMounted(() => {
  load();
});

async function load() {
  loading.value = true;
  try {
    const page = await listAttachments({ page: 1, pageSize: 50 });
    items.value = page.items;
  } catch (error) {
    ElMessage.error(error instanceof ApiError ? error.message : "加载附件失败");
  } finally {
    loading.value = false;
  }
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

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MiB`;
}
</script>

<style scoped>
.file-input {
  display: none;
}
</style>
