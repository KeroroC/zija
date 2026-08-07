<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h1 class="page-title">报表设置</h1>
        <p class="page-subtitle">投影重建与导出审计</p>
      </div>
    </div>

    <!-- 投影重建 -->
    <div class="section">
      <h2 class="section-title">投影重建</h2>
      <p class="section-desc">清空报表读模型并从源数据重新填充。适用于投影 schema 变更或数据修复。</p>
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
      <p v-if="rebuildResult" class="rebuild-result">{{ rebuildResult }}</p>
    </div>

    <!-- 导出审计 -->
    <div class="section">
      <h2 class="section-title">导出审计</h2>
      <el-table :data="auditLogs" v-loading="auditLoading">
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="action" label="操作" width="160" />
        <el-table-column prop="outcome" label="结果" width="100">
          <template #default="{ row }">
            <span :class="row.outcome === 'SUCCESS' ? 'text-pine' : 'text-danger'">
              {{ row.outcome }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情">
          <template #default="{ row }">
            <span style="font-family: var(--zj-mono); font-size: 12px;">
              {{ JSON.stringify(row.detail) }}
            </span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { rebuildProjection } from '../../api/reporting'
import { getJson } from '../../api/http'

const rebuilding = ref(false)
const rebuildResult = ref('')
const auditLoading = ref(false)
const auditLogs = ref<any[]>([])

async function doRebuild() {
  rebuilding.value = true
  try {
    await rebuildProjection()
    rebuildResult.value = '重建完成'
    ElMessage.success('报表读模型重建完成')
  } catch {
    rebuildResult.value = '重建失败'
    ElMessage.error('重建失败')
  } finally {
    rebuilding.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const result = await getJson<any>('/api/v1/audit-logs?action=EXPORT_PERFORMED&page=1&pageSize=50')
    auditLogs.value = result.items || []
  } finally {
    auditLoading.value = false
  }
}

function formatTime(ts: string) {
  return new Date(ts).toLocaleString('zh-CN')
}

onMounted(loadAuditLogs)
</script>

<style scoped>
.section {
  margin-bottom: 32px;
}
.section-title {
  font-family: var(--zj-serif);
  font-size: 18px;
  color: var(--zj-ink-900);
  margin-bottom: 8px;
}
.section-desc {
  font-size: 13px;
  color: var(--zj-ink-600);
  margin-bottom: 16px;
}
.rebuild-result {
  margin-top: 8px;
  font-size: 13px;
  color: var(--zj-ink-600);
}
.text-pine { color: var(--zj-pine-600); }
.text-danger { color: var(--zj-danger); }
</style>
