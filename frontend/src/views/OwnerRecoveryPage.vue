<template>
  <div class="recovery-page">
    <el-card>
      <h2>重置所有者密码</h2>
      <template v-if="info?.valid">
        <p>请为所有者账户设置新密码。</p>
        <el-form :model="form" label-position="top" @submit.prevent="reset">
          <el-form-item label="新密码">
            <el-input v-model="form.newPassword" type="password" required show-password />
          </el-form-item>
          <el-form-item label="确认新密码">
            <el-input v-model="confirmPassword" type="password" required show-password />
          </el-form-item>
          <el-button type="primary" :loading="loading" :disabled="!passwordsMatch" @click="reset">
            重置密码
          </el-button>
        </el-form>
      </template>
      <template v-else-if="info !== null">
        <p>恢复链接无效或已过期。</p>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ownerRecoveryApi } from "../api/owner-recovery";
import { authApi } from "../api/auth";
import type { OwnerRecoveryInspect } from "../types/identity";

const router = useRouter();
const info = ref<OwnerRecoveryInspect | null>(null);
const loading = ref(false);
const token = ref("");
const form = reactive({ newPassword: "" });
const confirmPassword = ref("");

const passwordsMatch = computed(
  () => form.newPassword.length > 0 && form.newPassword === confirmPassword.value
);

onMounted(async () => {
  const hash = window.location.hash;
  const match = hash.match(/token=([^&]+)/);
  if (!match) {
    info.value = { valid: false };
    return;
  }
  window.history.replaceState(null, "", window.location.pathname);
  try {
    token.value = decodeURIComponent(match[1]);
  } catch {
    info.value = { valid: false };
    return;
  }

  try {
    await authApi.initializeCsrf();
    info.value = await ownerRecoveryApi.inspect(token.value);
  } catch {
    info.value = { valid: false };
  }
});

async function reset() {
  if (!passwordsMatch.value) return;

  loading.value = true;
  try {
    await ownerRecoveryApi.resetPassword({ token: token.value, newPassword: form.newPassword });
    ElMessage.success("密码已重置，请使用新密码登录");
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.recovery-page {
  max-width: 400px;
  margin: 4rem auto;
}
</style>
