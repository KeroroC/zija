<template>
  <div class="ai-settings-tab">
    <section class="status-section" aria-labelledby="ai-status-title">
      <div class="section-heading">
        <h3 id="ai-status-title" class="section-title">AI 可用状态</h3>
        <el-tooltip content="刷新状态" placement="top">
          <el-button circle text aria-label="刷新 AI 状态" @click="loadStatus">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <div v-if="status" class="status-line">
        <span :class="['zj-dot', status.available ? 'zj-dot-pine' : 'zj-dot-warn']" aria-hidden="true"></span>
        <strong>{{ status.available ? "可用" : `不可用（${reasonLabel(status.reasonCode)}）` }}</strong>
        <span class="status-detail">{{ status.detail }}</span>
      </div>
      <div v-if="status?.chatModel || status?.embeddingModel" class="model-meta">
        <span>聊天模型：{{ status.chatModel || "—" }}</span>
        <span>Embedding：{{ status.embeddingModel || "—" }}</span>
      </div>
    </section>

    <el-divider />

    <el-form v-if="canEdit" :model="form" class="ai-form">
      <el-form-item label="启用 AI">
        <el-switch v-model="form.enabled" />
      </el-form-item>
      <el-form-item label="模型提供方">
        <el-select v-model="form.providerId" class="provider-select">
          <el-option label="Ollama（本地）" value="ollama" />
        </el-select>
      </el-form-item>
      <el-form-item label="提供方凭据">
        <el-input
          v-model="form.credential"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="留空保持现有凭据"
          class="credential-input"
        />
      </el-form-item>
      <el-form-item v-if="form.credentialConfigured" label="清除已保存凭据">
        <el-checkbox v-model="form.clearCredential" />
      </el-form-item>
      <el-form-item label="允许出网">
        <el-switch v-model="form.outboundEnabled" />
      </el-form-item>

      <el-divider content-position="left">资源限制</el-divider>
      <el-form-item label="每分钟请求数">
        <el-input-number v-model="form.requestsPerMinute" :min="1" :max="600" />
      </el-form-item>
      <el-form-item label="成员每分钟请求数">
        <el-input-number v-model="form.memberRequestsPerMinute" :min="1" :max="600" />
      </el-form-item>
      <el-form-item label="上下文上限">
        <el-input-number v-model="form.maxContextTokens" :min="256" :max="131072" :step="256" />
        <span class="unit-hint">tokens</span>
      </el-form-item>
      <el-form-item label="并发请求数">
        <el-input-number v-model="form.maxConcurrentRequests" :min="1" :max="32" />
      </el-form-item>
      <el-form-item label="请求超时">
        <el-input-number v-model="form.requestTimeoutSeconds" :min="1" :max="300" />
        <span class="unit-hint">秒</span>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存 AI 设置</el-button>
      </el-form-item>
    </el-form>
    <p v-else class="readonly">仅管理员可修改。</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { Refresh } from "@element-plus/icons-vue";
import { fetchAiSettings, fetchAiStatus, updateAiSettings } from "../../api/ai";
import type { AiSettings, AiStatus } from "../../types/ai";
import { ApiError } from "../../api/http";
import { AI_CONFIGURATION_VERSION_CONFLICT } from "../../types/errorCodes";
import { useSessionStore } from "../../stores/session";

const session = useSessionStore();
const canEdit = computed(() => session.role === "OWNER" || session.role === "ADMIN");
const saving = ref(false);
const status = ref<AiStatus | null>(null);
const form = reactive<AiSettings & { credential: string; clearCredential: boolean }>({
  enabled: false,
  providerId: "ollama",
  credentialConfigured: false,
  outboundEnabled: false,
  requestsPerMinute: 20,
  memberRequestsPerMinute: 10,
  maxContextTokens: 8192,
  maxConcurrentRequests: 2,
  requestTimeoutSeconds: 30,
  version: 0,
  credential: "",
  clearCredential: false,
});

onMounted(async () => {
  await Promise.all([loadSettings(), loadStatus()]);
});

async function loadSettings() {
  try {
    const settings = await fetchAiSettings();
    Object.assign(form, settings, { credential: "", clearCredential: false });
  } catch (error) {
    showError(error);
  }
}

async function loadStatus() {
  try {
    status.value = await fetchAiStatus();
  } catch (error) {
    showError(error);
  }
}

async function save() {
  saving.value = true;
  try {
    const updated = await updateAiSettings({
      enabled: form.enabled,
      providerId: form.providerId,
      credential: form.credential || undefined,
      clearCredential: form.clearCredential,
      outboundEnabled: form.outboundEnabled,
      requestsPerMinute: form.requestsPerMinute,
      memberRequestsPerMinute: form.memberRequestsPerMinute,
      maxContextTokens: form.maxContextTokens,
      maxConcurrentRequests: form.maxConcurrentRequests,
      requestTimeoutSeconds: form.requestTimeoutSeconds,
      version: form.version,
    });
    Object.assign(form, updated, { credential: "", clearCredential: false });
    ElMessage.success("AI 设置已保存");
    await loadStatus();
  } catch (error) {
    if (error instanceof ApiError && error.errorCode === AI_CONFIGURATION_VERSION_CONFLICT) {
      ElMessage.warning("AI 设置已被他人修改，已为您重新加载");
      await loadSettings();
      return;
    }
    showError(error);
  } finally {
    saving.value = false;
  }
}

function reasonLabel(reasonCode: string): string {
  const labels: Record<string, string> = {
    AI_DISABLED: "已停用",
    PROVIDER_NOT_FOUND: "提供方不可用",
    OUTBOUND_DISABLED: "出网已关闭",
    CREDENTIAL_MISSING: "缺少凭据",
    CHAT_MODEL_MISSING: "聊天模型不可用",
    EMBEDDING_MODEL_MISSING: "Embedding 模型不可用",
    EMBEDDING_DIMENSION_MISMATCH: "Embedding 维度不匹配",
    PROVIDER_UNREACHABLE: "提供方不可达",
  };
  return labels[reasonCode] ?? "不可用";
}

function showError(error: unknown) {
  if (error instanceof ApiError) ElMessage.error(error.message);
}

defineExpose({ form, save, loadSettings, loadStatus });
</script>

<style scoped>
.ai-settings-tab {
  max-width: var(--zj-width-ai-panel);
}
.status-section {
  padding: var(--zj-space-1) 0 var(--zj-space-2);
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title {
  margin: 0;
  color: var(--zj-ink-900);
  font-family: var(--zj-serif);
  font-size: var(--zj-text-heading-sm);
}
.status-line,
.model-meta {
  display: flex;
  align-items: center;
  gap: var(--zj-space-3);
  margin-top: var(--zj-space-3);
  color: var(--zj-ink-600);
  font-size: var(--zj-text-body-sm);
}
.status-detail {
  color: var(--zj-ink-400);
}
.model-meta {
  gap: var(--zj-space-5);
  color: var(--zj-ink-400);
  font-family: var(--zj-mono);
  font-size: var(--zj-text-caption);
}
.ai-form {
  max-width: var(--zj-width-ai-form);
}
.ai-form :deep(.el-form-item__label) {
  width: var(--zj-ai-label-width);
}
.provider-select {
  width: var(--zj-width-ai-select);
}
.credential-input {
  width: var(--zj-width-ai-secret);
}
.unit-hint {
  margin-left: var(--zj-space-3);
  color: var(--zj-ink-400);
  font-size: var(--zj-text-caption);
}
.readonly {
  color: var(--zj-ink-600);
}
</style>
