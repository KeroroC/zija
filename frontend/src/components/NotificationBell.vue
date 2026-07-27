<template>
  <el-popover trigger="click" width="360" placement="bottom-end" @show="loadRecent">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-button circle size="small" aria-label="通知">
          <el-icon><Bell /></el-icon>
        </el-button>
      </el-badge>
    </template>
    <div class="bell-list">
      <div v-for="n in recent" :key="n.id" class="bell-item">
        <div class="bell-item-title">{{ n.title }}</div>
        <div class="bell-item-time">{{ n.createdAt }}</div>
      </div>
      <div v-if="recent.length === 0" class="bell-empty">暂无未读通知</div>
      <div class="bell-actions">
        <el-button text size="small" @click="onReadAll">全部已读</el-button>
        <el-button text size="small" @click="goAll">查看全部</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from "vue";
import { useRouter } from "vue-router";
import { Bell } from "@element-plus/icons-vue";
import { ElMessage } from "element-plus";
import {
  fetchUnreadCount,
  fetchNotifications,
  markAllNotificationsRead,
  type NotificationItem,
} from "../api/notification";

const router = useRouter();
const unreadCount = ref(0);
const recent = ref<NotificationItem[]>([]);
let timer: ReturnType<typeof setInterval> | undefined;

async function refresh() {
  try {
    unreadCount.value = (await fetchUnreadCount()).count;
  } catch {
    /* 静默 */
  }
}

async function loadRecent() {
  try {
    const p = await fetchNotifications(1, 5, true);
    recent.value = p.items;
  } catch {
    /* 静默 */
  }
}

async function onReadAll() {
  try {
    await markAllNotificationsRead();
    await refresh();
    recent.value = [];
    ElMessage.success("已全部标为已读");
  } catch (e: any) {
    ElMessage.error(e?.message ?? "操作失败");
  }
}

function goAll() {
  router.push("/notifications");
}

onMounted(() => {
  refresh();
  timer = window.setInterval(refresh, 30000);
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});
</script>

<style scoped>
.bell-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.bell-item {
  padding: 8px 0;
  border-bottom: 1px solid var(--zj-line);
}

.bell-item-title {
  font-size: 13px;
  color: var(--zj-ink-900);
}

.bell-item-time {
  font-size: 11px;
  color: var(--zj-ink-400);
  margin-top: 2px;
}

.bell-empty {
  padding: 16px 0;
  text-align: center;
  color: var(--zj-ink-400);
  font-size: 13px;
}

.bell-actions {
  display: flex;
  justify-content: space-between;
  padding-top: 8px;
}
</style>
