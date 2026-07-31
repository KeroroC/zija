<template>
  <div class="auth-stage">
    <div class="auth-card auth-card-wide">
      <div class="auth-brand">
        <div class="auth-brand-cn">知家</div>
        <div class="auth-brand-en">HOUSEHOLD LEDGER</div>
        <div class="auth-brand-rule"></div>
      </div>
      <h2 class="auth-title">加入家庭</h2>
      <template v-if="info?.valid">
        <p class="invite-meta">家庭：{{ info.householdName }}</p>
        <p class="invite-meta">角色：{{ info.role }}</p>
        <el-form :model="form" label-position="top" @submit.prevent="redeem">
          <el-form-item label="用户名">
            <el-input v-model="form.username" required />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="form.password" type="password" required show-password />
          </el-form-item>
          <el-form-item label="显示名">
            <el-input v-model="form.displayName" required />
          </el-form-item>
          <el-form-item label="邮箱（可选）">
            <el-input v-model="form.email" type="email" />
          </el-form-item>
          <el-button type="primary" :loading="loading" @click="redeem">加入</el-button>
        </el-form>
      </template>
      <template v-else>
        <p class="invite-meta">邀请链接无效或已过期。</p>
      </template>
    </div>
    <p class="auth-foot">家庭物品管理系统 · 让每一件物品都有迹可循</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { invitationApi } from "../api/invitation";
import { authApi } from "../api/auth";
import { ApiError } from "../api/http";
import { useSessionStore } from "../stores/session";
import type { InvitationInspect } from "../types/identity";

const router = useRouter();
const session = useSessionStore();
const info = ref<InvitationInspect | null>(null);
const loading = ref(false);
const token = ref("");
const form = reactive({
  username: "",
  password: "",
  displayName: "",
  email: ""
});

onMounted(async () => {
  const hash = window.location.hash;
  const match = hash.match(/token=([^&]+)/);
  if (!match) {
    info.value = { valid: false };
    return;
  }
  token.value = decodeURIComponent(match[1]);
  window.history.replaceState(null, "", window.location.pathname);

  try {
    await authApi.initializeCsrf();
    info.value = await invitationApi.inspect(token.value);
  } catch {
    info.value = { valid: false };
  }
});

async function redeem() {
  loading.value = true;
  try {
    await authApi.initializeCsrf();
    const sessionInfo = await invitationApi.redeem(token.value, form);
    await session.applySession(sessionInfo);
    router.push({ name: "home" });
  } catch (e) {
    if (e instanceof ApiError && e.fieldErrors) {
      const labels: Record<string, string> = {
        username: "用户名", password: "密码", displayName: "显示名", email: "邮箱"
      };
      const msg = Object.entries(e.fieldErrors)
        .map(([k, v]) => `${labels[k] ?? k}：${v}`)
        .join("；");
      ElMessage.error(msg);
    } else {
      ElMessage.error((e as Error).message);
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.invite-meta {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--zj-ink-600);
}
</style>
