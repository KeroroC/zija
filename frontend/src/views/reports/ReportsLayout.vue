<template>
  <div class="page-container">
    <!-- 路由驱动的子页导航：tab name 与 /reports/<segment> 一一对应 -->
    <el-tabs v-model="activeTab" class="report-tabs">
      <el-tab-pane label="全局搜索" name="search" />
      <el-tab-pane label="库存分布" name="stock-by-location" />
      <el-tab-pane label="临期批次" name="expiring-lots" />
      <el-tab-pane label="低库存" name="low-stock" />
      <el-tab-pane label="库存变化" name="stock-changes" />
      <el-tab-pane label="流水" name="movements" />
      <el-tab-pane label="报表设置" name="settings" />
    </el-tabs>
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";

const route = useRoute();
const router = useRouter();

// 活动标签 = 当前路由的末段路径；切换即跳转到对应的 report-* 路由
// （el-tabs 仅在点击不同标签时触发 setter，无需额外去重）
const activeTab = computed({
  get: () => route.path.replace(/^\/reports\/?/, "") || "search",
  set: (name: string) => {
    router.push({ name: `report-${name}` });
  }
});
</script>

<style scoped>
.report-tabs {
  margin-bottom: 24px;
}
</style>
