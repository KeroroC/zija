<template>
  <div class="members-page">
    <el-card>
      <template #header>
        <span>成员管理</span>
      </template>
      <el-table :data="members" v-loading="loading">
        <el-table-column prop="username" label="用户名" />
        <el-table-column prop="displayName" label="显示名" />
        <el-table-column prop="role" label="角色" />
        <el-table-column prop="status" label="状态" />
        <el-table-column label="操作">
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
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from "vue";
import { ElMessage } from "element-plus";
import { memberApi } from "../api/member";
import { useSessionStore } from "../stores/session";
import type { MemberInfo } from "../types/identity";

const session = useSessionStore();
const members = ref<MemberInfo[]>([]);
const loading = ref(false);

const isOwner = computed(() => session.role === "OWNER");
const isAdmin = computed(() => session.role === "OWNER" || session.role === "ADMIN");

function canManage(row: MemberInfo): boolean {
  if (row.role === "OWNER") return false;
  if (row.accountId === session.currentMember?.accountId) return false;
  if (row.role === "ADMIN") return isOwner.value;
  return isAdmin.value;
}

function canPromote(row: MemberInfo): boolean {
  return isOwner.value && row.role !== "OWNER";
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
    await memberApi.updateStatus(row.id,
      row.status === "ACTIVE" ? "DEACTIVATED" : "ACTIVE");
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

onMounted(load);
</script>

<style scoped>
.members-page {
  max-width: 800px;
  margin: 2rem auto;
}
</style>
