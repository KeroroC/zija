<template>
  <div class="page-container not-found">
    <p class="not-found-code">404</p>
    <h1 class="not-found-title">这一页不在家</h1>
    <p class="not-found-hint">你找的页面不存在，或已被移动到其他位置。</p>
    <el-button type="primary" @click="goHome">返回首页</el-button>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from "vue-router";

const router = useRouter();

function goHome() {
  router.push({ name: "home" });
}
</script>

<style scoped>
.not-found {
  padding-top: 64px;
  text-align: center;
}
.not-found-code {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 64px;
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--zj-pine-100);
  line-height: 1.1;
}
.not-found-title {
  margin: 8px 0 0;
  font-family: var(--zj-serif);
  font-size: 26px;
  font-weight: 600;
}
.not-found-hint {
  margin: 10px 0 28px;
  font-size: 14px;
  color: var(--zj-ink-600);
}
</style>
