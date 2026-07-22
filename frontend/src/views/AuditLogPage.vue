<template>
  <div class="audit-page">
    <div class="page-header">
      <div>
        <h2>审计日志</h2>
        <p class="page-subtitle">查看家庭成员的操作记录</p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-select
          v-model="filters.quickTime"
          placeholder="时间范围"
          clearable
          style="width: 150px"
          @change="onQuickTimeChange"
        >
          <el-option label="今天" value="today" />
          <el-option label="最近 7 天" value="7d" />
          <el-option label="最近 30 天" value="30d" />
        </el-select>

        <el-date-picker
          v-model="filters.dateRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ssZ"
          :default-time="defaultTime"
          style="width: 380px"
          @change="onDateRangeChange"
        />

        <el-select
          v-model="filters.action"
          placeholder="操作类型"
          clearable
          style="width: 150px"
          @change="loadData"
        >
          <el-option
            v-for="opt in actionOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <el-select
          v-model="filters.outcome"
          placeholder="结果"
          clearable
          style="width: 100px"
          @change="loadData"
        >
          <el-option
            v-for="opt in outcomeOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>
      </div>
    </div>

    <!-- 日志列表 -->
    <el-table
      :data="logs"
      v-loading="loading"
      class="audit-table"
      :row-class-name="rowClass"
    >
      <el-table-column label="时间" width="180">
        <template #default="{ row }">
          <span class="time-cell">{{ formatTime(row.createdAt) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="操作类型" width="130">
        <template #default="{ row }">
          <span class="action-badge" :class="'action-' + row.outcome.toLowerCase()">
            {{ actionLabel(row.action) }}
          </span>
        </template>
      </el-table-column>

      <el-table-column label="操作人" min-width="120">
        <template #default="{ row }">
          {{ row.actor?.displayName ?? row.actor?.username ?? "—" }}
        </template>
      </el-table-column>

      <el-table-column label="目标成员" min-width="120">
        <template #default="{ row }">
          {{ row.subject?.displayName ?? row.subject?.username ?? "—" }}
        </template>
      </el-table-column>

      <el-table-column label="结果" width="80">
        <template #default="{ row }">
          <span class="outcome-dot" :class="row.outcome === 'SUCCESS' ? 'dot-success' : 'dot-failure'" />
          {{ row.outcome === "SUCCESS" ? "成功" : "失败" }}
        </template>
      </el-table-column>

      <el-table-column label="IP" width="140">
        <template #default="{ row }">
          <span class="ip-cell">{{ row.ipAddress ?? "—" }}</span>
        </template>
      </el-table-column>

      <el-table-column label="关键信息" min-width="160">
        <template #default="{ row }">
          <span class="detail-cell">{{ formatDetail(row.detail) }}</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentPageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="审计详情" size="480px">
      <template v-if="selectedLog">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="时间">{{ formatTime(selectedLog.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="操作类型">{{ actionLabel(selectedLog.action) }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            {{ selectedLog.outcome === "SUCCESS" ? "成功" : "失败" }}
          </el-descriptions-item>
          <el-descriptions-item label="操作人">
            {{ selectedLog.actor?.displayName ?? selectedLog.actor?.username ?? "—" }}
          </el-descriptions-item>
          <el-descriptions-item label="目标成员">
            {{ selectedLog.subject?.displayName ?? selectedLog.subject?.username ?? "—" }}
          </el-descriptions-item>
          <el-descriptions-item label="IP 地址">{{ selectedLog.ipAddress ?? "—" }}</el-descriptions-item>
          <el-descriptions-item label="请求 ID">
            <span class="mono">{{ selectedLog.requestId ?? "—" }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="selectedLog.detail" class="detail-section">
          <h4>详细信息</h4>
          <pre class="detail-json">{{ JSON.stringify(selectedLog.detail, null, 2) }}</pre>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { fetchAuditLogs } from "../api/audit";
import {
  ACTION_LABELS,
  ACTION_OPTIONS,
  OUTCOME_OPTIONS,
} from "../types/audit";
import type { AuditLogItem } from "../types/audit";

const logs = ref<AuditLogItem[]>([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const currentPageSize = ref(20);

const filters = reactive({
  quickTime: "" as string,
  dateRange: null as [string, string] | null,
  action: "",
  outcome: "",
});

const actionOptions = ACTION_OPTIONS;
const outcomeOptions = OUTCOME_OPTIONS;
const defaultTime: [Date, Date] = [new Date(0, 0, 0, 0, 0, 0), new Date(0, 0, 0, 23, 59, 59)];

const drawerVisible = ref(false);
const selectedLog = ref<AuditLogItem | null>(null);

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action;
}

function formatTime(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function formatDetail(detail: Record<string, unknown> | null): string {
  if (!detail) return "—";
  const entries = Object.entries(detail);
  if (entries.length === 0) return "—";
  return entries.map(([k, v]) => `${k}: ${v}`).join(", ");
}

function rowClass({ row }: { row: AuditLogItem }) {
  return row.outcome === "FAILURE" ? "row-failure" : "";
}

function onQuickTimeChange(val: string) {
  filters.dateRange = null;
  if (!val) {
    loadData();
    return;
  }
  const now = new Date();
  let from: Date;
  switch (val) {
    case "today":
      from = new Date(now.getFullYear(), now.getMonth(), now.getDate());
      break;
    case "7d":
      from = new Date(now.getTime() - 7 * 86400000);
      break;
    case "30d":
      from = new Date(now.getTime() - 30 * 86400000);
      break;
    default:
      loadData();
      return;
  }
  filters.dateRange = [from.toISOString(), now.toISOString()];
  loadData();
}

function onDateRangeChange() {
  filters.quickTime = "";
  loadData();
}

function onPageChange(page: number) {
  currentPage.value = page;
  loadData();
}

function onSizeChange(size: number) {
  currentPageSize.value = size;
  currentPage.value = 1;
  loadData();
}

async function loadData() {
  loading.value = true;
  try {
    const result = await fetchAuditLogs({
      page: currentPage.value,
      pageSize: currentPageSize.value,
      from: filters.dateRange?.[0] ?? undefined,
      to: filters.dateRange?.[1] ?? undefined,
      action: filters.action || undefined,
      outcome: filters.outcome || undefined,
    });
    logs.value = result.items;
    total.value = result.total;
  } catch (e) {
    ElMessage.error((e as Error).message);
  } finally {
    loading.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.audit-page {
  max-width: 1100px;
  margin: 2rem auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
}

.page-subtitle {
  margin: 4px 0 0;
  color: #65756f;
  font-size: 14px;
}

.filter-bar {
  margin-bottom: 16px;
  padding: 16px;
  background: #f8faf9;
  border-radius: 10px;
}

.filter-row {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.audit-table {
  border-radius: 12px;
  overflow: hidden;
}

.audit-table :deep(.row-failure) {
  background: #fef2f2;
}

.time-cell {
  font-size: 13px;
  color: #374151;
}

.action-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  font-weight: 500;
}

.action-badge.action-success {
  background: #f0fdf4;
  color: #166534;
}

.action-badge.action-failure {
  background: #fef2f2;
  color: #991b1b;
}

.outcome-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 6px;
  vertical-align: middle;
}

.dot-success {
  background: #22c55e;
}

.dot-failure {
  background: #ef4444;
}

.ip-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}

.detail-cell {
  font-size: 12px;
  color: #6b7280;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.detail-section {
  margin-top: 24px;
}

.detail-section h4 {
  margin: 0 0 12px;
  font-size: 14px;
  color: #374151;
}

.detail-json {
  background: #f3f4f6;
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow-x: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
}
</style>
