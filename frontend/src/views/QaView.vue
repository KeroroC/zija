<template>
  <div class="page-container qa-page">
    <header class="page-header">
      <div>
        <h1 class="page-title">家庭问答</h1>
        <p class="page-subtitle">用自然语言查询物品、批次、库存位、位置、流水与提醒</p>
      </div>
      <div class="qa-readiness">
        <p v-if="aiStatus" class="qa-ai-status" data-testid="qa-ai-status">
          <span
            :class="['zj-dot', aiStatus.available ? 'zj-dot-pine' : 'zj-dot-warn']"
            aria-hidden="true"
          ></span>
          <span>{{ aiStatusLine }}</span>
        </p>
        <p
          v-if="knowledgePrepLine"
          class="qa-knowledge-prep"
          data-testid="qa-knowledge-prep"
        >
          {{ knowledgePrepLine }}
        </p>
      </div>
    </header>

    <section class="qa-shell">
      <!-- 对话记录（仅当前浏览器会话，不存服务端） / 空状态 -->
      <div
        v-if="turns.length || submitting"
        ref="threadEl"
        class="qa-thread"
        data-testid="qa-thread"
      >
        <div v-for="(turn, i) in turns" :key="i" class="qa-turn">
          <div class="qa-question">
            <span class="qa-question-label">问</span>
            <span class="qa-question-text">{{ turn.question }}</span>
          </div>

          <div class="qa-answer">
            <div
              v-if="submitting && confirmingIndex === i"
              class="qa-pending"
              data-testid="qa-pending"
              role="status"
              aria-live="polite"
              aria-busy="true"
            >
              <el-skeleton animated class="qa-pending-skeleton">
                <template #template>
                  <el-skeleton-item variant="p" class="qa-pending-line qa-pending-line-wide" />
                  <el-skeleton-item variant="p" class="qa-pending-line qa-pending-line-mid" />
                  <el-skeleton-item variant="p" class="qa-pending-line qa-pending-line-narrow" />
                </template>
              </el-skeleton>
              <p class="qa-pending-copy">正在查阅账册…</p>
              <p v-if="waitingElapsedSeconds >= WAITING_ELAPSED_HINT_AFTER" class="qa-pending-elapsed">
                已等待 {{ waitingElapsedSeconds }} 秒
              </p>
              <el-button class="qa-pending-cancel" data-testid="qa-cancel" @click="cancelAsk">取消</el-button>
            </div>
            <div v-else>
            <div v-if="turn.answer.usedAnswerScope" class="qa-used-scope" data-testid="qa-used-scope">
              <span class="zj-badge zj-badge-ink">
                实际 {{ answerScopeLabel(turn.answer.usedAnswerScope) }}
              </span>
              <span v-if="turn.answer.recommendedAnswerScope" class="qa-datetime">
                推荐 {{ answerScopeLabel(turn.answer.recommendedAnswerScope) }}
              </span>
              <span v-if="turn.answer.targetScope?.label" class="qa-datetime">
                {{ turn.answer.targetScope.label }}
              </span>
            </div>

            <div v-if="turn.answer.reasonCode === 'AMBIGUOUS_TARGET'" class="qa-clarification">
              <p class="qa-summary">{{ turn.answer.summary }}</p>
              <button
                v-for="candidate in turn.answer.candidates ?? []"
                :key="`${candidate.type}-${candidate.id}`"
                type="button"
                class="qa-candidate"
                data-testid="qa-candidate"
                :disabled="submitting"
                @click="confirmCandidate(i, candidate)"
              >
                <span class="qa-candidate-label">{{ candidate.label }}</span>
                <span class="qa-candidate-detail">{{ candidate.detail }}</span>
              </button>
            </div>
            <div v-else-if="!hasDisplayableResults(turn.answer)" class="qa-unavailable">
              <span v-if="reasonLabel(turn.answer.reasonCode)" class="zj-badge zj-badge-warn">
                {{ reasonLabel(turn.answer.reasonCode) }}
              </span>
              <p class="qa-summary">{{ turn.answer.summary }}</p>
              <el-button
                v-if="attachmentEntry(turn.answer.jumps)"
                text
                data-testid="qa-attachment-entry"
                class="qa-attachment-entry"
                @click="goJump(attachmentEntry(turn.answer.jumps)!)"
              >
                <el-icon><Paperclip /></el-icon>
                {{ attachmentEntry(turn.answer.jumps)?.label ?? "附件管理" }}
              </el-button>
            </div>
            <template v-else>
              <div
                v-if="turn.answer.reasonCode === 'STRUCTURED_FACTS_FALLBACK'"
                class="qa-fallback"
                data-testid="qa-fallback"
              >
                <span class="zj-badge zj-badge-warn">{{ reasonLabel(turn.answer.reasonCode) }}</span>
                <p v-if="!turn.answer.answerParts?.length" class="qa-summary">
                  {{ turn.answer.summary }}
                </p>
              </div>
              <div v-if="turn.answer.answerParts?.length" class="qa-answer-parts">
                <article
                  v-for="part in turn.answer.answerParts"
                  :key="part.category"
                  class="qa-answer-part"
                  data-testid="qa-answer-part"
                >
                  <header class="qa-answer-part-header">
                    <span class="zj-badge" :class="sourceBadgeClass(part.category)">
                      {{ part.label }}
                    </span>
                    <span v-if="!part.available && reasonLabel(part.reasonCode)" class="qa-datetime">
                      {{ reasonLabel(part.reasonCode) }}
                    </span>
                  </header>
                  <p class="qa-summary">{{ part.summary }}</p>
                </article>
              </div>
              <p
                v-else-if="turn.answer.reasonCode !== 'STRUCTURED_FACTS_FALLBACK'"
                class="qa-summary"
              >
                {{ turn.answer.summary }}
              </p>

              <div
                v-for="conflict in turn.answer.conflicts ?? []"
                :key="`${conflict.kind}-${conflict.factValue}-${conflict.knowledgeValue}`"
                class="qa-conflict"
                data-testid="qa-conflict"
              >
                <strong>来源不一致</strong>
                <span>家庭事实 {{ conflict.factValue }} · 知识来源 {{ conflict.knowledgeValue }}</span>
                <p>{{ conflict.note }}</p>
              </div>

              <!-- 回答依据：来源类别 + 数据时间 -->
              <div v-if="factSources(turn.answer.sources).length" class="qa-sources">
                <template v-for="(source, j) in factSources(turn.answer.sources)" :key="j">
                  <span class="zj-badge zj-badge-pine">{{ source.label }}</span>
                  <span class="qa-datetime">数据时间 {{ formatDateTime(source.dataTime) }}</span>
                </template>
              </div>

              <div v-if="knowledgeSources(turn.answer.sources).length" class="qa-groundings">
                <article
                  v-for="(source, j) in knowledgeSources(turn.answer.sources)"
                  :key="`${source.attachmentId ?? 'source'}-${j}`"
                  class="qa-grounding"
                >
                  <header class="qa-grounding-header">
                    <el-button text class="qa-grounding-file" @click="openSource(source)">
                      <el-icon><Paperclip /></el-icon>
                      {{ source.attachmentName ?? source.label }}
                    </el-button>
                    <span class="zj-badge zj-badge-plain">{{ source.mountLabel }}</span>
                    <span class="qa-datetime">数据时间 {{ formatDateTime(source.dataTime) }}</span>
                  </header>
                  <p v-if="evidenceLocation(source)" class="qa-grounding-location">
                    {{ evidenceLocation(source) }}
                  </p>
                  <blockquote v-if="source.excerpt" class="qa-grounding-excerpt">
                    {{ source.excerpt }}
                  </blockquote>
                </article>
              </div>

              <!-- 权威页面跳转 -->
              <div v-if="turn.answer.jumps.length" class="qa-jumps">
                <el-button
                  v-for="(jump, j) in turn.answer.jumps"
                  :key="j"
                  size="small"
                  text
                  class="qa-jump"
                  @click="goJump(jump)"
                >
                  <el-icon class="qa-jump-icon">
                    <Paperclip v-if="jump.type === 'ATTACHMENT'" />
                    <Location v-else />
                  </el-icon>
                  {{ jump.label }}
                </el-button>
              </div>

              <!-- 结构化结果 -->
              <div
                v-for="(result, r) in turn.answer.structuredResults"
                :key="r"
                class="qa-result"
              >
                <div class="qa-result-title">{{ result.title }}</div>
                <el-table
                  v-if="result.rows.length"
                  :data="result.rows"
                  size="small"
                  class="qa-result-table"
                >
                  <el-table-column
                    v-for="col in columnsOf(result.rows)"
                    :key="col"
                    :prop="col"
                    :label="col"
                    min-width="96"
                  >
                    <template #default="{ row }">
                      {{ formatResultCell(col, row[col]) }}
                    </template>
                  </el-table-column>
                </el-table>
                <p v-else class="qa-result-empty">暂无数据</p>
              </div>
            </template>
            </div>
          </div>
        </div>

        <div
          v-if="submitting && confirmingIndex === null"
          class="qa-turn"
          data-testid="qa-pending"
        >
          <div class="qa-question">
            <span class="qa-question-label">问</span>
            <span class="qa-question-text">{{ pendingQuestion }}</span>
          </div>
          <div
            class="qa-answer"
            role="status"
            aria-live="polite"
            aria-busy="true"
          >
            <div class="qa-pending">
              <el-skeleton animated class="qa-pending-skeleton">
                <template #template>
                  <el-skeleton-item variant="p" class="qa-pending-line qa-pending-line-wide" />
                  <el-skeleton-item variant="p" class="qa-pending-line qa-pending-line-mid" />
                  <el-skeleton-item variant="p" class="qa-pending-line qa-pending-line-narrow" />
                </template>
              </el-skeleton>
              <p class="qa-pending-copy">正在查阅账册…</p>
              <p v-if="waitingElapsedSeconds >= WAITING_ELAPSED_HINT_AFTER" class="qa-pending-elapsed">
                已等待 {{ waitingElapsedSeconds }} 秒
              </p>
              <el-button class="qa-pending-cancel" data-testid="qa-cancel" @click="cancelAsk">取消</el-button>
            </div>
          </div>
        </div>
      </div>

      <div v-else class="qa-empty">
        <div class="qa-empty-icon" aria-hidden="true">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <p class="qa-empty-title">问问家里的物品与资料</p>
        <p class="qa-empty-hint">试试这些问题，点一下填入输入框</p>
        <div class="qa-empty-examples">
          <button
            v-for="example in exampleQuestions"
            :key="example"
            type="button"
            class="qa-scope-chip"
            data-testid="qa-example"
            @click="question = example"
          >
            {{ example }}
          </button>
        </div>
      </div>

      <!-- 输入区：固定吸视口底，settings 面板可折叠。out 当 backdrop, card 内嵌 -->
      <div class="qa-composer">
        <div class="qa-composer-card">
          <div class="qa-composer-scope">
            <button
              type="button"
              class="qa-scope-chip"
              data-testid="qa-settings-toggle"
              :aria-expanded="settingsOpen"
              @click="settingsOpen = !settingsOpen"
            >
              <el-icon class="qa-scope-chip-icon"><Setting /></el-icon>
              <span class="qa-scope-chip-label">{{ settingsOpen ? "收起范围设置" : "范围设置" }}</span>
              <el-icon class="qa-scope-chip-caret" :class="{ 'is-open': settingsOpen }">
                <ArrowDown />
              </el-icon>
            </button>
            <span
              v-if="!settingsOpen"
              class="zj-badge zj-badge-pine"
              data-testid="qa-active-scope-chip"
            >
              {{ answerScopeLabel(effectiveScope) }}
            </span>
            <span v-if="!settingsOpen && pageContext" class="qa-context-label">
              当前页面 · {{ pageContext.label ?? answerTargetLabel(pageContext.type) }}
            </span>
          </div>

          <!-- settings 面板：v-show 保留 DOM，settings 测试选择器稳定 -->
          <div v-show="settingsOpen" class="qa-settings" data-testid="qa-settings-panel">
            <div class="qa-scope-bar">
              <div class="qa-scope-control" data-testid="qa-answer-scope">
                <span class="qa-control-label">回答来源</span>
                <el-segmented v-model="answerScope" :options="answerScopeOptions" :disabled="submitting" />
              </div>
              <div class="qa-scope-control qa-target-control">
                <span class="qa-control-label">回答对象</span>
                <div data-testid="qa-target-type">
                  <el-segmented v-model="targetType" :options="targetTypeOptions" :disabled="submitting" />
                </div>
                <el-select
                  v-if="targetType"
                  v-model="selectedScopeId"
                  data-testid="qa-scope-select"
                  filterable
                  clearable
                  :loading="scopeLoading"
                  :disabled="submitting"
                  :placeholder="scopePlaceholder"
                  class="qa-scope-select"
                >
                  <el-option
                    v-for="option in scopeChoices"
                    :key="option.value"
                    :label="option.label"
                    :value="option.value"
                  />
                </el-select>
              </div>
            </div>
            <div class="qa-scope-preview" data-testid="qa-scope-recommendation">
              <span class="zj-badge zj-badge-pine">推荐 {{ answerScopeLabel(recommendedScope) }}</span>
              <span v-if="pageContext" class="qa-context-label">
                当前页面 · {{ pageContext.label ?? answerTargetLabel(pageContext.type) }}
              </span>
            </div>
          </div>

          <el-input
            v-model="question"
            type="textarea"
            :rows="2"
            resize="none"
            maxlength="2000"
            :placeholder="questionPlaceholder"
            :disabled="submitting"
            class="qa-input"
            @keydown.enter.exact.prevent="submit"
          />
          <div class="qa-composer-footer">
            <span class="qa-hint">{{ scopeHint }}</span>
            <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
              提问
            </el-button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { ChatDotRound, Location, Paperclip, Setting, ArrowDown } from "@element-plus/icons-vue";
import { askHouseholdQuestion, fetchAiStatus, fetchKnowledgeSources } from "../api/ai";
import { fetchItems } from "../api/catalog";
import { fetchLots } from "../api/inventory";
import { fetchLocationTree } from "../api/location";
import { loadQaThread, saveQaThread } from "../utils/qaThread";
import { flattenLocationChoices } from "../utils/location";
import { movementTypeLabel } from "../utils/movement";
import { aiStatusReasonLabel } from "../utils/aiStatus";
import type {
  AiStatus,
  HouseholdFactAnswer,
  QaAnswerScope,
  QaAnswerSource,
  QaJump,
  QaQuestionOptions,
  QaQuestionScope,
  QaScopeCandidate,
  KnowledgeSourceInfo,
} from "../types/ai";
import type { CatalogItem } from "../types/catalog";
import type { LotSummary } from "../types/inventory";
import type { LocationNode } from "../types/location";
import { ApiError } from "../api/http";
import { AI_REQUEST_LIMITED } from "../types/errorCodes";

const router = useRouter();
const route = useRoute();
const WAITING_ELAPSED_HINT_AFTER = 3;
const exampleQuestions = [
  "牛奶还有多少？",
  "哪些批次快到期了？",
  "看看低库存物品",
  "牛奶最近有没有入库？",
  "滤网怎么清洁？",
];

const restoredThread = loadQaThread();
const question = ref(restoredThread.draft);
const submitting = ref(false);
const pendingQuestion = ref("");
const confirmingIndex = ref<number | null>(null);
const waitingElapsedSeconds = ref(0);
const threadEl = ref<HTMLElement | null>(null);
let waitingTimer: ReturnType<typeof setInterval> | null = null;
let waitingStartedAt = 0;
let askAbort: AbortController | null = null;
const turns = ref(restoredThread.turns);
// 范围设置面板：首次默认展开，提问后自动收起；用户后续可手动再展开。
const settingsOpen = ref(restoredThread.turns.length === 0);

watch(
  [turns, question],
  () => saveQaThread({ turns: turns.value, draft: question.value }),
  { deep: true },
);

const answerScope = ref<QaAnswerScope>("AUTO");
const targetType = ref<"" | "ITEM" | "LOT" | "LOCATION">("");
const selectedScopeId = ref("");
const scopeLoading = ref(false);
const items = ref<CatalogItem[]>([]);
const lots = ref<LotSummary[]>([]);
const locationRoots = ref<LocationNode[]>([]);
const aiStatus = ref<AiStatus | null>(null);
const knowledgePrep = ref<{ processing: number; available: number; failed: number } | null>(null);
const SCOPE_PAGE_SIZE = 100;
const answerScopeOptions = [
  { label: "自动", value: "AUTO" },
  { label: "家庭事实", value: "HOUSEHOLD_FACT" },
  { label: "知识来源", value: "KNOWLEDGE_SOURCE" },
  { label: "两者", value: "BOTH" },
];
const targetTypeOptions = [
  { label: "物品", value: "ITEM" },
  { label: "批次", value: "LOT" },
  { label: "位置", value: "LOCATION" },
];

const pageContext = computed<QaQuestionScope | undefined>(() => {
  const type = queryString(route.query.contextType)?.toUpperCase();
  const id = queryString(route.query.contextId);
  if (!id || !type || !["ITEM", "LOT", "LOCATION"].includes(type)) return undefined;
  return {
    type: type as QaQuestionScope["type"],
    id,
    label: queryString(route.query.contextLabel) || undefined,
  };
});

const knowledgeRange = computed<{ type: "ITEM" | "LOT"; id: string } | undefined>(() => {
  if ((targetType.value === "ITEM" || targetType.value === "LOT") && selectedScopeId.value) {
    return { type: targetType.value, id: selectedScopeId.value };
  }
  const ctx = pageContext.value;
  if (ctx && (ctx.type === "ITEM" || ctx.type === "LOT")) {
    return { type: ctx.type, id: ctx.id };
  }
  return undefined;
});

const knowledgePrepLine = computed(() => {
  if (!knowledgePrep.value) return "";
  const { processing, available, failed } = knowledgePrep.value;
  return `知识准备状态：处理中 ${processing} · 可用 ${available} · 失败 ${failed}`;
});

const scopeChoices = computed(() => {
  if (targetType.value === "ITEM") {
    return items.value.map((item) => ({
      value: item.id,
      label: item.status === "ARCHIVED" ? `${item.name}（已归档）` : item.name,
    }));
  }
  if (targetType.value === "LOT") {
    return lots.value.map((lot) => ({ value: lot.lotId, label: lotLabel(lot) }));
  }
  if (targetType.value === "LOCATION") {
    return flattenLocationChoices(locationRoots.value);
  }
  return [];
});

const scopePlaceholder = computed(() => {
  switch (targetType.value) {
    case "ITEM": return "选择物品";
    case "LOT": return "选择批次";
    case "LOCATION": return "选择位置";
    default: return "";
  }
});

const canSubmit = computed(() => Boolean(question.value.trim()) && !submitting.value);

const recommendedScope = computed<Exclude<QaAnswerScope, "AUTO">>(() => {
  const normalized = question.value.trim().toLowerCase();
  const fact = ["库存", "还有", "多少", "哪里", "在哪", "位置", "批次", "到期", "临期",
    "低库存", "缺货", "流水", "入库", "领用", "报损", "提醒", "当前"]
    .some((term) => normalized.includes(term));
  const knowledge = ["怎么", "如何", "清洁", "维护", "保养", "使用", "说明", "故障", "注意", "步骤", "资料"]
    .some((term) => normalized.includes(term));
  if (fact && knowledge) return "BOTH";
  if (knowledge) return "KNOWLEDGE_SOURCE";
  if (fact) return "HOUSEHOLD_FACT";
  if (pageContext.value && pageContext.value.type !== "LOCATION") return "BOTH";
  return "HOUSEHOLD_FACT";
});

const effectiveScope = computed<Exclude<QaAnswerScope, "AUTO">>(
  () => answerScope.value === "AUTO" ? recommendedScope.value : answerScope.value,
);

const scopeHint = computed(() => {
  if (aiStatus.value && !aiStatus.value.available) {
    return "知识问答不可用，提问将走家庭事实兜底";
  }
  return `实际将使用 ${answerScopeLabel(effectiveScope.value)}`;
});

const questionPlaceholder = computed(() => effectiveScope.value === "HOUSEHOLD_FACT"
  ? "例如：牛奶还有多少、放在哪里？哪些批次快到期了？"
  : "例如：库存是否与说明书一致？滤网怎么清洁？");

const aiStatusLine = computed(() => {
  const status = aiStatus.value;
  if (!status) return "";
  if (status.available) return "AI 可用";
  return `AI 不可用（${aiStatusReasonLabel(status.reasonCode)}）`;
});

onMounted(() => {
  void loadAiStatus();
});

watch(
  () => knowledgeRange.value ? `${knowledgeRange.value.type}:${knowledgeRange.value.id}` : "HOUSEHOLD",
  async (_rangeKey, _previous, onCleanup) => {
    let active = true;
    onCleanup(() => {
      active = false;
    });
    const range = knowledgeRange.value;
    try {
      const sources = await fetchKnowledgeSources();
      if (!active) return;
      knowledgePrep.value = summarizeKnowledgePrep(sources, range);
    } catch {
      if (active) knowledgePrep.value = null;
    }
  },
  { immediate: true },
);

async function loadAiStatus() {
  try {
    aiStatus.value = await fetchAiStatus();
  } catch {
    aiStatus.value = null;
  }
}

function summarizeKnowledgePrep(
  sources: KnowledgeSourceInfo[],
  range: { type: "ITEM" | "LOT"; id: string } | undefined,
): { processing: number; available: number; failed: number } {
  const counts = { processing: 0, available: 0, failed: 0 };
  for (const source of sources) {
    if (!isKnowledgeSourceInRange(source, range)) continue;
    if (source.status === "PROCESSING") counts.processing += 1;
    else if (source.status === "AVAILABLE") counts.available += 1;
    else if (source.status === "FAILED") counts.failed += 1;
  }
  return counts;
}

function isKnowledgeSourceInRange(
  source: KnowledgeSourceInfo,
  range: { type: "ITEM" | "LOT"; id: string } | undefined,
): boolean {
  if (source.mountType === "HOUSEHOLD") return true;
  if (!range) return false;
  return source.mountType === range.type && source.mountId === range.id;
}

watch(targetType, async (mode, _previousMode, onCleanup) => {
  selectedScopeId.value = "";
  if (!mode) return;
  let active = true;
  onCleanup(() => {
    active = false;
  });
  scopeLoading.value = true;
  try {
    if (mode === "ITEM" && items.value.length === 0) {
      items.value = await loadAllScopeOptions((page) => fetchItems({
        page,
        pageSize: SCOPE_PAGE_SIZE,
      }));
    } else if (mode === "LOT" && lots.value.length === 0) {
      lots.value = await loadAllScopeOptions((page) => fetchLots({
        page,
        pageSize: SCOPE_PAGE_SIZE,
      }));
    } else if (mode === "LOCATION" && locationRoots.value.length === 0) {
      locationRoots.value = (await fetchLocationTree()).roots;
    }
  } catch {
    if (active) ElMessage.error(scopeLoadError(mode));
  } finally {
    if (active) scopeLoading.value = false;
  }
});

// 聊过一次后自动收起范围设置，让 composer 回归到聊天输入框的瘦体形态；
// 用户仍可手动点 chip 再次展开。
watch(() => turns.value.length, (count) => {
  if (count > 0 && settingsOpen.value) settingsOpen.value = false;
  if (count === 0) return;
  void scrollThreadToLatest();
});

watch([submitting, confirmingIndex], ([isSubmitting, index]) => {
  if (!isSubmitting || index !== null) return;
  void scrollThreadToLatest();
});

async function scrollThreadToLatest() {
  await nextTick();
  const thread = threadEl.value;
  if (!thread) return;
  thread.scrollTop = thread.scrollHeight;
}

function startWaiting(text: string, index: number | null) {
  pendingQuestion.value = text;
  confirmingIndex.value = index;
  waitingStartedAt = Date.now();
  waitingElapsedSeconds.value = 0;
  if (waitingTimer !== null) {
    clearInterval(waitingTimer);
  }
  waitingTimer = setInterval(() => {
    waitingElapsedSeconds.value = Math.floor((Date.now() - waitingStartedAt) / 1000);
  }, 1000);
}

function stopWaiting() {
  if (waitingTimer !== null) {
    clearInterval(waitingTimer);
    waitingTimer = null;
  }
  pendingQuestion.value = "";
  confirmingIndex.value = null;
  waitingElapsedSeconds.value = 0;
  askAbort = null;
}

/** 客户端停止等待即可；已发出的请求仍可能占服务端并发名额，本票不要求服务端取消模型调用。 */
function cancelAsk() {
  askAbort?.abort();
}

function isAbortError(error: unknown): boolean {
  return typeof error === "object"
    && error !== null
    && "name" in error
    && (error as { name: string }).name === "AbortError";
}

onUnmounted(stopWaiting);

async function loadAllScopeOptions<T>(
  fetchPage: (page: number) => Promise<{ items: T[]; total: number }>,
): Promise<T[]> {
  const all: T[] = [];
  for (let page = 1; ; page += 1) {
    const result = await fetchPage(page);
    all.push(...result.items);
    if (all.length >= result.total || result.items.length === 0) {
      return all;
    }
  }
}

async function submit() {
  if (!canSubmit.value) return;
  const text = question.value.trim();
  question.value = "";
  askAbort = new AbortController();
  const signal = askAbort.signal;
  startWaiting(text, null);
  submitting.value = true;
  try {
    const options = questionOptions();
    const result = await askHouseholdQuestion(text, options, signal);
    if (signal.aborted) {
      question.value = text;
      ElMessage.info("已取消");
      return;
    }
    turns.value.push({
      question: text,
      answerScope: answerScope.value,
      answer: result,
      confirmedScopes: nextConfirmedScopes(options),
    });
  } catch (e) {
    question.value = text;
    if (signal.aborted || isAbortError(e)) {
      ElMessage.info("已取消");
    } else if (e instanceof ApiError) {
      ElMessage.error(qaErrorMessage(e));
    } else {
      ElMessage.error("提问失败，请稍后重试");
    }
  } finally {
    stopWaiting();
    submitting.value = false;
  }
}

async function confirmCandidate(index: number, candidate: QaScopeCandidate) {
  const turn = turns.value[index];
  if (!turn || submitting.value) return;
  askAbort = new AbortController();
  const signal = askAbort.signal;
  startWaiting(turn.question, index);
  submitting.value = true;
  try {
    const confirmedScope: QaQuestionScope = {
      type: candidate.type,
      id: candidate.id,
      label: candidate.label,
    };
    const options: QaQuestionOptions = {
      answerScope: turn.answer.usedAnswerScope ?? turn.answerScope,
      scope: confirmedScope,
    };
    if (turn.confirmedScopes.length > 0) {
      options.confirmedScopes = [...turn.confirmedScopes];
    }
    const result = await askHouseholdQuestion(turn.question, options, signal);
    if (signal.aborted) {
      ElMessage.info("已取消");
      return;
    }
    turn.answer = result;
    if (!turn.confirmedScopes.some((scope) => scope.type === confirmedScope.type && scope.id === confirmedScope.id)) {
      turn.confirmedScopes.push(confirmedScope);
    }
  } catch (e) {
    if (signal.aborted || isAbortError(e)) {
      ElMessage.info("已取消");
    } else {
      ElMessage.error(e instanceof ApiError ? qaErrorMessage(e) : "提问失败，请稍后重试");
    }
  } finally {
    stopWaiting();
    submitting.value = false;
  }
}

function questionOptions(): QaQuestionOptions {
  const options: QaQuestionOptions = { answerScope: answerScope.value };
  if (targetType.value && selectedScopeId.value) {
    const choice = scopeChoices.value.find((option) => option.value === selectedScopeId.value);
    options.scope = {
      type: targetType.value,
      id: selectedScopeId.value,
      ...(targetType.value === "LOCATION" && choice?.label ? { label: choice.label } : {}),
    };
    return options;
  }
  if (pageContext.value) {
    options.pageContext = pageContext.value;
  }
  const confirmedScopes = lastConfirmedScopes();
  if (confirmedScopes.length > 0) {
    options.confirmedScopes = confirmedScopes;
  }
  return options;
}

const MAX_CONFIRMED_SCOPES = 3;

function lastConfirmedScopes(): QaQuestionScope[] {
  const last = turns.value.at(-1);
  if (!last) return [];
  return uniqueScopes(last.confirmedScopes).slice(-MAX_CONFIRMED_SCOPES);
}

function nextConfirmedScopes(options: QaQuestionOptions): QaQuestionScope[] {
  if (options.scope) return uniqueScopes([options.scope]);
  return uniqueScopes([...(options.confirmedScopes ?? []), options.pageContext])
    .slice(-MAX_CONFIRMED_SCOPES);
}

function uniqueScopes(scopes: Array<QaQuestionScope | undefined>): QaQuestionScope[] {
  const result: QaQuestionScope[] = [];
  for (const scope of scopes) {
    if (!scope) continue;
    if (result.some((existing) => existing.type === scope.type && existing.id === scope.id)) {
      continue;
    }
    result.push(scope);
  }
  return result;
}

/** 从行数据推断列名（保持插入顺序）。 */
function columnsOf(rows: Array<Record<string, string>>): string[] {
  const cols: string[] = [];
  for (const row of rows) {
    for (const key of Object.keys(row)) {
      if (!cols.includes(key)) cols.push(key);
    }
  }
  return cols;
}

function formatResultCell(column: string, value: string): string {
  if (column === "类型") return movementTypeLabel(value);
  return value;
}

function hasDisplayableResults(answer: HouseholdFactAnswer): boolean {
  return answer.reasonCode === "ANSWERED" || answer.reasonCode === "STRUCTURED_FACTS_FALLBACK";
}

/** 用户可见的失败/降级原因；未知码不展示英文原文，回退到 summary。 */
const QA_REASON_LABELS: Record<string, string> = {
  NO_AVAILABLE_KNOWLEDGE_SOURCE: "当前范围没有可用的知识来源",
  KNOWLEDGE_SOURCE_PROCESSING: "知识来源正在准备",
  KNOWLEDGE_SOURCE_PREPARATION_FAILED: "知识来源准备失败",
  KNOWLEDGE_MODEL_UNAVAILABLE: "模型暂不可用",
  MODEL_UNAVAILABLE: "模型暂不可用",
  AI_QA_TIMEOUT: "模型暂不可用",
  STRUCTURED_FACTS_FALLBACK: "模型不可用，已返回可核对的家庭事实",
};

function reasonLabel(reasonCode: string): string | undefined {
  return QA_REASON_LABELS[reasonCode];
}

function qaErrorMessage(error: ApiError): string {
  if (error.errorCode !== AI_REQUEST_LIMITED) return error.message;
  switch (error.reasonCode) {
    case "AI_MEMBER_RATE_LIMITED": return "你的提问过于频繁，请稍后再试";
    case "AI_DEPLOYMENT_RATE_LIMITED": return "当前 AI 请求较多，请稍后再试";
    case "AI_CONTEXT_LIMIT_EXCEEDED": return "问题和资料内容超过当前上下文上限";
    case "AI_CONCURRENCY_LIMIT_EXCEEDED": return "当前 AI 正在处理其他请求，请稍后再试";
    default: return "AI 请求受限，请稍后再试";
  }
}

function goJump(jump: QaJump) {
  switch (jump.type) {
    case "ITEM":
      router.push({ path: "/items", query: { highlight: jump.itemId ?? "" } });
      break;
    case "LOT":
      router.push({ name: "inventory", query: { lotId: jump.lotId ?? "" } });
      break;
    case "LOCATION":
      router.push({ path: "/locations", query: { highlight: jump.locationId ?? "" } });
      break;
    case "MOVEMENT":
      router.push({ name: "report-movements" });
      break;
    case "REMINDER":
      router.push({ name: "reminders" });
      break;
    case "ATTACHMENT":
      router.push({ path: "/files", query: jump.attachmentId ? { highlight: jump.attachmentId } : {} });
      break;
    default:
      break;
  }
}

function factSources(sources: QaAnswerSource[]): QaAnswerSource[] {
  return sources.filter((source) => source.category !== "KNOWLEDGE_SOURCE");
}

function knowledgeSources(sources: QaAnswerSource[]): QaAnswerSource[] {
  return sources.filter((source) => source.category === "KNOWLEDGE_SOURCE");
}

function answerScopeLabel(scope: Exclude<QaAnswerScope, "AUTO"> | QaAnswerScope): string {
  switch (scope) {
    case "HOUSEHOLD_FACT": return "家庭事实";
    case "KNOWLEDGE_SOURCE": return "知识来源";
    case "BOTH": return "两者";
    default: return "自动";
  }
}

function answerTargetLabel(type: QaQuestionScope["type"]): string {
  switch (type) {
    case "ITEM": return "物品";
    case "LOT": return "批次";
    case "LOCATION": return "位置";
  }
}

function sourceBadgeClass(category: string): string {
  return category === "HOUSEHOLD_FACT" ? "zj-badge-pine" : "zj-badge-plain";
}

function attachmentEntry(jumps: QaJump[]): QaJump | undefined {
  return jumps.find((jump) => jump.type === "ATTACHMENT");
}

function openSource(source: QaAnswerSource) {
  goJump({ type: "ATTACHMENT", label: source.attachmentName ?? source.label,
    attachmentId: source.attachmentId });
}

function evidenceLocation(source: QaAnswerSource): string {
  const parts: string[] = [];
  if (source.pageNumber != null) parts.push(`第 ${source.pageNumber} 页`);
  if (source.sectionPath) parts.push(source.sectionPath);
  return parts.join(" · ");
}

function lotLabel(lot: LotSummary): string {
  return `${lot.itemName} · ${lot.lotNumber || lot.serialNumber || "未编号批次"}`;
}

function scopeLoadError(mode: "ITEM" | "LOT" | "LOCATION"): string {
  switch (mode) {
    case "ITEM": return "物品列表加载失败";
    case "LOT": return "批次列表加载失败";
    case "LOCATION": return "位置列表加载失败";
  }
}

function queryString(value: unknown): string {
  return typeof value === "string" ? value : "";
}

function formatDateTime(iso: string): string {
  try {
    return new Date(iso).toLocaleString("zh-CN", { hour12: false });
  } catch {
    return iso;
  }
}
</script>

<style scoped>
.qa-page {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  width: 100%;
  height: 100%;
  max-width: 1120px;
  overflow: hidden;
}

.qa-page .page-header {
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: var(--zj-space-3);
}

.qa-readiness {
  display: grid;
  justify-items: end;
  gap: var(--zj-space-1);
  min-width: 0;
}

.qa-ai-status {
  display: inline-flex;
  align-items: center;
  gap: var(--zj-space-2);
  margin: 0;
  color: var(--zj-ink-600);
  font-size: var(--zj-text-caption);
}

.qa-knowledge-prep {
  margin: 0;
  color: var(--zj-ink-400);
  font-size: var(--zj-text-caption);
}

.qa-shell {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  width: 100%;
  gap: var(--zj-space-4);
}

/* ---------- 输入区：钉在问答页底部，由对话线程承担滚动 ---------- */
.qa-composer {
  flex-shrink: 0;
  width: 100%;
}

.qa-composer-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-3);
  width: 100%;
  background: var(--zj-surface);
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-md);
  padding: var(--zj-space-4);
  /* shadow-md 比 shadow-sm 重一档，让 composer 像"账册底页"压在 thread 上 */
  box-shadow: var(--zj-shadow-md);
}

/* 顶部 1px 内阴影：纸边立起来感，跟 .card 一致 */
.qa-composer-card::before {
  content: "";
  position: absolute;
  inset: 0 0 auto 0;
  height: 1px;
  border-radius: var(--zj-radius-md) var(--zj-radius-md) 0 0;
  box-shadow: inset 0 1px 0 rgba(28, 58, 47, 0.04);
  pointer-events: none;
}

/* 默认让 children 上的 margin 失效，由父级 .qa-composer-card 的 gap 统筹间距 */
.qa-composer-card > .qa-composer-scope,
.qa-composer-card > .qa-settings,
.qa-composer-card > .qa-composer-footer {
  margin: 0;
}

.qa-composer-scope {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zj-space-2);
}

.qa-scope-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  border: 1px solid var(--zj-line);
  border-radius: 999px;
  background: var(--zj-surface);
  color: var(--zj-ink-600);
  font-family: inherit;
  font-size: var(--zj-text-body-sm);
  font-weight: 500;
  cursor: pointer;
  transition:
    border-color var(--zj-dur-fast) var(--zj-ease-out),
    background-color var(--zj-dur-fast) var(--zj-ease-out),
    color var(--zj-dur-fast) var(--zj-ease-out);
}

.qa-scope-chip:hover {
  border-color: var(--zj-pine-500);
  background: var(--zj-pine-50);
  color: var(--zj-pine-600);
}

.qa-scope-chip[aria-expanded="true"] {
  border-color: var(--zj-pine-600);
  background: var(--zj-pine-50);
  color: var(--zj-pine-600);
}

.qa-scope-chip:active {
  transform: scale(0.98);
}

.qa-scope-chip-icon,
.qa-scope-chip-caret {
  font-size: 14px;
}

.qa-scope-chip-caret {
  transition: transform var(--zj-dur-fast) var(--zj-ease-out);
}

.qa-scope-chip-caret.is-open {
  transform: rotate(180deg);
}

/* settings 面板：折叠时 v-show 隐藏，但仍在 DOM 里供测试与无障碍访问 */
.qa-settings {
  border-bottom: 1px solid var(--zj-line);
  padding-bottom: var(--zj-space-3);
  margin-bottom: var(--zj-space-3);
}

.qa-scope-bar {
  display: grid;
  gap: var(--zj-space-3);
}

.qa-input :deep(.el-textarea__inner) {
  background: var(--zj-surface);
  color: var(--zj-ink-900);
}

.qa-scope-control {
  display: flex;
  align-items: center;
  gap: var(--zj-space-3);
  min-width: 0;
}

.qa-control-label {
  flex: 0 0 var(--zj-control-label-width);
  color: var(--zj-ink-600);
  font-size: var(--zj-text-caption);
  font-weight: var(--zj-font-weight-semibold);
}

.qa-target-control {
  min-height: var(--zj-control-height-sm);
}

.qa-scope-select {
  flex: 1;
  max-width: 360px;
}

.qa-scope-preview,
.qa-used-scope {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--zj-space-2);
}

.qa-scope-preview {
  /* 在 settings 面板内跟 .qa-scope-bar 之间留一拍呼吸 */
  margin-top: var(--zj-space-3);
}

.qa-context-label {
  color: var(--zj-ink-400);
  font-size: var(--zj-text-caption);
}

.qa-composer-scope .qa-context-label {
  margin-left: 2px;
}

.qa-composer-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--zj-space-3);
}

.qa-hint {
  font-size: var(--zj-text-caption);
  color: var(--zj-ink-400);
}

/* ---------- 对话记录 ---------- */
.qa-thread {
  display: flex;
  flex-direction: column;
  flex: 1 1 auto;
  min-width: 0;
  min-height: 0;
  width: 100%;
  gap: var(--zj-space-5);
  overflow-x: hidden;
  overflow-y: auto;
  overscroll-behavior: contain;
}

.qa-turn {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-2);
}

.qa-question {
  display: flex;
  align-items: flex-start;
  gap: var(--zj-space-2);
  align-self: flex-end;
  max-width: 70%;
}

.qa-question-label {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--zj-pine-600);
  color: var(--zj-on-dark-100);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.qa-question-text {
  background: var(--zj-pine-50);
  border: 1px solid var(--zj-pine-100);
  border-radius: var(--zj-radius-md);
  padding: var(--zj-space-2) var(--zj-space-3);
  color: var(--zj-ink-900);
  font-size: var(--zj-text-body-sm);
}

.qa-answer {
  background: var(--zj-surface);
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-md);
  padding: var(--zj-space-4);
  box-shadow: var(--zj-shadow-sm);
  max-width: 90%;
}

.qa-pending {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-3);
}

.qa-pending-skeleton :deep(.el-skeleton__item) {
  background: var(--zj-surface-sunken);
}

.qa-pending-line {
  display: block;
  height: 14px;
  margin-top: 0;
}

.qa-pending-line + .qa-pending-line {
  margin-top: var(--zj-space-2);
}

.qa-pending-line-wide {
  width: 92%;
}

.qa-pending-line-mid {
  width: 72%;
}

.qa-pending-line-narrow {
  width: 48%;
}

.qa-pending-copy {
  margin: 0;
  font-size: var(--zj-text-body-sm);
  color: var(--zj-ink-600);
}

.qa-pending-cancel {
  align-self: flex-start;
}

.qa-pending-elapsed {
  margin: 0;
  font-size: var(--zj-text-caption);
  color: var(--zj-ink-400);
  font-variant-numeric: tabular-nums;
}

.qa-used-scope {
  margin-bottom: var(--zj-space-3);
  padding-bottom: var(--zj-space-2);
  border-bottom: 1px solid var(--zj-line);
}

.qa-answer-parts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--zj-space-3);
  margin-bottom: var(--zj-space-3);
}

.qa-fallback {
  display: grid;
  gap: var(--zj-space-2);
  margin-bottom: var(--zj-space-3);
  padding-bottom: var(--zj-space-3);
  border-bottom: 1px solid var(--zj-line);
}

.qa-fallback .qa-summary {
  margin-bottom: 0;
  color: var(--zj-ink-600);
}

.qa-answer-part {
  min-width: 0;
  padding: var(--zj-space-3);
  background: var(--zj-surface-sunken);
  border-radius: var(--zj-radius-sm);
}

.qa-answer-part-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zj-space-2);
  margin-bottom: var(--zj-space-2);
}

.qa-answer-part .qa-summary {
  margin-bottom: 0;
}

.qa-conflict {
  display: grid;
  gap: var(--zj-space-1);
  margin-bottom: var(--zj-space-3);
  padding: var(--zj-space-3);
  border-left: 3px solid var(--zj-warning);
  background: var(--zj-surface-sunken);
  color: var(--zj-ink-600);
  font-size: var(--zj-text-body-sm);
}

.qa-conflict strong {
  color: var(--zj-warning);
}

.qa-conflict p {
  margin: 0;
}

.qa-clarification {
  display: grid;
  gap: var(--zj-space-2);
}

.qa-candidate {
  display: grid;
  grid-template-columns: minmax(var(--zj-candidate-label-min-width), 0.35fr) minmax(0, 1fr);
  gap: var(--zj-space-3);
  width: 100%;
  padding: var(--zj-space-3);
  border: 1px solid var(--zj-line);
  border-radius: var(--zj-radius-sm);
  background: var(--zj-surface);
  color: var(--zj-ink-900);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--zj-dur-fast) var(--zj-ease-out),
    background var(--zj-dur-fast) var(--zj-ease-out);
}

.qa-candidate:hover {
  border-color: var(--zj-pine-600);
  background: var(--zj-pine-50);
}

.qa-candidate:active {
  transform: scale(0.98);
}

.qa-candidate:disabled {
  cursor: wait;
  opacity: 0.6;
}

.qa-candidate-label {
  font-weight: var(--zj-font-weight-semibold);
}

.qa-candidate-detail {
  color: var(--zj-ink-600);
}

.qa-summary {
  margin: 0 0 var(--zj-space-3);
  color: var(--zj-ink-900);
  font-size: 14px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.qa-sources {
  display: flex;
  align-items: center;
  gap: var(--zj-space-2);
  margin-bottom: var(--zj-space-3);
}

.qa-groundings {
  display: grid;
  gap: var(--zj-space-2);
  margin-bottom: var(--zj-space-3);
}

.qa-grounding {
  border-left: 3px solid var(--zj-pine-500);
  background: var(--zj-pine-50);
  padding: var(--zj-space-2) var(--zj-space-3);
}

.qa-grounding-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--zj-space-2);
}

.qa-grounding-file {
  min-width: 0;
  padding: 0;
  color: var(--zj-pine-600);
}

.qa-grounding-location {
  margin: var(--zj-space-1) 0 0;
  color: var(--zj-ink-400);
  font-size: var(--zj-text-caption);
  font-variant-numeric: tabular-nums;
}

.qa-grounding-excerpt {
  margin: var(--zj-space-2) 0 0;
  color: var(--zj-ink-600);
  font-size: var(--zj-text-body-sm);
  line-height: 1.6;
}

.qa-datetime {
  font-size: var(--zj-text-caption);
  color: var(--zj-ink-400);
  font-variant-numeric: tabular-nums;
}

.qa-jumps {
  display: flex;
  flex-wrap: wrap;
  gap: var(--zj-space-1);
  margin-bottom: var(--zj-space-3);
}

.qa-jump {
  color: var(--zj-pine-600);
}

.qa-jump-icon {
  margin-right: 2px;
}

.qa-result {
  margin-top: var(--zj-space-3);
  border-top: 1px solid var(--zj-line);
  padding-top: var(--zj-space-3);
}

.qa-result-title {
  font-size: var(--zj-text-body-sm);
  font-weight: 600;
  color: var(--zj-ink-600);
  margin-bottom: var(--zj-space-2);
}

.qa-result-table {
  width: 100%;
}

.qa-result-empty {
  color: var(--zj-ink-400);
  font-size: var(--zj-text-body-sm);
}

.qa-unavailable {
  display: flex;
  flex-direction: column;
  gap: var(--zj-space-2);
}

.qa-attachment-entry {
  align-self: flex-start;
  color: var(--zj-pine-600);
}

.zj-badge-warn {
  align-self: flex-start;
  border-color: var(--zj-warning);
  color: var(--zj-warning);
}

/* ---------- 空状态 ---------- */
.qa-empty {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  width: 100%;
  padding: 24px 0;
  text-align: center;
}

.qa-empty-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: var(--zj-radius-md);
  background: var(--zj-surface-sunken);
  color: var(--zj-ink-300);
}

.qa-empty-icon .el-icon {
  font-size: 24px;
}

.qa-empty-title {
  margin: 0;
  font-family: var(--zj-serif);
  font-size: 18px;
  font-weight: 600;
  color: var(--zj-ink-900);
}

.qa-empty-hint {
  margin: 8px auto 0;
  max-width: 440px;
  font-size: 13px;
  color: var(--zj-ink-400);
}

.qa-empty-examples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: var(--zj-space-2);
  max-width: 440px;
  margin: var(--zj-space-4) auto 0;
}

@media (max-width: 720px) {
  .qa-composer-card {
    padding: var(--zj-space-3);
  }

  .qa-composer-scope {
    flex-direction: column;
    align-items: flex-start;
  }

  .qa-readiness {
    justify-items: start;
    width: 100%;
  }

  .qa-scope-bar {
    align-items: stretch;
  }

  .qa-scope-control {
    align-items: stretch;
    flex-direction: column;
  }

  .qa-control-label {
    flex-basis: auto;
  }

  .qa-scope-select {
    max-width: none;
  }

  .qa-composer-footer,
  .qa-grounding-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .qa-question,
  .qa-answer {
    max-width: 100%;
  }

  .qa-answer-parts {
    grid-template-columns: 1fr;
  }
}
</style>
