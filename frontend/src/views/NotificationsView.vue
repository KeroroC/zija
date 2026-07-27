<template>
  <div class="page-container-narrow">
    <header class="page-header">
      <div>
        <h2 class="page-title">通知</h2>
        <p class="page-subtitle">全部站内通知</p>
      </div>
      <el-button size="small" @click="onReadAll">全部已读</el-button>
    </header>

    <div class="notif-list">
      <div
        v-for="n in items"
        :key="n.id"
        class="notif-card"
        :class="{ unread: !n.read }"
      >
        <div class="notif-head">
          <span class="zj-badge" :class="scopeBadge(n.scope)">{{
            scopeLabel(n.scope)
          }}</span>
          <span class="notif-time">{{ n.createdAt }}</span>
        </div>
        <div class="notif-title">{{ n.title }}</div>
        <div class="notif-msg">{{ n.message }}</div>
        <el-button
          v-if="!n.read"
          text
          size="small"
          @click="onReadOne(n.id)"
          >标记已读</el-button
        >
      </div>
      <div v-if="items.length === 0" class="empty">暂无通知</div>
    </div>

    <el-pagination
      :total="total"
      :current-page="page"
      :page-size="pageSize"
      layout="prev, pager, next"
      @current-change="onPage"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import {
  fetchNotifications,
  markNotificationRead,
  markAllNotificationsRead,
} from "../api/notification";
import type { NotificationItem } from "../types/notification";
import { ApiError } from "../api/http";

const items = ref<NotificationItem[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);

onMounted(reload);

async function reload() {
  try {
    const r = await fetchNotifications(page.value, pageSize.value, false);
    items.value = r.items;
    total.value = r.total;
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

function onPage(p: number) {
  page.value = p;
  reload();
}

async function onReadOne(id: string) {
  try {
    await markNotificationRead(id);
    await reload();
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

async function onReadAll() {
  try {
    await markAllNotificationsRead();
    ElMessage.success("已全部标为已读");
    await reload();
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

function scopeLabel(s: string) {
  return (
    (
      {
        TASK_CREATED: "新建",
        TASK_CLOSED: "关闭",
        RULE_CHANGED: "规则",
      } as Record<string, string>
    )[s] ?? s
  );
}

function scopeBadge(s: string) {
  return s === "RULE_CHANGED" ? "zj-badge-ink" : "zj-badge-pine";
}
</script>

<style scoped>
.notif-card {
  background: var(--zj-surface);
  border-radius: var(--zj-radius-md);
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: var(--zj-shadow-sm);
}

.notif-card.unread {
  border-left: 3px solid var(--zj-pine-600);
}

.notif-head {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.notif-time {
  color: var(--zj-ink-400);
  font-size: 12px;
}

.notif-title {
  font-size: 14px;
  color: var(--zj-ink-900);
  font-weight: 600;
}

.notif-msg {
  color: var(--zj-ink-600);
  font-size: 13px;
  margin-top: 4px;
}

.empty {
  padding: 24px 0;
  text-align: center;
  color: var(--zj-ink-400);
}
</style>
