<template>
  <div class="login-page">
    <el-card>
      <h2>登录</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名">
          <el-input v-model="form.username" required />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" required show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter, useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const route = useRoute();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ username: "", password: "" });

async function submit() {
  loading.value = true;
  try {
    await session.login(form.username, form.password);
    const redirect = route.query.redirect as string | undefined;
    router.push(redirect ?? { name: "home" });
  } catch (e: unknown) {
    const err = e as { errorCode?: string; message?: string };
    if (err.errorCode === "AUTH_LOGIN_RATE_LIMITED") {
      ElMessage.error("尝试过多，请稍后再试");
    } else {
      ElMessage.error("用户名或密码错误");
    }
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-page {
  max-width: 400px;
  margin: 4rem auto;
}
</style>
