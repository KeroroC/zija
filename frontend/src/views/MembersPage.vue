<template>
  <div class="members-page">
    <el-card>
      <template #header>
        <div class="header-row">
          <span>成员管理</span>
          <el-button
            v-if="canInvite"
            type="primary"
            size="small"
            data-testid="create-invite"
            @click="openInvite"
          >
            创建邀请
          </el-button>
        </div>
      </template>

      <el-alert
        v-if="inviteLink"
        type="success"
        :closable="true"
        show-icon
        class="invite-alert"
        @close="inviteLink = ''"
      >
        <template #title>邀请链接（只显示一次）</template>
        <div class="invite-link" data-testid="invite-link">{{ inviteLink }}</div>
        <el-button size="small" @click="copyInvite">复制链接</el-button>
      </el-alert>

      <el-table :data="members" v-loading="loading">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作" min-width="240">
          <template #default="{ row }">
            <el-button
              v-if="canManage(row as MemberInfo)"
              size="small"
              @click="toggleStatus(row as MemberInfo)"
            >
              {{ row.status === "ACTIVE" ? "停用" : "启用" }}
            </el-button>
            <el-button
              v-if="canPromote(row as MemberInfo)"
              size="small"
              @click="toggleRole(row as MemberInfo)"
            >
              {{ row.role === "MEMBER" ? "设为管理员" : "取消管理员" }}
            </el-button>
            <el-button
              v-if="canTransfer(row as MemberInfo)"
              size="small"
              type="warning"
              :data-testid="`transfer-${row.id}`"
              @click="openTransfer(row as MemberInfo)"
            >
              转移所有权
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="inviteVisible" title="创建邀请" width="420px">
      <el-form label-position="top">
        <el-form-item label="角色">
          <el-select v-model="inviteRole" style="width: 100%">
            <el-option label="普通成员" value="MEMBER" />
            <el-option v-if="isOwner" label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="有效期（小时）">
          <el-input-number v-model="expiresInHours" :min="1" :max="168" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inviteVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="inviteLoading"
          data-testid="confirm-invite"
          @click="createInvite"
        >
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="transferVisible" title="转移所有权" width="420px">
      <p>
        将把所有权转移给
        <strong>{{ transferTarget?.displayName }}</strong>
        （{{ transferTarget?.username }}）。你将成为管理员，并需要重新登录。
      </p>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button
          type="warning"
          :loading="transferLoading"
          data-testid="confirm-transfer"
          @click="confirmTransfer"
        >
          确认转移
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { householdApi } from "../api/household";
import { invitationApi } from "../api/invitation";
import { memberApi } from "../api/member";
import { useSessionStore } from "../stores/session";
import type { MemberInfo } from "../types/identity";

const router = useRouter();
const session = useSessionStore();
const members = ref<MemberInfo[]>([]);
const loading = ref(false);

const inviteVisible = ref(false);
const inviteLoading = ref(false);
const inviteRole = ref<"ADMIN" | "MEMBER">("MEMBER");
const expiresInHours = ref(24);
const inviteLink = ref("");

const transferVisible = ref(false);
const transferLoading = ref(false);
const transferTarget = ref<MemberInfo | null>(null);

const isOwner = computed(() => session.role === "OWNER");
const isAdmin = computed(() => session.role === "OWNER" || session.role === "ADMIN");
const canInvite = computed(() => isAdmin.value);

function canManage(row: MemberInfo): boolean {
  if (row.role === "OWNER") return false;
  if (row.accountId === session.currentMember?.accountId) return false;
  if (row.role === "ADMIN") return isOwner.value;
  return isAdmin.value;
}

function canPromote(row: MemberInfo): boolean {
  return isOwner.value && row.role !== "OWNER";
}

function canTransfer(row: MemberInfo): boolean {
  return isOwner.value
    && row.role !== "OWNER"
    && row.status === "ACTIVE"
    && row.accountId !== session.currentMember?.accountId;
}

async function load() {
  loading.value = true;
  try {
    members.value = await memberApi.list();
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

async function toggleStatus(row: MemberInfo) {
  try {
    await memberApi.updateStatus(
      row.id,
      row.status === "ACTIVE" ? "DEACTIVATED" : "ACTIVE"
    );
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

async function toggleRole(row: MemberInfo) {
  try {
    await memberApi.updateRole(row.id, row.role === "MEMBER" ? "ADMIN" : "MEMBER");
    await load();
  } catch (e) {
    ElMessage.error((e as Error).message);
  }
}

function openInvite() {
  inviteRole.value = "MEMBER";
  expiresInHours.value = 24;
  inviteVisible.value = true;
}

async function createInvite() {
  inviteLoading.value = true;
  try {
    const result = await invitationApi.create(inviteRole.value, expiresInHours.value);
    inviteLink.value = `${window.location.origin}${result.path}`;
    inviteVisible.value = false;
    ElMessage.success("邀请已创建");
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    inviteLoading.value = false;
  }
}

async function copyInvite() {
  try {
    await navigator.clipboard.writeText(inviteLink.value);
    ElMessage.success("已复制邀请链接");
  } catch {
    ElMessage.error("复制失败，请手动选择链接");
  }
}

function openTransfer(row: MemberInfo) {
  transferTarget.value = row;
  transferVisible.value = true;
}

async function confirmTransfer() {
  if (!transferTarget.value) return;
  transferLoading.value = true;
  try {
    await householdApi.transferOwnership(transferTarget.value.id);
    transferVisible.value = false;
    session.clearLocalSession();
    ElMessage.success("所有权已转移，请重新登录");
    router.push({ name: "login" });
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    transferLoading.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.members-page {
  max-width: 960px;
  margin: 2rem auto;
}

.header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}

.invite-alert {
  margin-bottom: 1rem;
}

.invite-link {
  word-break: break-all;
  margin: 0.5rem 0;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
