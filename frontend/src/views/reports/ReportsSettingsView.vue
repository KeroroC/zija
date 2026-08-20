<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">报表设置</h1>
        <p class="page-subtitle">投影重建与导出审计</p>
      </div>
    </div>

    <!-- 投影重建 -->
    <section class="card rebuild-card" aria-labelledby="rebuild-title">
      <div class="rebuild-head">
        <div class="rebuild-icon" aria-hidden="true">
          <el-icon><Refresh /></el-icon>
        </div>
        <div>
          <h2 id="rebuild-title" class="section-title">投影重建</h2>
          <p class="section-desc">清空报表读模型并从源数据重新填充。适用于投影 schema 变更或数据修复。</p>
        </div>
      </div>
      <div class="rebuild-actions">
        <el-popconfirm
          title="确认重建报表读模型？此操作将清空现有投影数据。"
          confirm-button-text="确认重建"
          cancel-button-text="取消"
          @confirm="doRebuild"
        >
          <template #reference>
            <el-button type="danger" :loading="rebuilding">重建报表读模型</el-button>
          </template>
        </el-popconfirm>
        <p v-if="rebuildResult" class="rebuild-result" :class="rebuildFailed ? 'result-fail' : 'result-ok'">
          <span class="zj-dot" :class="rebuildFailed ? 'zj-dot-danger' : 'zj-dot-pine'"></span>
          {{ rebuildResult }}
        </p>
      </div>
    </section>

    <!-- 导出审计 -->
    <section class="card audit-card" aria-labelledby="audit-title">
      <div class="audit-head">
        <h2 id="audit-title" class="section-title">导出审计</h2>
        <span v-if="!auditLoading" class="audit-count">
          共 <span class="zj-num">{{ auditLogs.length }}</span> 条
        </span>
      </div>
      <el-table :data="auditLogs" v-loading="auditLoading" class="audit-table">
        <el-table-column label="时间" width="170" class-name="col-secondary">
          <template #default="{ row }">
            <span class="cell-secondary zj-num">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="130">
          <template #default="{ row }">
            {{ actionLabel(row.action) }}
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90">
          <template #default="{ row }">
            <span class="zj-dot" :class="row.outcome === 'SUCCESS' ? 'zj-dot-pine' : 'zj-dot-danger'"></span>
            {{ outcomeLabel(row.outcome) }}
          </template>
        </el-table-column>
        <el-table-column label="详情" min-width="220">
          <template #default="{ row }">
            <span class="detail-json">{{ formatDetail(row.detail) }}</span>
          </template>
        </el-table-column>
        <template #empty>
          <div class="audit-empty">
            <p class="audit-empty-title">还没有导出记录</p>
            <p class="audit-empty-hint">从报表页导出 CSV 后，会在这里留下操作记录。</p>
          </div>
        </template>
      </el-table>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { rebuildProjection } from '../../api/reporting'
import { fetchAuditLogs } from '../../api/audit'
import { formatDateTime } from '../../utils/date'
import { ACTION_LABELS, type AuditLogItem } from '../../types/audit'

const rebuilding = ref(false)
const rebuildResult = ref('')
const rebuildFailed = ref(false)
const auditLoading = ref(false)
const auditLogs = ref<AuditLogItem[]>([])

async function doRebuild() {
  rebuilding.value = true
  try {
    await rebuildProjection()
    rebuildResult.value = '重建完成'
    rebuildFailed.value = false
    ElMessage.success('报表读模型重建完成')
  } catch {
    rebuildResult.value = '重建失败，请稍后重试'
    rebuildFailed.value = true
    ElMessage.error('重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const result = await fetchAuditLogs({ action: 'EXPORT_PERFORMED', page: 1, pageSize: 50 })
    auditLogs.value = result.items ?? []
  } finally {
    auditLoading.value = false
  }
}

function actionLabel(action: string): string {
  return ACTION_LABELS[action] ?? action
}

function outcomeLabel(outcome: string): string {
  return outcome === 'SUCCESS' ? '成功' : '失败'
}

/** 详情压缩为单行：`键: 值 · 键: 值`，避免整块 JSON 撑破行 */
function formatDetail(detail: Record<string, unknown> | null | undefined): string {
  if (!detail) return '—'
  return Object.entries(detail).map(([k, v]) => `${k}: ${v}`).join(' · ')
}

onMounted(loadAuditLogs)
</script>

<style scoped>
/* ---------- 投影重建 ---------- */
.rebuild-card {
  margin-bottom: 24px;
  padding: 24px;
  border-left: 3px solid var(--zj-warning);
}

.rebuild-head {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.rebuild-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border-radius: var(--zj-radius-sm);
  background: rgba(156, 116, 38, 0.08);
  color: var(--zj-warning);
}

.rebuild-icon .el-icon {
  font-size: 20px;
}

.rebuild-actions {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 20px;
  padding-left: 56px;
}

.rebuild-result {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  font-size: 13px;
}

.result-ok {
  color: var(--zj-pine-600);
}

.result-fail {
  color: var(--zj-danger);
}

/* ---------- 导出审计 ---------- */
.audit-card {
  padding: 20px 20px 24px;
}

.audit-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.audit-count {
  font-size: 13px;
  color: var(--zj-ink-400);
}

.audit-table {
  border-radius: var(--zj-radius-md);
  overflow: hidden;
}

.cell-secondary {
  color: var(--zj-ink-600);
}

.detail-json {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: var(--zj-mono);
  font-size: 12px;
  color: var(--zj-ink-600);
}

.audit-empty {
  padding: 32px 0 36px;
  text-align: center;
}

.audit-empty-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 16px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.audit-empty-hint {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--zj-ink-400);
}

/* 通用 section 标题（与页头区分） */
.section-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 16px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.section-desc {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--zj-ink-600);
}
</style>
