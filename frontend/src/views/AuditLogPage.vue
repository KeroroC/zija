<template>
  <div class="audit-page">
    <div class="page-header">
      <div>
        <h2>审计日志</h2>
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
.audit-page {
  margin: 2rem;
}

.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0; }
.page-subtitle { margin: 4px 0 0; color: #65756f; font-size: 14px; }

.tl-filters {
  display: flex; gap: 8px; align-items: center; flex-wrap: wrap;
  margin-bottom: 20px; padding: 12px 16px;
  background: #f8faf9; border-radius: 10px;
}

.tl-count { margin-left: auto; font-size: 12px; color: #9ca3af; }

.tl-track { position: relative; padding-left: 28px; }

.tl-day-group { margin-bottom: 16px; }

.tl-day-label {
  display: flex; align-items: center; gap: 10px;
  margin-bottom: 10px; position: relative; left: -28px;
}

.tl-day-dot {
  width: 10px; height: 10px; border-radius: 50%;
  background: #10b981; flex-shrink: 0;
}

.tl-day-text { font-size: 13px; font-weight: 600; color: #374151; }

.tl-event {
  position: relative; display: flex; align-items: flex-start;
  margin-bottom: 6px; cursor: pointer;
}

.tl-event:hover .tl-card { box-shadow: 0 2px 12px rgba(0,0,0,0.08); }

.tl-line {
  position: absolute; left: -20px; top: 12px; bottom: -12px;
  width: 2px; background: #e5e7eb;
}

.tl-event:last-child .tl-line { display: none; }

.tl-node {
  position: absolute; left: -24px; top: 14px;
  width: 10px; height: 10px; border-radius: 50%;
  border: 2px solid #fff; z-index: 1;
}

.node-success { background: #22c55e; }
.node-failure { background: #ef4444; }

.tl-card {
  flex: 1; padding: 10px 16px;
  background: #fff; border: 1px solid #e5e7eb;
  border-radius: 10px; transition: box-shadow 0.15s;
}

.card-failure { border-left: 3px solid #ef4444; }

.tl-card-head {
  display: flex; align-items: center; gap: 12px;
  flex-wrap: wrap;
}

.tl-action {
  font-size: 12px; font-weight: 600; padding: 2px 10px;
  border-radius: 8px; white-space: nowrap; flex-shrink: 0;
}

.tl-success { background: #f0fdf4; color: #166534; }
.tl-failure { background: #fef2f2; color: #991b1b; }

.tl-meta {
  font-size: 13px; color: #374151; white-space: nowrap;
}

.tl-actor { font-weight: 500; }
.tl-arrow { margin: 0 4px; color: #9ca3af; }
.tl-subject { color: #6b7280; }

.tl-ip {
  font-size: 11px; color: #9ca3af;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: #f3f4f6; padding: 1px 6px; border-radius: 4px;
}

.tl-detail {
  font-size: 11px; color: #9ca3af;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  max-width: 300px;
}

.tl-time {
  margin-left: auto; font-size: 12px; color: #6b7280;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  white-space: nowrap;
}

.tl-empty { text-align: center; padding: 40px; color: #9ca3af; }

.tl-pagination { display: flex; justify-content: flex-end; margin-top: 16px; }

.detail-section { margin-top: 24px; }
.detail-section h4 { margin: 0 0 12px; font-size: 14px; color: #374151; }
.detail-json {
  background: #f3f4f6; border-radius: 8px; padding: 12px;
  font-size: 12px; font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  overflow-x: auto; white-space: pre-wrap; word-break: break-all;
}
.mono { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 12px; }
</style>
