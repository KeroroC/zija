<template>
  <div class="auth-stage">
    <div class="auth-card auth-card-wide">
      <div class="auth-brand">
        <div class="auth-brand-cn">知家</div>
        <div class="auth-brand-en">HOUSEHOLD LEDGER</div>
        <div class="auth-brand-rule"></div>
      </div>
      <h2 class="auth-title">初始化你的家庭</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item v-if="setupTokenRequired" label="初始化口令">
          <el-input
            v-model="setupToken"
            type="password"
            required
            show-password
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item label="家庭名称">
          <el-input v-model="form.householdName" required />
        </el-form-item>
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
        <el-button type="primary" :loading="loading" @click="submit">
          创建家庭
        </el-button>
      </el-form>
    </div>
    <p class="auth-foot">知家 · 让每一件物品都有迹可循</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { householdApi } from "../api/household";
import { authApi } from "../api/auth";
import { ApiError } from "../api/http";
import { useSessionStore } from "../stores/session";
import type { BootstrapRequest } from "../types/identity";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const setupTokenRequired = ref(false);
const setupToken = ref("");
const form = reactive<BootstrapRequest>({
  householdName: "",
  username: "",
  password: "",
  displayName: "",
  email: ""
});

onMounted(async () => {
  try {
    const status = await householdApi.getStatus();
    setupTokenRequired.value = status.setupTokenRequired;
  } catch {
    setupTokenRequired.value = false;
  }
});

async function submit() {
  loading.value = true;
  try {
    await authApi.initializeCsrf();
    const sessionInfo = await householdApi.bootstrap(
      form,
      setupTokenRequired.value ? setupToken.value : undefined
    );
    session.householdInitialized = true;
    await session.applySession(sessionInfo);
    router.push({ name: "home" });
  } catch (e) {
    if (e instanceof ApiError && e.fieldErrors) {
      const labels: Record<string, string> = {
        householdName: "家庭名称", username: "用户名", password: "密码",
        displayName: "显示名", email: "邮箱"
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
</style>
