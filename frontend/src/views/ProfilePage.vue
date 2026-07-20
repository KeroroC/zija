<template>
  <div class="profile-page">
    <el-card>
      <h2>修改密码</h2>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="当前密码">
          <el-input v-model="form.currentPassword" type="password" required show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" required show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">修改密码</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { authApi } from "../api/auth";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const form = reactive({ currentPassword: "", newPassword: "" });

async function submit() {
  loading.value = true;
  try {
    await authApi.changePassword(form);
    ElMessage.success("密码已修改，请重新登录");
    await session.logout();
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 400px;
  margin: 4rem auto;
}
</style>
