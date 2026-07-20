<template>
  <div class="invitation-page">
    <el-card>
      <h2>加入家庭</h2>
      <template v-if="info?.valid">
        <p>家庭：{{ info.householdName }}</p>
        <p>角色：{{ info.role }}</p>
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
        <p>邀请链接无效或已过期。</p>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { invitationApi } from "../api/invitation";
import { authApi } from "../api/auth";
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
    await invitationApi.redeem(token.value, form);
    await session.ensureInitialized();
    router.push({ name: "home" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.invitation-page {
  max-width: 480px;
  margin: 4rem auto;
}
</style>
