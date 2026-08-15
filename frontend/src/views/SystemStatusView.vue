<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import { fetchSystemInfo } from "../api/system";
import type { SystemInfo } from "../types/system";

const loading = ref(true);
const error = ref("");
const info = ref<SystemInfo>();

const databaseTimeLabel = computed(() => {
  if (!info.value) {
    return "";
  }
  return new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "medium"
  }).format(new Date(info.value.databaseTime));
});

async function copyInstallationId() {
  if (!info.value) return;
  try {
    await navigator.clipboard.writeText(info.value.installationId);
    ElMessage.success("安装标识已复制");
  } catch {
    ElMessage.error("复制失败，请手动选择");
  }
}

onMounted(async () => {
  try {
    info.value = await fetchSystemInfo();
  } catch {
    error.value = "暂时无法读取系统状态，请检查后端与数据库。";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">系统状态</h1>
        <p class="page-subtitle">后端与数据库连接情况</p>
      </div>
    </div>

    <el-card v-if="loading">
      <el-skeleton :rows="4" animated />
    </el-card>

    <el-alert
      v-else-if="error"
      title="暂时无法读取系统状态"
      :description="error"
      type="error"
      show-icon
      :closable="false"
    />

    <el-card v-else-if="info">
      <div class="status-ok">
        <svg class="status-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
          <path d="M12 2.5a9.5 9.5 0 1 0 9.5 9.5" />
          <path d="m8 12.5 2.5 2.5L16 9.5" />
        </svg>
        <div>
          <p class="status-title">系统运行正常</p>
          <p class="status-subtitle">PostgreSQL 已连接</p>
        </div>
      </div>
      <el-descriptions :column="2" class="status-descriptions">
        <el-descriptions-item label="应用">
          {{ info.application }}
        </el-descriptions-item>
        <el-descriptions-item label="版本">
          <span class="zj-num">{{ info.version }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="数据库时间">
          {{ databaseTimeLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="安装标识">
          <span class="install-id">
            <span class="zj-mono">{{ info.installationId }}</span>
            <el-button size="small" text @click="copyInstallationId">复制</el-button>
          </span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>

<style scoped>
/* 状态区：细线对勾 + 双行文字，替代 el-result 的大块彩色图标（spec §5.7） */
.status-ok {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.status-check {
  width: 40px;
  height: 40px;
  color: var(--zj-pine-500);
  flex-shrink: 0;
}

.status-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.status-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--zj-ink-600);
}

/* 描述列表去竖边框，仅保留行分隔发丝线。
   注意：不能对 .el-descriptions__cell:last-child 做处理——它在表格里匹配的是
   「每行最后一个单元格」（版本/安装标识列），会造成左列有线、右列无线。
   改为只在第一行单元格加下边框，得到两行之间一条通栏发丝线。 */
.status-descriptions :deep(.el-descriptions__body) {
  border: 0;
}

.status-descriptions :deep(.el-descriptions__table) {
  border: 0;
}

.status-descriptions :deep(.el-descriptions__cell) {
  border: 0;
  padding: 10px 0;
}

.status-descriptions :deep(.el-descriptions__table tr:first-of-type .el-descriptions__cell) {
  border-bottom: 1px solid var(--zj-line);
}

.status-descriptions :deep(.el-descriptions__label) {
  color: var(--zj-ink-400);
  font-size: 12px;
  letter-spacing: 0.04em;
  line-height: 1.5;
}

.install-id {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.install-id .zj-mono {
  font-size: 12px;
}
</style>
