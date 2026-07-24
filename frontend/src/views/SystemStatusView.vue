<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
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
      <el-result
        icon="success"
        title="系统运行正常"
        sub-title="PostgreSQL 已连接"
      />
      <el-descriptions :column="2" border>
        <el-descriptions-item label="应用">
          {{ info.application }}
        </el-descriptions-item>
        <el-descriptions-item label="版本">
          {{ info.version }}
        </el-descriptions-item>
        <el-descriptions-item label="数据库时间">
          {{ databaseTimeLabel }}
        </el-descriptions-item>
        <el-descriptions-item label="安装标识">
          {{ info.installationId }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </section>
</template>
