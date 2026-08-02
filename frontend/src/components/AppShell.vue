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
          <el-dropdown trigger="click" placement="bottom-end" @command="onUserCommand">
            <button class="user-trigger" type="button" :title="displayName">
              <span class="user-initial" :class="`user-initial-${initialTone}`" aria-hidden="true">
                {{ initial }}
              </span>
              <span class="user-name">{{ displayName }}</span>
              <el-icon class="user-caret"><ArrowDown /></el-icon>
            </button>
            <template #dropdown>
              <div class="user-menu">
                <header class="user-menu-header">
                  <span class="user-menu-name">{{ displayName }}</span>
                  <span class="user-menu-meta">
                    <span class="user-menu-role">{{ roleLabel }}</span>
                    <span class="user-menu-sep" aria-hidden="true">·</span>
                    <span class="user-menu-household">{{ householdName }}</span>
                  </span>
                </header>
                <el-dropdown-menu class="user-menu-list">
                  <el-dropdown-item command="profile">
                    <el-icon class="el-icon--left"><User /></el-icon>个人资料
                  </el-dropdown-item>
                  <el-dropdown-item command="logout" divided class="user-menu-item-danger">
                    <el-icon class="el-icon--left"><SwitchButton /></el-icon>登出
                  </el-dropdown-item>
                </el-dropdown-menu>
              </div>
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
  AlarmClock,
  SwitchButton
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

const displayName = computed(
  () => session.currentMember?.displayName?.trim() || session.currentMember?.username || "-"
);

// Single Chinese-character initial; fall back to the first letter for ASCII
// displayNames ("Admin" → "A") so non-CJK households still get a meaningful glyph.
const initial = computed(() => {
  const name = displayName.value;
  if (!name || name === "-") return "·";
  return Array.from(name)[0] ?? "·";
});

// Tone buckets match the role. Owners get the deepest pine, members the
// mid-tone, so the trigger's accent rings consistent with the rest of the
// shell but distinguishes power-users at a glance.
const initialTone = computed(() => {
  switch (session.role) {
    case "OWNER":
      return "deep";
    case "ADMIN":
      return "mid";
    default:
      return "soft";
  }
});

const roleLabel = computed(() => {
  switch (session.role) {
    case "OWNER":
      return "所有者";
    case "ADMIN":
      return "管理员";
    case "MEMBER":
      return "成员";
    default:
      return "访客";
  }
});

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
/* 身份药丸：左侧首字母圆形 + 显示名 + 折叠箭头，hover 整圈描边变松绿 */
.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 36px;
  padding: 0 10px 0 6px;
  border: 1px solid var(--zj-line);
  border-radius: 999px;
  background: var(--zj-surface);
  color: var(--zj-ink-900);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--zj-dur-fast) var(--zj-ease-out),
    background-color var(--zj-dur-fast) var(--zj-ease-out),
    color var(--zj-dur-fast) var(--zj-ease-out);
}

.user-trigger:hover {
  border-color: var(--zj-pine-500);
  background: var(--zj-pine-50);
}

.user-trigger:focus-visible {
  outline: 2px solid rgba(61, 114, 96, 0.55);
  outline-offset: 2px;
}

/* 首字母圆形：与角色挂钩的色阶，从深松绿到雾松 */
.user-initial {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  font-family: var(--zj-serif);
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: #f4f7f3;
  flex-shrink: 0;
}

.user-initial-deep {
  background: var(--zj-pine-800);
}

.user-initial-mid {
  background: var(--zj-pine-600);
}

.user-initial-soft {
  background: var(--zj-pine-500);
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--zj-ink-900);
}

.user-caret {
  font-size: 12px;
  color: var(--zj-ink-400);
  transition: transform var(--zj-dur-fast) var(--zj-ease-out),
              color var(--zj-dur-fast) var(--zj-ease-out);
}

.user-trigger:hover .user-caret {
  color: var(--zj-pine-600);
}

/* Element Plus 把 .user-menu 挂在 body 上（teleport），所以弹窗内部样式统一在 index.css 的 .user-menu-* 里。 */
</style>
