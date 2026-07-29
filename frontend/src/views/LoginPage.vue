<template>
  <div class="auth-stage">
    <div class="auth-card">
      <div class="auth-brand">
        <div class="auth-brand-cn">知家</div>
        <div class="auth-brand-en">HOUSEHOLD LEDGER</div>
        <div class="auth-brand-rule"></div>
      </div>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item>
          <template #label>
            <span class="login-label">用户名</span>
          </template>
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            autocomplete="username"
            required
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-form-item>
          <template #label>
            <span class="login-label">密码</span>
          </template>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            autocomplete="current-password"
            show-password
            required
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="login-btn"
          @click="submit"
        >
          登录
        </el-button>
      </el-form>
    </div>
    <p class="auth-foot">家庭物品管理系统 · 让每一件物品都有迹可循</p>
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
  if (loading.value) return;
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
.login-label {
  font-size: 13px;
  font-weight: 500;
}
.login-btn {
  width: 100%;
  margin-top: 8px;
  letter-spacing: 0.3em;
  text-indent: 0.3em;
}
</style>
