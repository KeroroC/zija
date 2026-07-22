<template>
  <div class="login-layout">
    <div class="login-bg">
      <div class="bg-circle c1"></div>
      <div class="bg-circle c2"></div>
      <div class="bg-circle c3"></div>
    </div>
    <div class="login-card">
      <div class="login-title">
        <span class="login-logo">知家</span>
      </div>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item>
          <template #label>
            <span class="login-label">用户名</span>
          </template>
          <el-input v-model="form.username" placeholder="请输入用户名" size="large" required />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="login-label">密码</span>
          </template>
          <el-input v-model="form.password" type="password" placeholder="请输入密码" size="large" show-password required />
        </el-form-item>
        <el-button
          size="large"
          :loading="loading"
          class="login-btn"
          @click="submit"
        >
          登 录
        </el-button>
      </el-form>
    </div>
    <p class="login-footer">家庭物品管理系统 · 让每一件物品都有迹可循</p>
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
    const err = e as { errorCode?: string };
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
.login-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #1a3a32;
  position: relative;
  overflow: hidden;
}
.login-bg {
  position: absolute;
  inset: 0;
}
.bg-circle {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.6;
}
.c1 {
  width: 400px;
  height: 400px;
  background: #264f46;
  top: -100px;
  left: -100px;
}
.c2 {
  width: 500px;
  height: 500px;
  background: #397262;
  bottom: -150px;
  right: -100px;
}
.c3 {
  width: 300px;
  height: 300px;
  background: #4a9a80;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}
.login-card {
  position: relative;
  width: 380px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.08);
  backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.3);
}
.login-title {
  text-align: center;
  margin-bottom: 32px;
}
.login-logo {
  font-size: 36px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.05em;
}
.login-label {
  color: rgba(255, 255, 255, 0.8);
}
.login-card :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.15) inset;
}
.login-card :deep(.el-input__inner) {
  color: #ffffff;
}
.login-card :deep(.el-input__inner::placeholder) {
  color: rgba(255, 255, 255, 0.4);
}
.login-btn {
  width: 100%;
  margin-top: 8px;
  background: #397262;
  border-color: #397262;
  color: #ffffff;
}
.login-btn:hover,
.login-btn:focus {
  background: #4a9a80;
  border-color: #4a9a80;
  color: #ffffff;
}
.login-btn:active {
  background: #2e5f52;
  border-color: #2e5f52;
  color: #ffffff;
}
.login-footer {
  position: relative;
  margin-top: 32px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.4);
}
</style>
