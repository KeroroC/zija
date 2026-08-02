<template>
  <template v-if="!session.authenticated || session.isPublicRoute(route)">
    <router-view />
  </template>
  <el-container v-else class="app-shell">
    <el-aside class="app-sidebar" width="224px">
      <div class="brand">
        <span class="brand-cn">知家</span>
        <span class="brand-en">ZIJA</span>
      </div>
      <el-menu
        router
        class="app-menu"
        :default-active="$route.path"
      >
        <el-menu-item index="/">
          <el-icon><House /></el-icon>
          <span>首页</span>
        </el-menu-item>
        <div class="nav-group-label">物品</div>
        <el-menu-item index="/items">
          <el-icon><Box /></el-icon>
          <span>物品资料</span>
        </el-menu-item>
        <el-menu-item index="/inventory">
          <el-icon><Files /></el-icon>
          <span>库存管理</span>
        </el-menu-item>
        <el-menu-item index="/locations">
          <el-icon><Location /></el-icon>
          <span>位置管理</span>
        </el-menu-item>
        <el-menu-item index="/reminders">
          <el-icon><Bell /></el-icon>
          <span>提醒中心</span>
        </el-menu-item>
        <el-menu-item index="/reports">
          <el-icon><TrendCharts /></el-icon>
          <span>报表与导出</span>
        </el-menu-item>
        <div class="nav-group-label">家庭</div>
        <el-menu-item index="/members">
          <el-icon><User /></el-icon>
          <span>成员管理</span>
        </el-menu-item>
        <el-menu-item index="/audit-logs">
          <el-icon><Document /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
        <el-menu-item index="/profile">
          <el-icon><Postcard /></el-icon>
          <span>个人资料</span>
        </el-menu-item>
        <el-menu-item index="/system">
          <el-icon><Document /></el-icon>
          <span>系统状态</span>
        </el-menu-item>
        <el-menu-item v-if="session.role === 'OWNER' || session.role === 'ADMIN'" index="/settings/catalog">
          <el-icon><Setting /></el-icon>
          <span>家庭设置</span>
        </el-menu-item>
        <el-menu-item v-else index="/settings" disabled>
          <el-icon><Setting /></el-icon>
          <span>家庭设置</span>
        </el-menu-item>
        <el-menu-item v-if="session.role === 'OWNER' || session.role === 'ADMIN'" index="/settings/reminder">
          <el-icon><AlarmClock /></el-icon>
          <span>提醒规则</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header" height="56px">
        <span class="header-context">家庭：{{ householdName }}</span>
        <div class="header-right">
          <el-dropdown trigger="click" @command="onInventoryCommand">
            <el-button size="small" type="primary" plain>
              库存操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="inbound">入库</el-dropdown-item>
                <el-dropdown-item command="consume">领用</el-dropdown-item>
                <el-dropdown-item command="loss">报损</el-dropdown-item>
                <el-dropdown-item command="transfer">移位</el-dropdown-item>
                <el-dropdown-item command="stocktake" divided>发起盘点</el-dropdown-item>
                <el-dropdown-item command="consistency" v-if="isAdmin">一致性检查</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <NotificationBell />
          <el-dropdown trigger="click" @command="onUserCommand">
            <button class="user-trigger" type="button">
              {{ session.currentMember?.displayName || "-" }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人资料</el-dropdown-item>
                <el-dropdown-item command="logout" divided>登出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  House,
  Box,
  Files,
  Location,
  Bell,
  TrendCharts,
  User,
  Document,
  Postcard,
  Setting,
  ArrowDown,
  AlarmClock
} from "@element-plus/icons-vue";
import { useSessionStore } from "../stores/session";
import NotificationBell from "./NotificationBell.vue";

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
// The store already resolves the current member on login and on session
// restore, so the name is reactive and correct without a separate fetch.
const householdName = computed(() => session.currentMember?.householdName || "我的家");

const isAdmin = computed(() => session.role === "OWNER" || session.role === "ADMIN");

async function onLogout() {
  try {
    await ElMessageBox.confirm("确定要登出当前账号吗？", "确认登出", {
      confirmButtonText: "登出",
      cancelButtonText: "取消",
      type: "warning"
    });
  } catch {
    return;
  }
  try {
    await session.logout();
    router.push({ name: "login" });
  } catch {
    ElMessage.error("登出失败，请重试");
  }
}

function onInventoryCommand(command: string) {
  // TODO: wire up inventory operation modals/dialogs
  console.log("inventory command:", command);
}

function onUserCommand(command: string) {
  if (command === "profile") {
    router.push({ name: "profile" });
  } else if (command === "logout") {
    onLogout();
  }
}
</script>

<style scoped>
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-sm);
  background: var(--zj-surface);
  color: var(--zj-ink-600);
  font-size: 13px;
  cursor: pointer;
  transition: border-color var(--zj-dur-fast) var(--zj-ease-out),
              color var(--zj-dur-fast) var(--zj-ease-out);
}
.user-trigger:hover {
  border-color: var(--zj-pine-600);
  color: var(--zj-pine-600);
}
</style>
