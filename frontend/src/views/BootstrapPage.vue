<template>
  <div class="bootstrap-page">
    <el-card>
      <h2>初始化你的家庭</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
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
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { householdApi } from "../api/household";
import { authApi } from "../api/auth";
import { useSessionStore } from "../stores/session";
import type { BootstrapRequest } from "../types/identity";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive<BootstrapRequest>({
  householdName: "",
  username: "",
  password: "",
  displayName: "",
  email: ""
});

async function submit() {
  loading.value = true;
  try {
    await authApi.initializeCsrf();
    const sessionInfo = await householdApi.bootstrap(form);
    session.householdInitialized = true;
    await session.applySession(sessionInfo);
    router.push({ name: "home" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.bootstrap-page {
  max-width: 480px;
  margin: 4rem auto;
}
</style>
