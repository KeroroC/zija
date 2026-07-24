<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">审计日志</h2>
        <p class="page-subtitle">查看家庭成员的操作记录</p>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="tl-filters">
      <el-select v-model="filters.quickTime" placeholder="时间" clearable size="small" style="width: 120px" @change="onQuickTimeChange">
        <el-option label="今天" value="today" />
        <el-option label="7 天" value="7d" />
        <el-option label="30 天" value="30d" />
      </el-select>
      <el-select v-model="filters.action" placeholder="操作类型" clearable size="small" style="width: 130px" @change="loadData">
        <el-option v-for="opt in actionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <el-select v-model="filters.outcome" placeholder="结果" clearable size="small" style="width: 90px" @change="loadData">
        <el-option v-for="opt in outcomeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
      </el-select>
      <span class="tl-count">共 {{ total }} 条</span>
    </div>

    <!-- 时间线 -->
    <div v-loading="loading" class="tl-track">
      <div v-for="(group, gi) in groupedLogs" :key="gi" class="tl-day-group">
        <div class="tl-day-label">
          <span class="tl-day-dot" />
          <span class="tl-day-text">{{ group.label }}</span>
        </div>
        <div v-for="log in group.items" :key="log.id" class="tl-event" @click="openDrawer(log)">
          <div class="tl-line" />
          <div class="tl-node" :class="log.outcome === 'SUCCESS' ? 'node-success' : 'node-failure'" />
          <div class="tl-card" :class="{ 'card-failure': log.outcome === 'FAILURE' }">
            <div class="tl-card-head">
              <span class="tl-action" :class="'tl-' + log.outcome.toLowerCase()">{{ actionLabel(log.action) }}</span>
              <span class="tl-meta">
                <span class="tl-actor">{{ log.actor?.displayName ?? log.actor?.username ?? "系统" }}</span>
                <span v-if="log.subject" class="tl-arrow">→</span>
                <span v-if="log.subject" class="tl-subject">{{ log.subject?.displayName ?? log.subject?.username }}</span>
              </span>
              <span v-if="log.ipAddress" class="tl-ip">{{ log.ipAddress }}</span>
              <span v-if="log.detail && Object.keys(log.detail).length" class="tl-detail">{{ formatDetail(log.detail) }}</span>
              <span class="tl-time">{{ formatTime(log.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-if="!loading && logs.length === 0" class="tl-empty">暂无审计记录</div>
    </div>

    <!-- 分页 -->
    <div class="tl-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="currentPageSize"
        :total="total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next"
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
import { ref, reactive, computed, onMounted } from "vue";
import { ElMessage } from "element-plus";
import { fetchAuditLogs } from "../api/audit";
import { ACTION_LABELS, ACTION_OPTIONS, OUTCOME_OPTIONS } from "../types/audit";
import type { AuditLogItem } from "../types/audit";

const logs = ref<AuditLogItem[]>([]);
const loading = ref(false);
const total = ref(0);
const currentPage = ref(1);
const currentPageSize = ref(20);

const filters = reactive({
  quickTime: "",
  dateRange: null as [string, string] | null,
  action: "",
  outcome: "",
});

const actionOptions = ACTION_OPTIONS;
const outcomeOptions = OUTCOME_OPTIONS;

const drawerVisible = ref(false);
const selectedLog = ref<AuditLogItem | null>(null);

function openDrawer(log: AuditLogItem) {
  selectedLog.value = log;
  drawerVisible.value = true;
}

function actionLabel(action: string) { return ACTION_LABELS[action] ?? action; }

function formatTime(iso: string) {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function formatDay(iso: string) {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatDetail(detail: Record<string, unknown> | null) {
  if (!detail) return "";
  return Object.entries(detail).map(([k, v]) => `${k}: ${v}`).join(" · ");
}

const groupedLogs = computed(() => {
  const groups: { label: string; items: AuditLogItem[] }[] = [];
  for (const log of logs.value) {
    const day = formatDay(log.createdAt);
    const last = groups[groups.length - 1];
    if (last && last.label === day) {
      last.items.push(log);
    } else {
      groups.push({ label: day, items: [log] });
    }
  }
  return groups;
});

function onQuickTimeChange(val: string) {
  filters.dateRange = null;
  if (!val) { loadData(); return; }
  const now = new Date();
  let from: Date;
  if (val === "today") from = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  else if (val === "7d") from = new Date(now.getTime() - 7 * 86400000);
  else if (val === "30d") from = new Date(now.getTime() - 30 * 86400000);
  else { loadData(); return; }
  filters.dateRange = [from.toISOString(), now.toISOString()];
  loadData();
}

function onPageChange(page: number) { currentPage.value = page; loadData(); }
function onSizeChange(size: number) { currentPageSize.value = size; currentPage.value = 1; loadData(); }

async function loadData() {
  loading.value = true;
  try {
    const result = await fetchAuditLogs({
      page: currentPage.value, pageSize: currentPageSize.value,
      from: filters.dateRange?.[0], to: filters.dateRange?.[1],
      action: filters.action || undefined, outcome: filters.outcome || undefined,
    });
    logs.value = result.items;
    total.value = result.total;
  } catch (e) { ElMessage.error((e as Error).message); }
  finally { loading.value = false; }
}

onMounted(loadData);
</script>

<style scoped>
.tl-filters {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
  margin-bottom: 20px; padding: 10px 12px;
  background: var(--zj-surface-sunken); border-radius: var(--zj-radius-md);
}

.tl-count {
  margin-left: auto; font-size: 13px; color: var(--zj-ink-400);
  font-variant-numeric: tabular-nums;
}

.tl-track { position: relative; padding-left: 28px; }

.tl-day-group { margin-bottom: 16px; }

.tl-day-label {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 10px; position: relative; left: -28px;
}

.tl-day-dot {
  width: 9px; height: 9px; border-radius: 50%;
  background: var(--zj-pine-500); flex-shrink: 0;
}

.tl-day-text {
  font-family: var(--zj-serif);
  font-size: 15px; font-weight: 600; color: var(--zj-ink-900);
}

.tl-event {
  position: relative; display: flex; align-items: flex-start;
  margin-bottom: 8px; cursor: pointer;
}

.tl-event:hover .tl-card { box-shadow: var(--zj-shadow-md); }

.tl-line {
  position: absolute; left: -20px; top: 12px; bottom: -12px;
  width: 1px; background: var(--zj-line-strong);
}

.tl-event:last-child .tl-line { display: none; }

.tl-node {
  position: absolute; left: -24px; top: 16px;
  width: 9px; height: 9px; border-radius: 50%;
  border: 2px solid var(--zj-canvas); z-index: 1;
}

.node-success { background: var(--zj-pine-500); }
.node-failure { background: var(--zj-danger); }

.tl-card {
  flex: 1; padding: 12px 18px;
  background: var(--zj-surface); border: 0;
  border-radius: var(--zj-radius-md);
  box-shadow: var(--zj-shadow-sm);
  transition: box-shadow var(--zj-dur-fast) var(--zj-ease-out);
}

.card-failure { border-left: 3px solid var(--zj-danger); }

.tl-card-head {
  display: flex; align-items: baseline; gap: 14px;
  flex-wrap: wrap;
}

.tl-action {
  font-size: 12px; font-weight: 500; padding: 1px 10px;
  border-radius: 999px; white-space: nowrap; flex-shrink: 0;
  border: 1px solid;
}

.tl-success { border-color: var(--zj-pine-500); color: var(--zj-pine-600); background: transparent; }
.tl-failure { border-color: var(--zj-danger); color: var(--zj-danger); background: transparent; }

.tl-meta {
  font-size: 13px; color: var(--zj-ink-600); white-space: nowrap;
}

.tl-actor { font-weight: 500; color: var(--zj-ink-900); }
.tl-arrow { margin: 0 4px; color: var(--zj-ink-400); }
.tl-subject { color: var(--zj-ink-600); }

.tl-ip {
  font-size: 12px; color: var(--zj-ink-400);
  font-family: var(--zj-mono);
}

.tl-detail {
  font-size: 12.5px; color: var(--zj-ink-400);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 320px;
}

.tl-time {
  margin-left: auto; font-size: 12.5px; color: var(--zj-ink-400);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.tl-empty {
  text-align: center; padding: 44px 0;
  font-family: var(--zj-serif); font-size: 16px;
  color: var(--zj-ink-400);
}

.tl-pagination { display: flex; justify-content: flex-end; margin-top: 16px; }

.detail-section { margin-top: 24px; }
.detail-section h4 {
  margin: 0 0 12px; font-size: 14px; font-weight: 500;
  color: var(--zj-ink-900);
}
.detail-json {
  background: var(--zj-surface-sunken); border-radius: var(--zj-radius-sm); padding: 12px;
  font-size: 12px; font-family: var(--zj-mono);
  overflow-x: auto; white-space: pre-wrap; word-break: break-all;
}
.mono { font-family: var(--zj-mono); font-size: 12px; }
</style>
