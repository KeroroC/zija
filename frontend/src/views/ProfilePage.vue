<template>
  <div class="page-container-narrow">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">管理你的个人信息与账户安全。</p>
      </div>
    </div>

    <el-card>
      <template #header>个人信息</template>
      <div class="info-row">
        <span class="info-label">用户名</span>
        <span class="info-value">{{ username }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">角色</span>
        <span class="zj-badge zj-badge-pine">{{ roleLabel }}</span>
      </div>
      <div class="info-row">
        <span class="info-label">显示名</span>
        <div class="name-edit">
          <el-input v-model="profile.displayName" maxlength="100" @keyup.enter="saveDisplayName" />
          <el-button type="primary" :loading="savingName" @click="saveDisplayName">保存</el-button>
        </div>
      </div>
    </el-card>

    <el-card>
      <h2 class="auth-title">修改密码</h2>
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
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { authApi } from "../api/auth";
import { useSessionStore } from "../stores/session";

const router = useRouter();
const session = useSessionStore();
const loading = ref(false);
const savingName = ref(false);
const form = reactive({ currentPassword: "", newPassword: "" });
const profile = reactive({ displayName: "" });

const username = computed(() => session.currentMember?.username ?? "-");
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

function syncDisplayName() {
  profile.displayName = session.currentMember?.displayName ?? "";
}
syncDisplayName();

async function saveDisplayName() {
  const name = profile.displayName.trim();
  if (!name) {
    ElMessage.error("显示名不能为空");
    return;
  }
  savingName.value = true;
  try {
    await authApi.updateDisplayName({ displayName: name });
    await session.refreshCurrentMember();
    syncDisplayName();
    ElMessage.success("显示名已更新");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    savingName.value = false;
  }
}

async function submit() {
  loading.value = true;
  try {
    await authApi.changePassword(form);
    ElMessage.success("密码已修改，请重新登录");
    session.clearLocalSession();
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  gap: 16px;
}
.info-label {
  color: var(--zj-ink-400);
  font-size: 13px;
  flex-shrink: 0;
}
.info-value {
  color: var(--zj-ink-600);
  font-size: 13px;
}
.name-edit {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  max-width: 280px;
}
</style>
