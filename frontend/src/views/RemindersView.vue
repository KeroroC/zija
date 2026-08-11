<template>
  <div class="page-container">
    <header class="page-header">
      <div>
        <h2 class="page-title">提醒中心</h2>
        <p class="page-subtitle">临期与低库存任务</p>
      </div>
    </header>

    <div class="filter-bar">
      <el-select v-model="filter.kind" placeholder="类型" clearable size="small" @change="reload">
        <el-option label="全部" value="" />
        <el-option label="临期" value="EXPIRY" />
        <el-option label="低库存" value="LOW_STOCK" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable size="small" @change="reload">
        <el-option label="全部" value="" />
        <el-option label="待处理" value="OPEN" />
        <el-option label="已延后" value="SNOOZED" />
        <el-option label="已完成" value="DONE" />
        <el-option label="已忽略" value="IGNORED" />
      </el-select>
      <el-checkbox v-model="filter.overdue" @change="reload">仅过期</el-checkbox>
    </div>

    <el-table :data="tasks" class="tasks-table">
      <el-table-column label="类型">
        <template #default="{ row }">{{ kindLabel(row.kind) }}</template>
      </el-table-column>
      <el-table-column label="紧急度">
        <template #default="{ row }">
          <span class="zj-dot" :class="dotClass(row.severity)"></span>
          {{ severityLabel(row.severity) }}
        </template>
      </el-table-column>
      <el-table-column prop="dueAt" label="到期/评估">
        <template #default="{ row }">{{ formatDate(row.dueAt) }}</template>
      </el-table-column>
      <el-table-column label="状态">
        <template #default="{ row }">
          <span class="zj-dot" :class="statusDotClass(row.status)"></span>{{ statusLabel(row.status) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-dropdown trigger="click" @command="(c: string) => onAction(c, row as ReminderTask)">
            <el-button text size="small">操作</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="row.status === 'OPEN' || row.status === 'SNOOZED'" command="snooze">
                  稍后提醒
                </el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'OPEN' || row.status === 'SNOOZED'" command="complete">
                  完成
                </el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'OPEN' || row.status === 'SNOOZED'" command="ignore">
                  忽略
                </el-dropdown-item>
                <el-dropdown-item v-if="row.status === 'DONE' || row.status === 'IGNORED'" command="reopen">
                  重新打开
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      :total="total"
      :current-page="filter.page"
      :page-size="filter.pageSize"
      layout="prev, pager, next"
      @current-change="onPage"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import {
  fetchTasks,
  snoozeTask,
  completeTask,
  ignoreTask,
  reopenTask,
  type ReminderTask,
} from "../api/reminder";
import { ApiError } from "../api/http";

const route = useRoute();
const tasks = ref<ReminderTask[]>([]);
const total = ref(0);
const filter = reactive({
  kind: (route.query.kind as string) ?? "",
  status: "",
  overdue: false,
  page: 1,
  pageSize: 20,
});

onMounted(reload);

async function reload() {
  const p: Record<string, string | number | boolean> = {};
  if (filter.kind) p.kind = filter.kind;
  if (filter.status) p.status = filter.status;
  if (filter.overdue) p.overdue = true;
  p.page = filter.page;
  p.pageSize = filter.pageSize;

  try {
    const r = await fetchTasks(p);
    tasks.value = r.items;
    total.value = r.total;
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

function onPage(pg: number) {
  filter.page = pg;
  reload();
}

async function onAction(cmd: string, row: ReminderTask) {
  try {
    if (cmd === "snooze") {
      const picked = await ElMessageBox.prompt("延后至：", "稍后提醒", {
        inputType: "datetime",
      });
      if (picked.value) {
        await snoozeTask(row.id, new Date(picked.value).toISOString());
      }
    } else if (cmd === "complete") {
      await completeTask(row.id);
    } else if (cmd === "ignore") {
      await ignoreTask(row.id);
    } else if (cmd === "reopen") {
      await reopenTask(row.id);
    }
    ElMessage.success("已处理");
    reload();
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}

function kindLabel(k: string) {
  return k === "EXPIRY" ? "临期" : "低库存";
}

function severityLabel(s: string) {
  return s === "URGENT" ? "紧急" : s === "WARN" ? "警告" : "提示";
}

function statusLabel(s: string) {
  return (
    ({ OPEN: "待处理", SNOOZED: "已延后", DONE: "已完成", IGNORED: "已忽略" } as Record<string, string>)[s] ?? s
  );
}

function dotClass(s: string) {
  return s === "URGENT" ? "zj-dot-danger" : s === "WARN" ? "zj-dot-warn" : "zj-dot-pine";
}

function statusDotClass(s: string) {
  return s === "DONE" ? "zj-dot-pine" : s === "IGNORED" ? "zj-dot-off" : "zj-dot-warn";
}

function formatDate(s: string) {
  try {
    return new Date(s).toLocaleString("zh-CN");
  } catch {
    return s;
  }
}
</script>

<style scoped>
.filter-bar {
  background: var(--zj-surface-sunken);
  padding: 12px 16px;
  border-radius: var(--zj-radius-sm);
  margin-bottom: 16px;
  display: flex;
  gap: 12px;
  align-items: center;
}

.tasks-table {
  background: var(--zj-surface);
}

:deep(.tasks-table th.el-table__cell) {
  background: var(--zj-surface);
}
</style>
