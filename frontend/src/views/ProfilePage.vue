<template>
  <div class="page-container-narrow">
    <div class="page-header">
      <div>
        <h1 class="page-title">个人资料</h1>
        <p class="page-subtitle">管理你的基本信息与账户安全。</p>
      </div>
    </div>

    <section class="profile-card" aria-labelledby="profile-identity-title">
      <header class="profile-card-header">
        <h2 id="profile-identity-title" class="profile-card-title">身份信息</h2>
        <p class="profile-card-hint">显示名将出现在界面右上角和成员列表中。</p>
      </header>

      <dl class="prop-list">
        <div class="prop">
          <dt class="prop-label">用户名</dt>
          <dd class="prop-value prop-value-mono">{{ username }}</dd>
        </div>
        <div class="prop">
          <dt class="prop-label">角色</dt>
          <dd class="prop-value">
            <span class="zj-badge zj-badge-pine">{{ roleLabel }}</span>
          </dd>
        </div>
        <div class="prop prop-stretch">
          <dt class="prop-label">显示名</dt>
          <dd class="prop-value">
            <div class="name-edit">
              <el-input v-model="profile.displayName" maxlength="100" @keyup.enter="saveDisplayName" />
              <el-button type="primary" :loading="savingName" @click="saveDisplayName">保存</el-button>
            </div>
          </dd>
        </div>
      </dl>
    </section>

    <section class="profile-card" aria-labelledby="profile-password-title">
      <header class="profile-card-header">
        <h2 id="profile-password-title" class="profile-card-title">修改密码</h2>
        <p class="profile-card-hint">修改成功后你需要重新登录一次。</p>
      </header>
      <el-form :model="form" label-position="top" class="profile-form" @submit.prevent="submit">
        <el-form-item label="当前密码">
          <el-input v-model="form.currentPassword" type="password" required show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" required show-password />
        </el-form-item>
        <el-button type="primary" :loading="loading" @click="submit">修改密码</el-button>
      </el-form>
    </section>
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
  if (savingName.value) {
    return;
  }
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
/* 主区上移，避免表单卡片贴顶。spec §5.7 */
.page-container-narrow {
  padding-top: 48px;
}

/* 实色卡片：和 auth-card 一致的气质，去掉 el-card 的厚边框和默认内阴影 */
.profile-card {
  margin-bottom: 24px;
  padding: 28px 28px 24px;
  background: var(--zj-surface);
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-md);
  box-shadow: var(--zj-shadow-sm);
}

.profile-card:last-child {
  margin-bottom: 0;
}

.profile-card-header {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--zj-line);
}

.profile-card-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--zj-ink-900);
}

.profile-card-hint {
  margin: 6px 0 0;
  font-size: 12.5px;
  color: var(--zj-ink-600);
}

/* label/value 双列：左 100px 标签，右柔性值，行为发丝线分隔 */
.prop-list {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 0;
}

.prop {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 0;
  border-bottom: 1px solid var(--zj-line);
}

.prop:last-child {
  border-bottom: 0;
}

.prop-stretch {
  align-items: flex-start;
}

.prop-label {
  flex-shrink: 0;
  width: 88px;
  margin: 0;
  font-size: 13px;
  color: var(--zj-ink-400);
  letter-spacing: 0.02em;
}

.prop-value {
  flex: 1;
  margin: 0;
  font-size: 14px;
  color: var(--zj-ink-900);
  text-align: right;
}

.prop-stretch .prop-value {
  text-align: left;
}

.prop-value-mono {
  font-family: var(--zj-mono);
  font-size: 12.5px;
  letter-spacing: 0.02em;
  color: var(--zj-ink-600);
}

/* 显示名编辑：保证测试能用 .name-edit 选择器命中（input + 按钮） */
.name-edit {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  max-width: 320px;
}

.name-edit :deep(.el-input) {
  flex: 1;
}

/* 密码表单：行内表单，标签居顶，避免双层 margin */
.profile-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.profile-form :deep(.el-form-item:last-child) {
  margin-bottom: 0;
}
</style>
