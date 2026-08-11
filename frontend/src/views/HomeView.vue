<template>
  <div class="page-container">
    <header class="page-header">
      <div>
        <h2 class="page-title">首页</h2>
        <p class="page-subtitle">任务与风险</p>
      </div>
    </header>

    <el-row :gutter="24" class="risk-cards">
      <el-col :span="8">
        <div class="page-card risk-card" @click="goReminders('EXPIRY')">
          <el-skeleton v-if="loading" :rows="2" animated />
          <template v-else>
            <div class="risk-num">{{ expiryCount }}</div>
            <div class="risk-label">7 天内到期</div>
            <div class="risk-link">查看清单</div>
          </template>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="page-card risk-card" @click="goReminders('LOW_STOCK')">
          <el-skeleton v-if="loading" :rows="2" animated />
          <template v-else>
            <div class="risk-num">{{ lowStockCount }}</div>
            <div class="risk-label">低库存物品</div>
            <div class="risk-link">查看清单</div>
          </template>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="page-card risk-card" @click="goStocktakes">
          <el-skeleton v-if="loading" :rows="2" animated />
          <template v-else>
            <div class="risk-num">{{ stocktakeCount }}</div>
            <div class="risk-label">待盘点</div>
            <div class="risk-link">前往盘点</div>
          </template>
        </div>
      </el-col>
    </el-row>

    <section class="page-card priority-section">
      <div class="section-title">优先处理任务</div>
      <div
        v-for="t in priorityTasks"
        :key="t.taskId"
        class="priority-row"
      >
        <span class="zj-dot" :class="dotClass(t.severity)"></span>
        <span class="priority-title">{{ t.title }}</span>
        <span class="priority-due">{{ formatDate(t.dueAt) }}</span>
        <el-dropdown trigger="click" @command="(c: string) => onTaskAction(c, t.taskId)">
          <el-button text size="small">操作</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="snooze">稍后提醒</el-dropdown-item>
              <el-dropdown-item command="complete">完成</el-dropdown-item>
              <el-dropdown-item command="ignore">忽略</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
      <div v-if="priorityTasks.length === 0" class="empty">暂无优先任务</div>
    </section>

    <section class="page-card quick-actions">
      <div class="section-title">快速操作</div>
      <el-button @click="goInventory('inbound')">入库</el-button>
      <el-button @click="goInventory('consume')">领用</el-button>
      <el-button @click="goInventory('stocktake')">盘点</el-button>
      <el-button @click="goInventory('transfer')">移位</el-button>
    </section>

    <section class="page-card recent-section">
      <div class="section-title">最近库存流水</div>
      <el-table :data="recentMovements" size="small">
        <el-table-column prop="businessTime" label="时间" />
        <el-table-column label="类型">
          <template #default="{ row }">{{ moveTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" align="right" />
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import {
  fetchDashboard,
  snoozeTask,
  completeTask,
  ignoreTask,
} from "../api/reminder";
import type { DashboardItem } from "../types/reminder";
import type { Movement } from "../types/inventory";
import { fetchStocktakes, fetchMovements } from "../api/inventory";
import { ApiError } from "../api/http";

const router = useRouter();

const loading = ref(true);
const expiryCount = ref(0);
const lowStockCount = ref(0);
const stocktakeCount = ref(0);
const priorityTasks = ref<DashboardItem[]>([]);
const recentMovements = ref<Movement[]>([]);

onMounted(async () => {
  try {
    const [dash, st, mv] = await Promise.all([
      fetchDashboard(7, 8),
      fetchStocktakes({ status: "DRAFT", page: 1, pageSize: 1 }),
      fetchMovements({ page: 1, pageSize: 10 }),
    ]);
    expiryCount.value = dash.expiryWithin7Days.count;
    lowStockCount.value = dash.lowStockItems.count;
    priorityTasks.value = dash.priorityTasks.items;
    stocktakeCount.value = st.total;
    recentMovements.value = mv.items;
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  } finally {
    loading.value = false;
  }
});

function goReminders(kind: string) {
  router.push(`/reminders?kind=${kind}`);
}

function goStocktakes() {
  router.push("/inventory?tab=stocktakes&status=DRAFT");
}

function goInventory(action: string) {
  router.push({ name: "inventory", query: { action } });
}

function dotClass(sev: string) {
  if (sev === "URGENT") return "zj-dot-danger";
  if (sev === "WARN") return "zj-dot-warn";
  return "zj-dot-pine";
}

function formatDate(s: string) {
  try {
    return new Date(s).toLocaleDateString("zh-CN");
  } catch {
    return s;
  }
}

function moveTypeLabel(t: string) {
  return (
    (
      {
        INBOUND: "入库",
        CONSUME: "领用",
        LOSS: "报损",
        ADJUSTMENT: "盘点调整",
        TRANSFER: "移位",
        REVERSAL: "冲正",
      } as Record<string, string>
    )[t] ?? t
  );
}

async function onTaskAction(cmd: string, taskId: string) {
  try {
    if (cmd === "snooze") {
      const until = new Date(Date.now() + 3_600_000).toISOString();
      await snoozeTask(taskId, until);
    } else if (cmd === "complete") {
      await completeTask(taskId);
    } else if (cmd === "ignore") {
      await ignoreTask(taskId);
    }
    ElMessage.success("已处理");
    const dash = await fetchDashboard(7, 8);
    priorityTasks.value = dash.priorityTasks.items;
  } catch (e) {
    if (e instanceof ApiError) ElMessage.error(e.message);
  }
}
</script>

<style scoped>
.page-card {
  background: var(--zj-surface);
  border-radius: var(--zj-radius-md);
  padding: 24px;
  box-shadow: var(--zj-shadow-sm);
}

.risk-cards {
  margin-bottom: 24px;
}

.risk-card {
  cursor: pointer;
  text-align: center;
  transition: box-shadow var(--zj-dur-fast) var(--zj-ease-out);
}

.risk-card:hover {
  box-shadow: var(--zj-shadow-md);
}

.risk-num {
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  color: var(--zj-pine-600);
}

.risk-label {
  margin-top: 8px;
  color: var(--zj-ink-600);
  font-size: 13px;
}

.risk-link {
  margin-top: 8px;
  color: var(--zj-pine-600);
  font-size: 12px;
}

.priority-section,
.quick-actions,
.recent-section {
  margin-bottom: 24px;
}

.section-title {
  font-size: 14px;
  color: var(--zj-ink-900);
  margin-bottom: 16px;
  font-weight: 600;
}

.priority-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--zj-line);
}

.priority-row:last-child {
  border-bottom: none;
}

.priority-title {
  flex: 1;
  color: var(--zj-ink-900);
  font-size: 13px;
}

.priority-due {
  color: var(--zj-ink-400);
  font-size: 12px;
}

.empty {
  padding: 24px 0;
  text-align: center;
  color: var(--zj-ink-400);
}
</style>
