<template>
  <template v-if="!session.authenticated">
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
        <el-menu-item index="/inventory" disabled>
          <el-icon><Files /></el-icon>
          <span>库存管理</span>
        </el-menu-item>
        <el-menu-item index="/locations">
          <el-icon><Location /></el-icon>
          <span>位置管理</span>
        </el-menu-item>
        <el-menu-item index="/reminders" disabled>
          <el-icon><Bell /></el-icon>
          <span>提醒中心</span>
        </el-menu-item>
        <el-menu-item index="/reports" disabled>
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
        <el-menu-item v-if="session.role === 'OWNER' || session.role === 'ADMIN'" index="/settings/catalog">
          <el-icon><Setting /></el-icon>
          <span>家庭设置</span>
        </el-menu-item>
        <el-menu-item v-else index="/settings" disabled>
          <el-icon><Setting /></el-icon>
          <span>家庭设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="app-header" height="56px">
        <span class="header-context">家庭：{{ householdName }}</span>
        <div class="header-right">
          <span class="zj-badge zj-badge-plain">{{ roleLabel }}</span>
          <el-button
            size="small"
            text
            @click="onLogout"
          >
            <el-icon style="margin-right: 4px"><SwitchButton /></el-icon>
            登出
          </el-button>
        </div>
      </el-header>
      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
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
  SwitchButton
} from "@element-plus/icons-vue";
import { useSessionStore } from "../stores/session";
import { householdApi } from "../api/household";

const router = useRouter();
const session = useSessionStore();
const householdName = ref("我的家");

onMounted(async () => {
  try {
    const member = await householdApi.getCurrentMember();
    if (member.householdName) {
      householdName.value = member.householdName;
    }
  } catch {
    // fallback to default
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
    await session.logout();
    router.push({ name: "login" });
  } catch {
    ElMessage.error("登出失败，请重试");
  }
}
</script>
